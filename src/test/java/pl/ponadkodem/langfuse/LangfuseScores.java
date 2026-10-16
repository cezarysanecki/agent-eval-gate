package pl.ponadkodem.langfuse;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.Map;

import org.springframework.web.client.RestClient;

/**
 * Przypina ocenę do trace'a w Langfuse (POST /api/public/scores).
 * Langfuse nie ma SDK dla Javy, więc to zwykłe wywołanie REST.
 */
class LangfuseScores {

	private final RestClient restClient;

	LangfuseScores(String baseUrl, String basicAuth) {
		this.restClient = RestClient.builder()
				.baseUrl(baseUrl)
				.defaultHeader(AUTHORIZATION, "Basic " + basicAuth)
				.build();
	}

	void send(String traceId, String name, boolean value, String comment) {
		restClient.post()
				.uri("/api/public/scores")
				.contentType(APPLICATION_JSON)
				.body(Map.of("traceId", traceId, "name", name, "value", value ? 1 : 0, "dataType", "BOOLEAN", "comment", comment))
				.retrieve()
				.toBodilessEntity();
	}

}
