/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.element.instance;

import static io.camunda.gateway.mcp.tool.ToolDescriptions.EVENTUAL_CONSISTENCY_NOTE;

import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.ElementInstanceServices;
import io.camunda.service.ElementInstanceServices.SetVariablesRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class ElementInstanceTools {

  private final ElementInstanceServices elementInstanceServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ElementInstanceTools(
      final ElementInstanceServices elementInstanceServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.elementInstanceServices = elementInstanceServices;
    this.authenticationProvider = authenticationProvider;
  }

  // TODO verify update variables work
  @McpTool(
      description =
          """
          Set variables for one or multiple process instances. Variables are set locally and visible
          to all child elements."""
              + EVENTUAL_CONSISTENCY_NOTE)
  public CallToolResult setProcessInstanceVariables(
      @McpToolParam(
              description =
                  "Process instance key or list of process instance keys to update. Can be a single key (number) or a list of keys.")
          @NotNull(message = "Process instance key(s) must not be null")
          final Object processInstanceKeys,
      @McpToolParam(description = "Variables to set. Values can be any JSON-serializable type.")
          @NotNull(message = "Variables must not be null")
          final Map<@NotBlank String, Object> variables,
      @McpToolParam(description = "Optional operation reference for tracking.", required = false)
          final Long operationReference) {
    try {
      final var keys = normalizeKeys(processInstanceKeys);
      return updateVariables(keys, variables, operationReference, "process instances");
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @McpTool(
      description =
          """
          Set variables for one or multiple element instances. Variables are set locally and only
          visible to the element and its children."""
              + EVENTUAL_CONSISTENCY_NOTE)
  public CallToolResult setElementInstanceVariables(
      @McpToolParam(
              description =
                  "Element instance key or list of element instance keys to update. Can be a single key (number) or a list of keys.")
          @NotNull(message = "Element instance key(s) must not be null")
          final Object elementInstanceKeys,
      @McpToolParam(description = "Variables to set. Values can be any JSON-serializable type.")
          @NotNull(message = "Variables must not be null")
          final Map<@NotBlank String, Object> variables,
      @McpToolParam(description = "Optional operation reference for tracking.", required = false)
          final Long operationReference) {
    try {
      final var keys = normalizeKeys(elementInstanceKeys);
      return updateVariables(keys, variables, operationReference, "element instances");
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  private List<Long> normalizeKeys(final Object input) {
    if (input instanceof Number) {
      // Single key as number
      final var key = ((Number) input).longValue();
      if (key <= 0) {
        throw new IllegalArgumentException("Key must be positive");
      }
      return List.of(key);
    } else if (input instanceof List) {
      // List of keys
      @SuppressWarnings("unchecked")
      final var list = (List<Object>) input;
      if (list.isEmpty()) {
        throw new IllegalArgumentException("Key list must not be empty");
      }
      return list.stream()
          .map(
              k -> {
                if (!(k instanceof Number)) {
                  throw new IllegalArgumentException("All keys must be numbers");
                }
                final var key = ((Number) k).longValue();
                if (key <= 0) {
                  throw new IllegalArgumentException("All keys must be positive");
                }
                return key;
              })
          .toList();
    } else {
      throw new IllegalArgumentException("Key must be a number or list of numbers");
    }
  }

  private CallToolResult updateVariables(
      final List<Long> keys,
      final Map<@NotBlank String, Object> variables,
      final Long operationReference,
      final String instanceType) {
    final var successCount = new java.util.concurrent.atomic.AtomicInteger(0);
    final var failureCount = new java.util.concurrent.atomic.AtomicInteger(0);

    for (final Long key : keys) {
      try {
        final var request = new SetVariablesRequest(key, variables, true, operationReference);

        elementInstanceServices
            .withAuthentication(authenticationProvider.getCamundaAuthentication())
            .setVariables(request)
            .join();

        successCount.incrementAndGet();
      } catch (final Exception e) {
        failureCount.incrementAndGet();
      }
    }

    final var message =
        String.format(
            "Updated variables in %d %s successfully. %d failed.",
            successCount.get(), instanceType, failureCount.get());

    return CallToolResultMapper.fromPrimitive(
        java.util.concurrent.CompletableFuture.completedFuture(null), result -> message);
  }
}
