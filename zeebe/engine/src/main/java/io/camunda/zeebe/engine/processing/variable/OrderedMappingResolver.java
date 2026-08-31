/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.InputMapping;
import io.camunda.zeebe.engine.processing.deployment.model.element.InputMappings;
import io.camunda.zeebe.util.Either;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves input mappings one by one in modeling order. Each mapping's source expression sees the
 * results of the earlier mappings and falls back to the element's variable scope otherwise. An
 * earlier result at a nested target shadows a same-named scope variable only partially: the keys it
 * defined win, and the rest fall through to the scope value. An earlier result assigned to a whole
 * name shadows it totally. Resolution stops at the first failing mapping.
 */
@NullMarked
public final class OrderedMappingResolver implements MappingResolver {

  @Override
  public Either<Failure, DirectBuffer> resolveInputMappings(
      final InputMappings inputMappings, final MappingExpressionProcessor processor) {
    final var resultBuilder =
        new InputMappingResultBuilder(
            name -> {
              final var value = processor.getEvaluationContext().getVariable(name);
              return value.isLeft() ? value.getLeft() : null;
            });
    final var boundProcessor = processor.prependContext(resultBuilder::get);

    for (final InputMapping mapping : inputMappings.mappings()) {
      final var result = boundProcessor.evaluateVariableMappingExpression(mapping.source());
      if (result.isLeft()) {
        return Either.left(result.getLeft());
      }
      resultBuilder.put(mapping.targetPath(), result.get());
    }
    return Either.right(resultBuilder.toDocument());
  }
}
