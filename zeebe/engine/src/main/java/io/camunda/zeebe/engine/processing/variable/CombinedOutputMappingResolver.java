/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.el.EvaluationContext;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.OutputMappings;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.VariableMappingTransformer;
import io.camunda.zeebe.engine.processing.expression.ScopedEvaluationContext;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashMap;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves output mappings by evaluating a single pre-built FEEL context literal against the outer
 * scope. This restores the pre-{@code #59087} behavior where all mappings were combined into one
 * expression at deploy time.
 *
 * <p>The combined expression is precomputed by {@link VariableMappingTransformer} and stored on
 * {@link OutputMappings}, so each completion only pays for evaluation.
 */
@NullMarked
public final class CombinedOutputMappingResolver implements MappingResolver<OutputMappings> {

  @Override
  public Either<Failure, DirectBuffer> resolve(
      final OutputMappings mappings, final MappingExpressionProcessor processor) {
    // Read the variable to merge from the flow scope
    final ScopedEvaluationContext evaluationContext =
        processor
            .getEvaluationContext()
            .processScoped(processor.getMappingContext().flowScopeKey());

    // First, evaluate the combined expression. Then, merge the result with the scope variables.
    return processor
        .evaluateVariableMappingExpression(mappings.combinedExpression())
        .flatMap(
            mappingResult -> {
              // Hack: using the converter instead of a proper MsgPack merging for simplicity
              final Map<String, Object> mappingResultMap =
                  MsgPackConverter.convertToMap(mappingResult);

              final Map<String, Object> mergeResult = merge(evaluationContext, mappingResultMap);

              return Either.right(
                  BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(mergeResult)));
            });
  }

  private Map<String, Object> merge(
      final EvaluationContext evaluationContext, final Map<String, Object> mappingResult) {
    final Map<String, Object> result = new HashMap<>();

    mappingResult.forEach(
        (key, value) -> {
          final var scopeValue = evaluationContext.getVariable(key);
          if (scopeValue.isLeft()) {
            if (scopeValue.getLeft() == null) {
              result.put(key, value);
              return;
            }

            final var scopeValueMap =
                MsgPackConverter.convertToObject(scopeValue.getLeft(), Object.class);

            if (value instanceof Map) {
              if (scopeValueMap instanceof Map) {
                result.put(
                    key, merge((Map<String, Object>) scopeValueMap, (Map<String, Object>) value));
              } else {
                result.put(key, null); // Consistent with `context merge()`
              }
            } else {
              result.put(key, value);
            }
          } else {
            final EvaluationContext nestedContext = scopeValue.get();

            if (value instanceof Map) {
              final Map<String, Object> map = (Map<String, Object>) value;
              final Map<String, Object> merge = merge(nestedContext, map);
              result.put(key, merge);
            } else {
              result.put(key, value);
            }
          }
        });

    return result;
  }

  private Map<String, Object> merge(
      final Map<String, Object> scopeValueMap, final Map<String, Object> map) {
    final Map<String, Object> result = new HashMap<>(scopeValueMap);
    map.forEach(
        (key, value) -> {
          if (value instanceof Map) {
            final Map<String, Object> nestedMap = (Map<String, Object>) value;
            final Object existingValue = scopeValueMap.get(key);
            if (existingValue instanceof Map) {
              final Map<String, Object> existingMap = (Map<String, Object>) existingValue;
              result.put(key, merge(existingMap, nestedMap));
            } else {
              result.put(key, null); // Consistent with `context merge()`
            }
          } else {
            result.put(key, value);
          }
        });
    return result;
  }
}
