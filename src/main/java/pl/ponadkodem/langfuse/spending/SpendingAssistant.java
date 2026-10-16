package pl.ponadkodem.langfuse.spending;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
class SpendingAssistant {

	record Answer(BigDecimal amount, String answer) {
	}

	private final ChatClient chatClient;

	private final Clock clock;

	SpendingAssistant(ChatClient.Builder builder, SpendingTools tools, Clock clock) {
		this.chatClient = builder.defaultTools(tools).build();
		this.clock = clock;
	}

	Answer ask(String question, AssistantPrompt prompt) {
		// data w prompcie, żeby model wiedział, który miesiąc jest "ten", a który "zeszły"
		return chatClient.prompt()
				.system(prompt.text() + "\nDzisiaj jest " + LocalDate.now(clock) + ".")
				.user(question)
				.call()
				.entity(Answer.class);
	}

}
