package pl.ponadkodem.langfuse;

import java.math.BigDecimal;

record Expense(BigDecimal amount, Category category, String description) {

	enum Category {
		FOOD, TRANSPORT, ENTERTAINMENT, SHOPPING, BILLS, OTHER
	}

}
