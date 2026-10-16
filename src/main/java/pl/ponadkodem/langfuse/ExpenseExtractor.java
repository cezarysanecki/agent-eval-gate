package pl.ponadkodem.langfuse;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
class ExpenseExtractor {

	private final ChatClient chatClient;

	ExpenseExtractor(ChatClient.Builder builder) {
		this.chatClient = builder.build();
	}

	Expense extract(String message, PromptVariant variant) {
		return chatClient.prompt()
				.system(variant.systemPrompt())
				.user(message)
				.call()
				.entity(Expense.class);
	}

}
