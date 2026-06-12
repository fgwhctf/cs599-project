package com.fgwh.service;

import com.fgwh.util.JsonSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class LlmService {

    private final ChatClient chatClient;
    private final YAMLMapper yamlMapper;

    public LlmService(ChatClient.Builder chatClientBuilder, YAMLMapper yamlMapper) {
        this.chatClient = chatClientBuilder.build();
        this.yamlMapper = yamlMapper;
    }

    public String text(String promptName, Map<String, Object> variables) {
        String prompt = PromptTemplates.load(promptName);
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            prompt = prompt.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return chatClient.prompt().user(prompt).call().content();
    }

    public List<String> jsonStringList(String promptName, Map<String, Object> variables) {
        String response = text(promptName, variables);
        return JsonSupport.read(JsonSupport.extractJson(response), new TypeReference<>() {
        });
    }

    public Map<String, List<String>> jsonStringListMap(String promptName, Map<String, Object> variables) {
        String response = text(promptName, variables);
        return JsonSupport.read(JsonSupport.extractJson(response), new TypeReference<>() {
        });
    }

    public String toYaml(Object value) {
        try {
            return yamlMapper.writeValueAsString(value);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static final class PromptTemplates {
        private static String load(String name) {
            try {
                var resource = new ClassPathResource("prompts/" + name + ".prompt");
                return resource.getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }
}
