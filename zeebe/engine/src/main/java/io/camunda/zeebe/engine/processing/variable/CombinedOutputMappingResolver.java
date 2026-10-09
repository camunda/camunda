/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.OutputMappings;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.VariableMappingTransformer;
import io.camunda.zeebe.util.Either;
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
    return processor.evaluateVariableMappingExpression(mappings.combinedExpression());
  }
}
