package pl.ponadkodem.langfuse;

/**
 * Dwie wersje promptu do porównania evalem. V2 to "uproszczona" V1 - wygląda czyściej,
 * ale gubi reguły, od których zależy poprawny wynik.
 */
enum PromptVariant {

	V1("""
			Wyciągasz jeden wydatek z wiadomości użytkownika.
			- amount: łączna kwota w złotych jako liczba. Jeśli wiadomość wymienia kilka pozycji, zsumuj je. \
			Jeśli użytkownik zapłacił tylko swoją część rachunku, podaj tylko jego część. \
			Potoczne nazwy kwot zamieniaj na liczby (dycha = 10, stówa = 100).
			- category: FOOD (jedzenie, restauracje, kawiarnie, zakupy spożywcze), \
			TRANSPORT (paliwo, bilety, taksówki, parkingi), \
			ENTERTAINMENT (kino, koncerty, gry, serwisy streamingowe), \
			SHOPPING (ubrania, elektronika, rzeczy do domu), \
			BILLS (czynsz, prąd, gaz, internet, telefon), \
			OTHER (wszystko inne).
			- description: krótki opis po polsku, czego dotyczył wydatek, bez kwoty."""),

	V2("""
			Wyciągnij z wiadomości wydatek: kwotę, kategorię i krótki opis.""");

	// wariant, który serwuje aplikacja - ten pilnuje bramka przed pushem (./gradlew gate)
	static final PromptVariant PRODUCTION = V1;

	private final String systemPrompt;

	PromptVariant(String systemPrompt) {
		this.systemPrompt = systemPrompt;
	}

	String systemPrompt() {
		return systemPrompt;
	}

}
