package pl.ponadkodem.langfuse.spending;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

/**
 * Eval na zamrożonym stanie: każde uruchomienie dostaje własną, świeżą bazę w pamięci, wypełnioną
 * snapshotem z 15.10.2026, i zegar zatrzymany na tym dniu. "Produkcyjnej" bazy nie dotyka.
 * Dwa runy startują z identycznego stanu, więc różnica wyniku może pochodzić tylko ze zmiany promptu.
 */
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:h2:mem:spending-snapshot;DB_CLOSE_DELAY=-1",
		"spring.sql.init.schema-locations=classpath:eval/spending-snapshot-2026-10-15.sql" })
@Import(SpendingEvalOnSnapshot.FrozenClock.class)
class SpendingEvalOnSnapshot extends SpendingEval {

	@TestConfiguration(proxyBeanMethods = false)
	static class FrozenClock {

		@Bean
		@Primary
		Clock frozenClock() {
			return Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneId.of("Europe/Warsaw"));
		}

	}

	@Override
	String dataState() {
		return "snapshot 2026-10-15";
	}

}
