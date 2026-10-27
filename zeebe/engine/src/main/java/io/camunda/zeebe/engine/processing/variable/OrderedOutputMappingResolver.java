/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.OutputMapping;
import io.camunda.zeebe.engine.processing.deployment.model.element.OutputMappings;
import io.camunda.zeebe.util.Either;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves output mappings one by one in modeling order. Each mapping's source expression sees the
 * results of the earlier mappings (they take priority over same-named scope variables) and falls
 * back to the element's variable scope otherwise. A nested target merges with the existing scope
 * value at every path level. Resolution stops at the first failing mapping.
 *
 * <p>The scope value for nested-target merges is obtained from the pre-scoped evaluation context
 * carried by the {@link MappingExpressionProcessor}: the top-level variable is looked up there,
 * then navigated along the remaining path segments via {@link MsgPackPath}.
 */
@NullMarked
public final class OrderedOutputMappingResolver implements MappingResolver<OutputMappings> {

  @Override
  public Either<Failure, DirectBuffer> resolve(
      final OutputMappings outputMappings, final MappingExpressionProcessor processor) {
    final var resultBuilder =
        new OutputMappingResultBuilder(
            path -> {
              final var value = processor.getEvaluationContext().getVariable(path.getFirst());
              final var rootValue = value.isLeft() ? value.getLeft() : null;
              return rootValue == null ? null : MsgPackPath.navigate(rootValue, path, 1);
            });
    final var boundProcessor = processor.prependContext(resultBuilder::get);

    for (final OutputMapping mapping : outputMappings.mappings()) {
      final var result = boundProcessor.evaluateVariableMappingExpression(mapping.source());
      if (result.isLeft()) {
        return Either.left(result.getLeft());
      }
      resultBuilder.put(mapping.targetPath(), result.get());
    }
    return Either.right(resultBuilder.toDocument());
  }
}
