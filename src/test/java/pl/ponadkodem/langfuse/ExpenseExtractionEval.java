package pl.ponadkodem.langfuse;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Predicate;

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
 * Nie jest testem z asercjami - mierzy i raportuje. Uruchomienie: ./gradlew eval
 */
@Tag("eval")
@SpringBootTest
class ExpenseExtractionEval {

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
	void compareVariants() throws IOException {
		List<EvalCase> cases = loadCases();
		String runId = "expense-eval " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

		StringBuilder report = new StringBuilder("\n=== " + runId + " (sesja w Langfuse) ===\n");
		for (PromptVariant variant : PromptVariant.values()) {
			List<CaseResult> results = cases.stream()
					.map(evalCase -> evaluate(evalCase, variant, runId))
					.toList();
			report.append(summary(variant, results));
		}
		System.out.println(report);
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

	private static String summary(PromptVariant variant, List<CaseResult> results) {
		StringBuilder summary = new StringBuilder("%s  kwota %s  kategoria %s  opis %s%n".formatted(variant,
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
