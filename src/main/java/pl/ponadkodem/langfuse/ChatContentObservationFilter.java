package pl.ponadkodem.langfuse;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Spring AI 2.x nie zapisuje treści rozmowy w spanach (tylko w logach), więc dokładamy ją
 * jako gen_ai.prompt / gen_ai.completion - Langfuse pokazuje je jako input / output generacji.
 * Format jak w API OpenAI: {"messages": [...], "tools": [...]} na wejściu i wiadomość
 * asystenta (z ewentualnymi tool_calls) na wyjściu. Na samą komunikację z modelem nie wpływa.
 */
@Component
class ChatContentObservationFilter implements ObservationFilter {

	private final JsonMapper jsonMapper;

	ChatContentObservationFilter(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public Observation.Context map(Observation.Context context) {
		if (context instanceof ChatModelObservationContext chat) {
			chat.addHighCardinalityKeyValue(KeyValue.of("gen_ai.prompt", input(chat.getRequest()).toString()));
			if (chat.getResponse() != null) {
				chat.addHighCardinalityKeyValue(KeyValue.of("gen_ai.completion", output(chat.getResponse()).toString()));
			}
		}
		return context;
	}

	private ObjectNode input(Prompt prompt) {
		ObjectNode input = jsonMapper.createObjectNode();
		ArrayNode messages = input.putArray("messages");
		prompt.getInstructions().forEach(message -> addMessage(messages, message));
		if (prompt.getOptions() instanceof ToolCallingChatOptions options && !options.getToolCallbacks().isEmpty()) {
			ArrayNode tools = input.putArray("tools");
			options.getToolCallbacks().forEach(callback -> {
				ToolDefinition tool = callback.getToolDefinition();
				tools.addObject().put("type", "function").putObject("function")
						.put("name", tool.name())
						.put("description", tool.description())
						.set("parameters", jsonMapper.readTree(tool.inputSchema()));
			});
		}
		return input;
	}

	private JsonNode output(ChatResponse response) {
		ArrayNode outputs = jsonMapper.createArrayNode();
		response.getResults().forEach(generation -> outputs.add(assistantMessage(generation.getOutput())));
		return outputs.size() == 1 ? outputs.get(0) : outputs;
	}

	private void addMessage(ArrayNode messages, Message message) {
		switch (message) {
			case AssistantMessage assistant -> messages.add(assistantMessage(assistant));
			// jedna ToolResponseMessage może nieść kilka wyników - w API OpenAI każdy to osobna wiadomość "tool"
			case ToolResponseMessage toolResponse -> toolResponse.getResponses().forEach(response -> messages.addObject()
					.put("role", "tool")
					.put("tool_call_id", response.id())
					.put("name", response.name())
					.put("content", response.responseData()));
			default -> messages.addObject()
					.put("role", message.getMessageType().getValue())
					.put("content", message.getText());
		}
	}

	private ObjectNode assistantMessage(AssistantMessage assistant) {
		ObjectNode message = jsonMapper.createObjectNode()
				.put("role", "assistant")
				.put("content", assistant.getText());
		if (assistant.hasToolCalls()) {
			ArrayNode toolCalls = message.putArray("tool_calls");
			assistant.getToolCalls().forEach(call -> toolCalls.addObject()
					.put("id", call.id())
					.put("type", "function")
					.putObject("function")
					.put("name", call.name())
					.put("arguments", call.arguments()));
		}
		return message;
	}

}
