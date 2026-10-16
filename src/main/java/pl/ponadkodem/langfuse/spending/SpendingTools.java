package pl.ponadkodem.langfuse.spending;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Wydatki w bazie i dzisiejsza data (Clock) - to są "dane pod agentem".
 * Jeśli zmienią się między uruchomieniami evala, wyniki przestają być porównywalne.
 */
@Component
class SpendingTools {

	private final JdbcClient jdbcClient;

	private final Clock clock;

	SpendingTools(JdbcClient jdbcClient, Clock clock) {
		this.jdbcClient = jdbcClient;
		this.clock = clock;
	}

	// rok liczy kod, nie model: przy formacie YYYY-MM model zgadywał rok z danych treningowych (2023)
	@Tool(description = "Zwraca sumę wydatków w złotych w danym miesiącu, opcjonalnie tylko z jednej kategorii")
	BigDecimal sumExpenses(
			@ToolParam(description = "numer miesiąca, 1-12") int month,
			@ToolParam(description = "rok - podaj tylko, jeśli użytkownik napisał go wprost; "
					+ "bez roku liczony jest ostatni taki miesiąc do dzisiaj włącznie", required = false) Integer year,
			@ToolParam(description = "kategoria: FOOD, TRANSPORT, ENTERTAINMENT, SHOPPING, BILLS albo OTHER; "
					+ "pomiń, żeby zsumować wszystkie kategorie", required = false) String category) {
		YearMonth yearMonth = year != null ? YearMonth.of(year, month) : lastOccurrenceOf(month);
		return jdbcClient.sql("""
						select coalesce(sum(amount), 0)
						from expense
						where spent_on >= :from and spent_on < :to
						  and (cast(:category as varchar) is null or category = :category)""")
				.param("from", yearMonth.atDay(1))
				.param("to", yearMonth.plusMonths(1).atDay(1))
				.param("category", category)
				.query(BigDecimal.class)
				.single();
	}

	private YearMonth lastOccurrenceOf(int month) {
		YearMonth current = YearMonth.now(clock);
		YearMonth thisYear = current.withMonth(month);
		return thisYear.isAfter(current) ? thisYear.minusYears(1) : thisYear;
	}

}
