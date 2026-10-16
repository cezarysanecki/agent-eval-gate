package pl.ponadkodem.langfuse;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ChatController {

	private final ChatClient chatClient;

	ChatController(ChatClient.Builder builder) {
		this.chatClient = builder
				.defaultSystem("Jesteś zwięzłym asystentem. Odpowiadaj po polsku jednym zdaniem, które jest najlepsze do odpowiedzi.")
				.defaultTools(new DateTimeTools())
				.build();
	}

	@PostMapping("/chat")
	ChatResponse chat(@RequestBody ChatRequest request) {
		String answer = chatClient.prompt()
				.user(request.message())
				.call()
				.content();
		return new ChatResponse(answer);
	}

	record ChatRequest(String message) {
	}

	record ChatResponse(String answer) {
	}

}
