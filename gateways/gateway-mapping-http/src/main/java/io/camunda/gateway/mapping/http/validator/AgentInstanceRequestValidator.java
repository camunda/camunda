/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validate;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateKeyFormat;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validatePositiveKeyFormat;

import io.camunda.gateway.protocol.model.AgentInstanceCreationRequest;
import io.camunda.gateway.protocol.model.AgentInstanceUpdateRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ProblemDetail;

@NullMarked
public class AgentInstanceRequestValidator {

  // Note: even if some properties are marked @NotNull in AgentInstanceCreationRequest,
  // no validation is performed during deserialization, so it is still necessary to validate it here
  @SuppressWarnings("ConstantValue")
  public Optional<ProblemDetail> validateCreateRequest(final AgentInstanceCreationRequest request) {
    return validate(
        () -> {
          final List<String> violations = new ArrayList<>();

          if (request.getElementInstanceKey() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("elementInstanceKey"));
          } else {
            validatePositiveKeyFormat(
                request.getElementInstanceKey(), "elementInstanceKey", violations);
          }

          if (request.getDefinition() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("definition"));
          } else {
            final var def = request.getDefinition();
            if (def.getModel() == null || def.getModel().isBlank()) {
              violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("definition.model"));
            }
            if (def.getProvider() == null || def.getProvider().isBlank()) {
              violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("definition.provider"));
            }
            if (def.getSystemPrompt() == null || def.getSystemPrompt().isBlank()) {
              violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("definition.systemPrompt"));
            }
          }

          if (request.getLimits() != null) {
            final var limits = request.getLimits();
            violations.addAll(validateLimit("limits.maxTokens", limits.getMaxTokens()));
            violations.addAll(validateLimit("limits.maxModelCalls", limits.getMaxModelCalls()));
            violations.addAll(validateLimit("limits.maxToolCalls", limits.getMaxToolCalls()));
          }

          return violations;
        });
  }

  // Note: even if some properties are marked @NotNull in AgentInstanceUpdateRequest,
  // no validation is performed during deserialization, so it is still necessary to validate it here
  @SuppressWarnings("ConstantValue")
  public Optional<ProblemDetail> validateUpdateRequest(
      final String agentInstanceKey, final AgentInstanceUpdateRequest request) {
    return validate(
        () -> {
          final List<String> violations = new ArrayList<>();

          validateKeyFormat(agentInstanceKey, "agentInstanceKey", violations);

          if (request.getElementInstanceKey() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("elementInstanceKey"));
          } else {
            validateKeyFormat(request.getElementInstanceKey(), "elementInstanceKey", violations);
          }

          if (request.getMetrics() != null) {
            final var metrics = request.getMetrics();
            violations.addAll(validateDelta("metrics.inputTokens", metrics.getInputTokens()));
            violations.addAll(validateDelta("metrics.outputTokens", metrics.getOutputTokens()));
            violations.addAll(validateDelta("metrics.modelCalls", metrics.getModelCalls()));
            violations.addAll(validateDelta("metrics.toolCalls", metrics.getToolCalls()));
          }

          if (request.getTools() != null) {
            for (int i = 0; i < request.getTools().size(); i++) {
              final var tool = request.getTools().get(i);
              if (tool.getName() == null || tool.getName().isBlank()) {
                violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("tools[" + i + "].name"));
              }
            }
          }

          return violations;
        });
  }

  private List<String> validateLimit(final String limitName, final Number limit) {
    if (limit != null && limit.longValue() < -1) {
      return List.of(ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(limitName, limit, ">= -1"));
    }
    return Collections.emptyList();
  }

  private List<String> validateDelta(final String fieldName, final @Nullable Number value) {
    if (value != null && value.longValue() < 0) {
      return List.of(ERROR_MESSAGE_INVALID_ATTRIBUTE_VALUE.formatted(fieldName, value, ">= 0"));
    }
    return Collections.emptyList();
  }
}
