package pl.ponadkodem.langfuse;

import org.springframework.ai.chat.client.ChatClient;

/**
 * LLM-as-judge: drugi model ocenia, czy opis wydatku znaczy to samo co opis wzorcowy.
 * Kod tu nie wystarczy, bo ten sam wydatek można opisać na wiele sposobów.
 */
class DescriptionJudge {

	record Verdict(boolean correct, String reason) {
	}

	private static final String PROMPT = """
			Oceniasz opis wydatku wyciągnięty przez innego asystenta z wiadomości użytkownika.
			Opis jest poprawny, jeśli mówi o tym samym wydatku co opis wzorcowy. Inne sformułowanie jest w porządku, \
			brakujący kluczowy szczegół albo zmyślony szczegół nie jest. \
			Szczegół, który wynika z innego słowa w opisie, nie jest brakujący. Kwota w opisie nie ma znaczenia.
			W reason podaj jednym zdaniem po polsku, dlaczego.""";

	private final ChatClient chatClient;

	DescriptionJudge(ChatClient.Builder builder) {
		this.chatClient = builder.defaultSystem(PROMPT).build();
	}

	Verdict judge(String message, String expectedDescription, String actualDescription) {
		return chatClient.prompt()
				.user("""
						Wiadomość: %s
						Opis wzorcowy: %s
						Opis do oceny: %s""".formatted(message, expectedDescription, actualDescription))
				.call()
				.entity(Verdict.class);
	}

}
