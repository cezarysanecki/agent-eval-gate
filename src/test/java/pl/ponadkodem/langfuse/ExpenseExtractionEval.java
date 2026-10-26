package pl.ponadkodem.langfuse;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import pl.ponadkodem.langfuse.DescriptionJudge.Verdict;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Eval wyciągania wydatków: te same wejścia (eval/expense-cases.json) dla obu wariantów promptu.
 * Kwotę i kategorię ocenia kod, opis - drugi model. Każdy przypadek to osobny trace w Langfuse
 * z przypiętymi ocenami, a całe uruchomienie to jedna sesja.
 * Porównanie wariantów mierzy i raportuje (./gradlew eval). Bramka sprawdza tylko wariant produkcyjny
 * i oblewa build, gdy kod znajdzie choć jeden błąd kwoty lub kategorii albo sędzia odrzuci zbyt wiele
 * opisów (./gradlew gate, odpalana przez hook pre-push z .githooks).
 */
@SpringBootTest
class ExpenseExtractionEval {

	// sprawdzenia w kodzie są deterministyczne, więc każdy błąd to regresja
	static final double MIN_CODE_PASS_RATE = 1.0;

	// sędzia LLM bywa niestały przy tym samym opisie, więc dostaje margines
	static final double MIN_JUDGE_PASS_RATE = 0.75;

	record EvalCase(String message, Expense expected) {
	}

	record CaseResult(EvalCase evalCase, Expense actual, String error, Verdict description) {

		// code-as-judge: deterministyczne sprawdzenia, tak albo nie
		boolean amountCorrect() {
			return actual != null && actual.amount() != null && actual.amount().compareTo(evalCase.expected().amount()) == 0;
		}

		boolean categoryCorrect() {
			return actual != null && actual.category() == evalCase.expected().category();
		}

		boolean descriptionCorrect() {
			return description.correct();
		}

		boolean allCorrect() {
			return amountCorrect() && categoryCorrect() && descriptionCorrect();
		}

		String actualOrError() {
			return actual != null ? actual.toString() : error;
		}

	}

	@Autowired
	ExpenseExtractor extractor;

	@Autowired
	Tracer tracer;

	@Autowired
	JsonMapper jsonMapper;

	@Autowired
	ChatClient.Builder chatClientBuilder;

	@Value("${langfuse.base-url}")
	String langfuseUrl;

	@Value("${LANGFUSE_AUTH}")
	String langfuseAuth;

	DescriptionJudge descriptionJudge;

	LangfuseScores scores;

	@BeforeEach
	void setUp() {
		descriptionJudge = new DescriptionJudge(chatClientBuilder);
		scores = new LangfuseScores(langfuseUrl, langfuseAuth);
	}

	@Test
	@Tag("eval")
	void compareVariants() throws IOException {
		List<EvalCase> cases = loadCases();
		String runId = runId("expense-eval");

		StringBuilder report = new StringBuilder("\n=== " + runId + " (sesja w Langfuse) ===\n");
		for (PromptVariant variant : PromptVariant.values()) {
			List<CaseResult> results = cases.stream()
					.map(evalCase -> evaluate(evalCase, variant, runId))
					.toList();
			report.append(summary(variant.name(), results));
		}
		System.out.println(report);
	}

	@Test
	@Tag("gate")
	void productionPromptPassesGate() throws IOException {
		String runId = runId("expense-gate");
		List<CaseResult> results = loadCases().stream()
				.map(evalCase -> evaluate(evalCase, PromptVariant.PRODUCTION, runId))
				.toList();
		System.out.println("\n=== " + runId + " ===\n" + summary("PRODUKCJA", results));
		List<String> failures = Stream.of(
				gateCheck("kwota", results, CaseResult::amountCorrect, MIN_CODE_PASS_RATE),
				gateCheck("kategoria", results, CaseResult::categoryCorrect, MIN_CODE_PASS_RATE),
				gateCheck("opis", results, CaseResult::descriptionCorrect, MIN_JUDGE_PASS_RATE))
				.flatMap(Optional::stream)
				.toList();
		assertTrue(failures.isEmpty(), "Bramka evala: " + String.join("; ", failures));
	}

	private static Optional<String> gateCheck(String name, List<CaseResult> results, Predicate<CaseResult> check,
			double minPassRate) {
		long passed = results.stream().filter(check).count();
		if (passed >= Math.ceil(minPassRate * results.size())) {
			return Optional.empty();
		}
		return Optional.of("%s %s, wymagane %.0f%%".formatted(name, hits(results, check), minPassRate * 100));
	}

	private static String runId(String name) {
		return name + " " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
	}

	private List<EvalCase> loadCases() throws IOException {
		try (InputStream json = new ClassPathResource("eval/expense-cases.json").getInputStream()) {
			return jsonMapper.readValue(json, new TypeReference<>() {
			});
		}
	}

	private CaseResult evaluate(EvalCase evalCase, PromptVariant variant, String runId) {
		Span span = tracer.nextSpan().name("eval expense " + variant).start();
		span.tag("langfuse.session.id", runId);
		span.tag("langfuse.trace.metadata.variant", variant.name());
		span.tag("langfuse.trace.metadata.expected", evalCase.expected().toString());
		Expense actual = null;
		String error = null;
		try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
			actual = extractor.extract(evalCase.message(), variant);
		}
		catch (RuntimeException e) {
			error = e.getClass().getSimpleName() + ": " + e.getMessage();
		}
		finally {
			span.end();
		}

		// LLM-as-judge poza trace'em agenta, żeby nie wliczać sędziego w jego koszt
		Verdict description = actual == null
				? new Verdict(false, "brak wyniku: " + error)
				: descriptionJudge.judge(evalCase.message(), evalCase.expected().description(), actual.description());
		CaseResult result = new CaseResult(evalCase, actual, error, description);

		String traceId = span.context().traceId();
		String comparison = "oczekiwano " + evalCase.expected() + ", jest " + result.actualOrError();
		scores.send(traceId, "amount_correct", result.amountCorrect(), comparison);
		scores.send(traceId, "category_correct", result.categoryCorrect(), comparison);
		scores.send(traceId, "description_correct", result.descriptionCorrect(), description.reason());
		return result;
	}

	private static String summary(String label, List<CaseResult> results) {
		StringBuilder summary = new StringBuilder("%s  kwota %s  kategoria %s  opis %s%n".formatted(label,
				hits(results, CaseResult::amountCorrect),
				hits(results, CaseResult::categoryCorrect),
				hits(results, CaseResult::descriptionCorrect)));
		results.stream()
				.filter(result -> !result.allCorrect())
				.forEach(result -> summary.append("""
						    "%s"
						      oczekiwano: %s
						      jest:       %s
						      sędzia:     %s
						""".formatted(result.evalCase().message(), result.evalCase().expected(),
						result.actualOrError(), result.description().reason())));
		return summary.toString();
	}

	private static String hits(List<CaseResult> results, Predicate<CaseResult> check) {
		return results.stream().filter(check).count() + "/" + results.size();
	}

}
