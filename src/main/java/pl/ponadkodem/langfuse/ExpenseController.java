package pl.ponadkodem.langfuse;

import java.util.Objects;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ExpenseController {

	private final ExpenseExtractor extractor;

	ExpenseController(ExpenseExtractor extractor) {
		this.extractor = extractor;
	}

	@PostMapping("/expenses")
	Expense extract(@RequestBody ExtractRequest request) {
		return extractor.extract(request.message(), Objects.requireNonNullElse(request.variant(), PromptVariant.V1));
	}

	record ExtractRequest(String message, PromptVariant variant) {
	}

}
