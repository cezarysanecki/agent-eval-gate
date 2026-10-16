package pl.ponadkodem.langfuse.spending;

/**
 * Eval na tym, co akurat jest: "produkcyjna" baza (H2 w pliku budget-db/data) i prawdziwy zegar.
 * Wynik zależy od dnia uruchomienia i od tego, co ktoś dopisał do bazy - porównanie dwóch takich runów
 * mierzy naraz zmianę promptu i zmianę danych.
 */
class SpendingEvalOnLiveData extends SpendingEval {

	@Override
	String dataState() {
		return "live";
	}

}
