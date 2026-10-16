package pl.ponadkodem.langfuse.spending;

import java.util.Objects;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SpendingController {

	private final SpendingAssistant assistant;

	SpendingController(SpendingAssistant assistant) {
		this.assistant = assistant;
	}

	@PostMapping("/spending/ask")
	SpendingAssistant.Answer ask(@RequestBody AskRequest request) {
		return assistant.ask(request.question(), Objects.requireNonNullElse(request.prompt(), AssistantPrompt.V1));
	}

	record AskRequest(String question, AssistantPrompt prompt) {
	}

}
