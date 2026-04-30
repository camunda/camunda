/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.processes;

import io.camunda.gateway.mcp.config.server.ToolRepository;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionState;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionType;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.ElementInstanceServices;
import io.camunda.service.IncidentServices;
import io.camunda.service.MessageServices;
import io.camunda.service.MessageServices.CorrelateMessageRequest;
import io.camunda.service.MessageSubscriptionServices;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.VariableServices;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.collection.Tuple;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

public class ProcessesToolRepository implements ToolRepository {

  static final String TOOL_GET_PROCESS_STATE = "getProcessState";

  protected static final String PROPERTY_INPUTS = "io.camunda.tool:inputs";
  protected static final String PROPERTY_PURPOSE = "io.camunda.tool:purpose";
  protected static final String PROPERTY_RESULTS = "io.camunda.tool:results";
  protected static final String PROPERTY_WHEN_NOT_TO_USE = "io.camunda.tool:when_not_to_use";
  protected static final String PROPERTY_WHEN_TO_USE = "io.camunda.tool:when_to_use";

  protected static final String LABEL_INPUTS = "## Inputs";
  protected static final String LABEL_RESULTS = "## Results";
  protected static final String LABEL_WHEN_NOT_TO_USE = "## When not to use";
  protected static final String LABEL_WHEN_TO_USE = "## When to use";

  private static final List<Tuple<String, String>> DESCRIPTION_PARTS =
      List.of(
          Tuple.of(PROPERTY_PURPOSE, null),
          Tuple.of(PROPERTY_INPUTS, LABEL_INPUTS),
          Tuple.of(PROPERTY_WHEN_TO_USE, LABEL_WHEN_TO_USE),
          Tuple.of(PROPERTY_WHEN_NOT_TO_USE, LABEL_WHEN_NOT_TO_USE),
          Tuple.of(PROPERTY_RESULTS, LABEL_RESULTS));

  private static final Tool GET_PROCESS_STATE_TOOL =
      Tool.builder()
          .name(TOOL_GET_PROCESS_STATE)
          .title("Get process state")
          .description(
              """
              Get the current state of a process instance by its key.

              Returns the instance's state (ACTIVE, COMPLETED, TERMINATED), all variables, \
              currently active element instances, and any open incidents. \
              Use this after starting a process instance to monitor its progress \
              or to investigate failures.""")
          .inputSchema(
              new JsonSchema(
                  "object",
                  Map.of(
                      "processInstanceKey",
                      Map.of(
                          "type",
                          "integer",
                          "format",
                          "int64",
                          "description",
                          "The key of the process instance to inspect.")),
                  List.of("processInstanceKey"),
                  false,
                  Map.of(),
                  Map.of()))
          .build();

  private final MessageSubscriptionServices messageSubscriptionServices;
  private final MessageServices messageServices;
  private final ProcessInstanceServices processInstanceServices;
  private final VariableServices variableServices;
  private final ElementInstanceServices elementInstanceServices;
  private final IncidentServices incidentServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ProcessesToolRepository(
      final MessageSubscriptionServices messageSubscriptionServices,
      final MessageServices messageServices,
      final ProcessInstanceServices processInstanceServices,
      final VariableServices variableServices,
      final ElementInstanceServices elementInstanceServices,
      final IncidentServices incidentServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.messageSubscriptionServices = messageSubscriptionServices;
    this.messageServices = messageServices;
    this.processInstanceServices = processInstanceServices;
    this.variableServices = variableServices;
    this.elementInstanceServices = elementInstanceServices;
    this.incidentServices = incidentServices;
    this.authenticationProvider = authenticationProvider;
  }

  @Override
  public @NonNull List<Tool> getTools(@NonNull final McpTransportContext transportContext) {
    final var auth = authenticationProvider.getCamundaAuthentication();
    final var query =
        MessageSubscriptionQuery.of(
            b ->
                b.filter(
                        f ->
                            f.messageSubscriptionTypes(MessageSubscriptionType.START_EVENT.name())
                                .messageSubscriptionStateOperations(
                                    Operation.neq(MessageSubscriptionState.DELETED.name()))
                                .toolNameOperations(Operation.exists(true)))
                    .unlimited());
    final List<Tool> tools = new ArrayList<>();
    tools.add(GET_PROCESS_STATE_TOOL);
    messageSubscriptionServices.search(query, auth).items().stream()
        .map(this::buildTool)
        .forEach(tools::add);
    return tools;
  }

  @Override
  public @NonNull Either<String, SyncToolSpecification> findTool(
      @NonNull final McpTransportContext transportContext, @NonNull final String toolName) {
    if (TOOL_GET_PROCESS_STATE.equals(toolName)) {
      return Either.right(buildGetProcessStateSpec());
    }

    final int lastUnderscore = toolName.lastIndexOf('_');
    if (lastUnderscore < 0) {
      return Either.left("Tool not found: " + toolName);
    }
    final long subscriptionKey;
    try {
      subscriptionKey = Long.parseLong(toolName.substring(lastUnderscore + 1));
    } catch (final NumberFormatException e) {
      return Either.left("Tool not found: " + toolName);
    }

    final var auth = authenticationProvider.getCamundaAuthentication();
    final var entity = messageSubscriptionServices.getByKey(subscriptionKey, auth);

    if (entity == null) {
      return Either.left("Tool not found: " + toolName);
    }
    if (entity.messageSubscriptionState() == MessageSubscriptionState.DELETED) {
      return Either.left("Tool " + toolName + " has been removed. Please refresh the tool list.");
    }

    return Either.right(
        SyncToolSpecification.builder()
            .tool(buildTool(entity))
            .callHandler(
                (ctx, req) -> {
                  final Map<String, Object> arguments =
                      req.arguments() != null ? req.arguments() : Map.of();
                  return CallToolResultMapper.from(
                      messageServices.correlateMessage(
                          new CorrelateMessageRequest(
                              entity.messageName(), "", arguments, entity.tenantId()),
                          authenticationProvider.getCamundaAuthentication()),
                      record -> Map.of("processInstanceKey", record.getProcessInstanceKey()));
                })
            .build());
  }

  private SyncToolSpecification buildGetProcessStateSpec() {
    return SyncToolSpecification.builder()
        .tool(GET_PROCESS_STATE_TOOL)
        .callHandler(
            (ctx, req) -> {
              try {
                final Map<String, Object> args =
                    req.arguments() != null ? req.arguments() : Map.of();
                final Object keyArg = args.get("processInstanceKey");
                if (keyArg == null) {
                  return CallToolResultMapper.mapErrorToResult(
                      new IllegalArgumentException("processInstanceKey is required"));
                }
                final long processInstanceKey = ((Number) keyArg).longValue();
                return CallToolResultMapper.from(assembleProcessState(processInstanceKey));
              } catch (final Exception e) {
                return CallToolResultMapper.mapErrorToResult(e);
              }
            })
        .build();
  }

  private Map<String, Object> assembleProcessState(final long processInstanceKey) {
    final CamundaAuthentication auth = authenticationProvider.getCamundaAuthentication();

    final var instance = processInstanceServices.getByKey(processInstanceKey, auth);

    final var variables =
        variableServices
            .search(
                VariableQuery.of(
                    b -> b.filter(f -> f.processInstanceKeys(processInstanceKey)).unlimited()),
                auth)
            .items()
            .stream()
            .map(
                v -> {
                  final Map<String, Object> varMap = new LinkedHashMap<>();
                  varMap.put("name", v.name());
                  varMap.put("value", v.value());
                  return varMap;
                })
            .toList();

    final var activeElements =
        elementInstanceServices
            .search(
                FlowNodeInstanceQuery.of(
                    b ->
                        b.filter(
                                f ->
                                    f.processInstanceKeys(processInstanceKey)
                                        .states(FlowNodeState.ACTIVE.name()))
                            .unlimited()),
                auth)
            .items()
            .stream()
            .map(
                fni -> {
                  final Map<String, Object> fniMap = new LinkedHashMap<>();
                  fniMap.put("flowNodeId", fni.flowNodeId());
                  fniMap.put("flowNodeName", fni.flowNodeName());
                  fniMap.put("type", fni.type() != null ? fni.type().name() : null);
                  return fniMap;
                })
            .toList();

    final var incidents =
        incidentServices
            .search(
                IncidentQuery.of(
                    b -> b.filter(f -> f.processInstanceKeys(processInstanceKey)).unlimited()),
                auth)
            .items()
            .stream()
            .map(
                inc -> {
                  final Map<String, Object> incMap = new LinkedHashMap<>();
                  incMap.put("incidentKey", inc.incidentKey());
                  incMap.put("errorType", inc.errorType() != null ? inc.errorType().name() : null);
                  incMap.put("errorMessage", inc.errorMessage());
                  incMap.put("flowNodeId", inc.flowNodeId());
                  return incMap;
                })
            .toList();

    final Map<String, Object> result = new LinkedHashMap<>();
    result.put("processInstanceKey", instance.processInstanceKey());
    result.put("state", instance.state() != null ? instance.state().name() : null);
    result.put("hasIncident", instance.hasIncident());
    result.put("variables", variables);
    result.put("activeElementInstances", activeElements);
    result.put("incidents", incidents);
    return result;
  }

  private Tool buildTool(final MessageSubscriptionEntity entity) {
    final String name = entity.toolName() + "_" + entity.messageSubscriptionKey();
    final String description = buildDescription(entity.extensionProperties());
    return Tool.builder()
        .name(name)
        .title(entity.toolName())
        .description(description)
        .inputSchema(new JsonSchema("object", Map.of(), List.of(), false, Map.of(), Map.of()))
        .outputSchema(
            Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                    "processInstanceKey",
                    Map.of(
                        "type",
                        "integer",
                        "format",
                        "int64",
                        "description",
                        "The key of the started process instance. Use this to investigate the state of the started instance.")),
                "required",
                List.of("processInstanceKey")))
        .build();
  }

  private String buildDescription(final Map<String, String> props) {
    return String.join(
        "\n\n",
        DESCRIPTION_PARTS.stream()
            .map(
                part -> {
                  final String value = props.get(part.getLeft());
                  if (value == null || value.isBlank()) {
                    return null;
                  }
                  if (part.getRight() == null) {
                    return value;
                  }
                  return part.getRight() + "\n" + value;
                })
            .filter(Objects::nonNull)
            .toList());
  }
}
