package pl.ponadkodem.langfuse.spending;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import pl.ponadkodem.langfuse.LangfuseScores;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Eval asystenta wydatków: te same pytania (eval/spending-cases.json) dla obu wersji promptu,
 * kwotę ocenia kod. Klucz odpowiedzi jest policzony z danych z 15.10.2026 - jest prawdziwy tylko
 * dla tego stanu. Podklasy różnią się wyłącznie tym, na jakich danych pracuje agent:
 * żywa baza i prawdziwy zegar albo zamrożony snapshot i zamrożony zegar.
 */
@Tag("eval")
@SpringBootTest
abstract class SpendingEval {

	record EvalCase(String question, BigDecimal expectedAmount) {
	}

	record CaseResult(EvalCase evalCase, SpendingAssistant.Answer actual, String error) {

		// code-as-judge
		boolean amountCorrect() {
			return actual != null && actual.amount() != null && actual.amount().compareTo(evalCase.expectedAmount()) == 0;
		}

		String actualOrError() {
			return actual != null ? actual.amount() + " zł (\"" + actual.answer() + "\")" : error;
		}

	}

	@Autowired
	SpendingAssistant assistant;

	@Autowired
	Tracer tracer;

	@Autowired
	JsonMapper jsonMapper;

	@Value("${langfuse.base-url}")
	String langfuseUrl;

	@Value("${LANGFUSE_AUTH}")
	String langfuseAuth;

	// które warianty promptu puścić: domyślnie oba, np. EVAL_PROMPTS=V1 ./gradlew eval --tests '*OnLiveData'
	@Value("${eval.prompts:V1,V2}")
	List<AssistantPrompt> prompts;

	/** Na jakich danych działa agent - trafia do metadanych trace'ów: porównywać wolno tylko runy z tym samym stanem. */
	abstract String dataState();

	@Test
	void compareVariants() throws IOException {
		List<EvalCase> cases = loadCases();
		LangfuseScores scores = new LangfuseScores(langfuseUrl, langfuseAuth);
		String runId = "spending-eval " + dataState() + " "
				+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

		StringBuilder report = new StringBuilder("\n=== " + runId + " (sesja w Langfuse) ===\n");
		for (AssistantPrompt prompt : prompts) {
			List<CaseResult> results = cases.stream()
					.map(evalCase -> evaluate(evalCase, prompt, runId, scores))
					.toList();
			report.append(summary(prompt, results));
		}
		System.out.println(report);
	}

	private List<EvalCase> loadCases() throws IOException {
		try (InputStream json = new ClassPathResource("eval/spending-cases.json").getInputStream()) {
			return jsonMapper.readValue(json, new TypeReference<>() {
			});
		}
	}

	private CaseResult evaluate(EvalCase evalCase, AssistantPrompt prompt, String runId, LangfuseScores scores) {
		Span span = tracer.nextSpan().name("eval spending " + prompt).start();
		span.tag("langfuse.session.id", runId);
		span.tag("langfuse.trace.metadata.prompt", prompt.name());
		span.tag("langfuse.trace.metadata.data_state", dataState());
		span.tag("langfuse.trace.metadata.expected_amount", evalCase.expectedAmount().toPlainString());
		SpendingAssistant.Answer actual = null;
		String error = null;
		try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
			actual = assistant.ask(evalCase.question(), prompt);
		}
		catch (RuntimeException e) {
			error = e.getClass().getSimpleName() + ": " + e.getMessage();
		}
		finally {
			span.end();
		}

		CaseResult result = new CaseResult(evalCase, actual, error);
		scores.send(span.context().traceId(), "amount_correct", result.amountCorrect(),
				"oczekiwano " + evalCase.expectedAmount() + " zł, jest " + result.actualOrError());
		return result;
	}

	private static String summary(AssistantPrompt prompt, List<CaseResult> results) {
		long hits = results.stream().filter(CaseResult::amountCorrect).count();
		StringBuilder summary = new StringBuilder("%s  kwota %d/%d%n".formatted(prompt, hits, results.size()));
		results.stream()
				.filter(result -> !result.amountCorrect())
				.forEach(result -> summary.append("""
						    "%s"
						      oczekiwano: %s zł
						      jest:       %s
						""".formatted(result.evalCase().question(), result.evalCase().expectedAmount(),
						result.actualOrError())));
		return summary.toString();
	}

}
