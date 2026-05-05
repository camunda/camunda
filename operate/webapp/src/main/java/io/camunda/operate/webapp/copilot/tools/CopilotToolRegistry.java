/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.copilot.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import io.camunda.security.api.model.CamundaAuthentication;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.springframework.stereotype.Component;

/**
 * Server-side tool implementations exposed to the Copilot LLM.
 *
 * <p>The current handlers return placeholder responses. Replace each handler with calls to the
 * existing Operate readers / REST services to ship real data.
 */
@Component
public class CopilotToolRegistry {

  private final ObjectMapper objectMapper;
  private final Map<String, Tool> tools = new LinkedHashMap<>();

  public CopilotToolRegistry(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    register(listProcesses());
    register(countFailedInstances());
    register(getProcessInstance());
    register(searchCamundaDocs());
  }

  public List<ToolSpecification> specifications() {
    return tools.values().stream().map(Tool::spec).toList();
  }

  public String invoke(String name, String argumentsJson, CamundaAuthentication authentication) {
    final Tool tool = tools.get(name);
    if (tool == null) {
      throw new IllegalArgumentException("unknown tool: " + name);
    }
    @SuppressWarnings("unchecked")
    final Map<String, Object> args;
    try {
      args = argumentsJson == null || argumentsJson.isBlank()
          ? Map.of()
          : objectMapper.readValue(argumentsJson, Map.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("invalid arguments JSON: " + e.getMessage(), e);
    }
    final Object result = tool.handler().apply(args, authentication);
    try {
      return objectMapper.writeValueAsString(result);
    } catch (Exception e) {
      throw new RuntimeException("failed to serialise tool result", e);
    }
  }

  private void register(Tool tool) {
    tools.put(tool.spec().name(), tool);
  }

  private Tool listProcesses() {
    return new Tool(
        ToolSpecification.builder()
            .name("list_processes")
            .description(
                "List process definitions deployed in the current Operate cluster. "
                    + "Use this when the user asks what processes exist.")
            .parameters(JsonObjectSchema.builder().build())
            .build(),
        // TODO: replace with ProcessDefinitionReader.search(...)
        (args, auth) ->
            Map.of(
                "processes",
                List.of(
                    Map.of("id", "order-process", "name", "Order Process", "version", 3),
                    Map.of("id", "shipping", "name", "Shipping", "version", 1))));
  }

  private Tool countFailedInstances() {
    return new Tool(
        ToolSpecification.builder()
            .name("count_failed_instances")
            .description("Count process instances with active incidents, optionally filtered by process id.")
            .parameters(
                JsonObjectSchema.builder()
                    .addProperty(
                        "processId",
                        JsonStringSchema.builder()
                            .description("Process definition id; omit for all processes")
                            .build())
                    .build())
            .build(),
        // TODO: replace with IncidentReader.search(...) + group by process
        (args, auth) ->
            Map.of(
                "processId", args.getOrDefault("processId", "*"),
                "count", 12,
                "byErrorType",
                Map.of("JOB_NO_RETRIES", 7, "EXTRACT_VALUE_ERROR", 5)));
  }

  private Tool getProcessInstance() {
    return new Tool(
        ToolSpecification.builder()
            .name("get_process_instance")
            .description("Fetch the current state of a single process instance by key.")
            .parameters(
                JsonObjectSchema.builder()
                    .addProperty(
                        "processInstanceKey",
                        JsonStringSchema.builder().description("Numeric instance key").build())
                    .required("processInstanceKey")
                    .build())
            .build(),
        // TODO: replace with ProcessInstanceReader.byKey(...)
        (args, auth) ->
            Map.of(
                "processInstanceKey", args.get("processInstanceKey"),
                "processId", "order-process",
                "state", "ACTIVE",
                "currentElement", "validate-payment",
                "incidents", 1));
  }

  private Tool searchCamundaDocs() {
    return new Tool(
        ToolSpecification.builder()
            .name("search_camunda_docs")
            .description("Search Camunda's public docs for relevant guides and reference material.")
            .parameters(
                JsonObjectSchema.builder()
                    .addProperty(
                        "query",
                        JsonStringSchema.builder().description("Free-text search query").build())
                    .required("query")
                    .build())
            .build(),
        // TODO: proxy to Kapa AI search endpoint
        (args, auth) ->
            Map.of(
                "query", args.get("query"),
                "results",
                List.of(
                    Map.of(
                        "title", "Camunda 8 docs",
                        "url", "https://docs.camunda.io/",
                        "snippet", "Placeholder result for: " + args.get("query")))));
  }

  private record Tool(
      ToolSpecification spec,
      BiFunction<Map<String, Object>, CamundaAuthentication, Object> handler) {}
}
