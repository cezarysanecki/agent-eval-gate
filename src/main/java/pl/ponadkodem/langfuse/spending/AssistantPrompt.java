package pl.ponadkodem.langfuse.spending;

/**
 * Dwie wersje promptu asystenta. Są równie dobre - inaczej sformułowane, te same zasady.
 * Jeśli eval pokaże między nimi różnicę, warto zapytać, czy nie zmieniło się coś innego niż prompt.
 */
enum AssistantPrompt {

	V1("""
			Odpowiadasz na pytania użytkownika o jego wydatki.
			Kwoty bierz wyłącznie z narzędzia sumExpenses.
			W amount podaj kwotę w złotych, w answer jedno zdanie po polsku."""),

	V2("""
			Jesteś asystentem budżetu domowego. Zanim odpowiesz, policz kwotę narzędziem sumExpenses. \
			Nie zgaduj kwot.
			W amount podaj kwotę w złotych, w answer krótką odpowiedź po polsku.""");

	private final String text;

	AssistantPrompt(String text) {
		this.text = text;
	}

	String text() {
		return text;
	}

}
