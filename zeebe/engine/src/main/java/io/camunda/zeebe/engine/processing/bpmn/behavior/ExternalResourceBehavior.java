/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior.LinkedResourceProps;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.state.immutable.ResourceState;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;

public class ExternalResourceBehavior {
  // TODO We can do this object mapping on deploy time. Then we don't have to parse the resource for
  //  every service task. We can store the variable names and values (expressions) in state and
  // fetch them here.
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private final BpmnStateBehavior stateBehavior;
  private final ResourceState resourceState;
  private final ExpressionLanguage expressionLanguage;
  private final ExpressionProcessor expressionProcessor;

  public ExternalResourceBehavior(
      final BpmnStateBehavior stateBehavior,
      final ResourceState resourceState,
      final ExpressionLanguage expressionLanguage,
      final ExpressionProcessor expressionProcessor) {
    this.stateBehavior = stateBehavior;
    this.resourceState = resourceState;
    this.expressionLanguage = expressionLanguage;
    this.expressionProcessor = expressionProcessor;
  }

  public Either<Failure, Void> createExternalResourceVariables(
      final BpmnElementContext context, final List<LinkedResourceProps> resourceProps) {
    resourceProps.stream()
        .map(LinkedResourceProps::getResourceKey)
        .map(Long::parseLong)
        .forEach(
            resourceKey -> {
              parseResource(context, resourceKey);
            });

    return Either.right(null);
  }

  private Either<Failure, Void> parseResource(
      final BpmnElementContext context, final Long resourceKey) {
    final var resourceOptional =
        resourceState.findResourceByKey(resourceKey, context.getTenantId());
    if (resourceOptional.isEmpty()) {
      return Either.left(
          new Failure(
              "Expected to find a resource with key '%d', but no resource was found."
                  .formatted(resourceKey)));
    }
    final var resource = resourceOptional.get();
    try {
      final var configValues = JSON_MAPPER.readValue(resource.getResource(), ConfigValues.class);
      return createVariables(context, configValues);
    } catch (final JsonProcessingException e) {
      return Either.left(new Failure(e.getMessage()));
    }
  }

  private Either<Failure, Void> createVariables(
      final BpmnElementContext context, final ConfigValues configValues) {
    for (final ConfigValue configValue : configValues.configValues()) {
      final var expression = expressionLanguage.parseExpression(configValue.value());
      final var evaluatedValue =
          expressionProcessor.evaluateAnyExpressionToBuffer(
              expression, context.getElementInstanceKey(), context.getTenantId());

      if (evaluatedValue.isLeft()) {
        return Either.left(evaluatedValue.getLeft());
      }

      stateBehavior.setLocalVariable(
          context, BufferUtil.wrapString(configValue.id()), evaluatedValue.get());
    }
    return Either.right(null);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ConfigValues(List<ConfigValue> configValues) {}

  private record ConfigValue(String id, String value) {}
}
