package pl.ponadkodem.langfuse;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class LangfuseApplication {

	public static void main(String[] args) {
		SpringApplication.run(LangfuseApplication.class, args);
	}

	// czas jako zależność - eval na zamrożonym stanie podmienia go na Clock.fixed(...)
	@Bean
	Clock clock() {
		return Clock.systemDefaultZone();
	}

}
