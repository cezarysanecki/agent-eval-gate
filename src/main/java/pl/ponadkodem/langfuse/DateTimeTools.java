package pl.ponadkodem.langfuse;

import java.time.ZonedDateTime;

import org.springframework.ai.tool.annotation.Tool;

class DateTimeTools {

	@Tool(description = "Zwraca bieżącą datę i godzinę w strefie czasowej serwera")
	String currentDateTime() {
		return ZonedDateTime.now().toString();
	}

}
