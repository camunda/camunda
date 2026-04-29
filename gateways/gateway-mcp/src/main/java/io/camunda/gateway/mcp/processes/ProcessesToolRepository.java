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
import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionState;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionType;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.MessageServices;
import io.camunda.service.MessageServices.CorrelateMessageRequest;
import io.camunda.service.MessageSubscriptionServices;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.collection.Tuple;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;

public class ProcessesToolRepository implements ToolRepository {

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

  private final MessageSubscriptionServices messageSubscriptionServices;
  private final MessageServices messageServices;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final List<SyncToolSpecification> staticTools;

  public ProcessesToolRepository(
      final MessageSubscriptionServices messageSubscriptionServices,
      final MessageServices messageServices,
      final CamundaAuthenticationProvider authenticationProvider,
      final List<SyncToolSpecification> staticTools) {
    this.messageSubscriptionServices = messageSubscriptionServices;
    this.messageServices = messageServices;
    this.authenticationProvider = authenticationProvider;
    this.staticTools = List.copyOf(staticTools);
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
    return Stream.concat(
            messageSubscriptionServices.search(query, auth).items().stream().map(this::buildTool),
            staticTools.stream().map(SyncToolSpecification::tool))
        .toList();
  }

  @Override
  public @NonNull Either<String, SyncToolSpecification> findTool(
      @NonNull final McpTransportContext transportContext, @NonNull final String toolName) {
    final Optional<SyncToolSpecification> staticTool =
        staticTools.stream().filter(spec -> spec.tool().name().equals(toolName)).findFirst();
    if (staticTool.isPresent()) {
      return Either.right(staticTool.get());
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
