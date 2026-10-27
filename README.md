# Agent eval gate

A quality gate for an AI agent: an eval traced in [Langfuse](https://langfuse.com) blocks a prompt regression before `git push` and again in CI.

The agent is a small Spring AI service that reads a personal expense from a chat message ("coffee and a cookie, 27.50 zł in total") and returns its amount, category and description. A cleaner-looking prompt can quietly lose the rules the result depends on. The gate catches that before the change leaves the machine.

Prompts, test cases and code comments are in Polish 🇵🇱 PL.

## How the gate works

[`PromptVariant`](src/main/java/pl/ponadkodem/langfuse/PromptVariant.java) holds two prompts. `V1` spells out the rules: sum several items, count only the user's share of a bill, turn slang amounts into numbers, map spending to categories. `V2` is a "simplified" one-liner. `PRODUCTION` points to the variant the app serves, and that is the one the gate checks.

[`ExpenseExtractionEval`](src/test/java/pl/ponadkodem/langfuse/ExpenseExtractionEval.java) runs 8 cases from [`expense-cases.json`](src/test/resources/eval/expense-cases.json) against the production prompt and checks each result on two levels:

| Check | Judge | Threshold |
| --- | --- | --- |
| amount | code, deterministic | 100% |
| category | code, deterministic | 100% |
| description | LLM judge ([`DescriptionJudge`](src/test/java/pl/ponadkodem/langfuse/DescriptionJudge.java)), compares meaning, not exact words | 75%, because the judge is not fully stable |

Every run is a session in Langfuse, with scores attached to the traces ([`LangfuseScores`](src/test/java/pl/ponadkodem/langfuse/LangfuseScores.java)).

The gate runs in two places:

- [`.githooks/pre-push`](.githooks/pre-push) runs `./gradlew gate`, prints the result and blocks the push if a threshold is missed,
- [`.github/workflows/eval-gate.yml`](.github/workflows/eval-gate.yml) runs the same task on every push and pull request, reporting to Langfuse Cloud.

## Spending assistant: why eval data must be frozen

The `spending` package is a second agent that answers questions about spending ("how much did I spend on food last month?") with tools over an H2 database. [`SpendingEval`](src/test/java/pl/ponadkodem/langfuse/spending/SpendingEval.java) asks 5 questions from [`spending-cases.json`](src/test/resources/eval/spending-cases.json) in two setups:

- [`SpendingEvalOnLiveData`](src/test/java/pl/ponadkodem/langfuse/spending/SpendingEvalOnLiveData.java): the "production" database and the real clock. The result depends on the day and on what was added to the data, so two runs measure a prompt change and a data change at once.
- [`SpendingEvalOnSnapshot`](src/test/java/pl/ponadkodem/langfuse/spending/SpendingEvalOnSnapshot.java): a fresh in-memory database from a snapshot of 15 October 2026 and a clock frozen on that day. Any difference between runs comes from the prompt alone.

`./gradlew simulateDrift` changes the live data to show the difference, and `./gradlew resetBudgetDb` deletes the database, so it is created again from [`budget-db/init.sql`](budget-db/init.sql) on the next start.

## Stack

Java 27 (Gradle runs on Java 26), Spring Boot 4.1, Spring AI 2.0, OpenAI, Langfuse v3 over OpenTelemetry, H2, JUnit 5.

## Running

```bash
cp .env.example .env          # OPENAI_API_KEY and LANGFUSE_AUTH
podman-compose up -d          # local Langfuse on http://localhost:3000
git config core.hooksPath .githooks

./gradlew gate                # the gate: production prompt against thresholds
./gradlew eval                # full evals: both prompt variants, spending assistant
```

`LANGFUSE_AUTH` is `base64("<public-key>:<secret-key>")` from Langfuse, Settings, API Keys. The local Langfuse in `docker-compose.yml` uses fixed, public passwords and is meant for learning only.

For CI, set the repository secrets `OPENAI_API_KEY` and `LANGFUSE_AUTH` and the variable `LANGFUSE_BASE_URL` (for example `https://cloud.langfuse.com`).

Evals call a real model, so they are slow, cost money and give slightly different results each run. They are outside the regular `build`.
