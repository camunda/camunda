/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.InputMappings;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.VariableMappingTransformer;
import io.camunda.zeebe.util.Either;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves all of an element's input mappings by evaluating a single FEEL context literal built
 * from every mapping. This is the pre-{@code baddd911435} behavior, kept available because some
 * modeled processes (notably the GitHub Outbound Connector) depend on the sibling-name resolution
 * that FEEL context literals give -- and, per issue camunda/camunda#60551, on the quirks of that
 * resolution when a source references the root being built (self-reference resolves to {@code
 * null}) or a sibling by bare name inside a nested target (the sibling is looked up in the outer
 * context, so a static value can end up replacing the whole nested object).
 *
 * <p>The combined expression is precomputed by {@link
 * VariableMappingTransformer#transformInputMappings} and stored on {@link
 * InputMappings#combinedExpression}, so each activation only pays for evaluation. Targets that
 * produce unparseable FEEL (e.g. empty path segments from {@code a..b}) cause {@link
 * VariableMappingTransformer#transformInputMappings} to throw {@code IllegalStateException} before
 * this resolver is ever selected.
 *
 * <p>Cross-mapping references work only through FEEL's own context-literal name resolution -- no
 * per-mapping {@code prependContext}, no {@link InputMappingResultBuilder}. Byte-compatible with
 * the removed transformer for static source expressions: they are inlined as {@code "<raw>"} with
 * embedded double quotes escaped (see issue #16043), so e.g. source {@code "1"} stays the string
 * {@code "1"} rather than being reparsed as a number by FEEL.
 */
@NullMarked
public final class CombinedInputMappingResolver implements MappingResolver<InputMappings> {

  @Override
  public Either<Failure, DirectBuffer> resolve(
      final InputMappings mappings, final MappingExpressionProcessor processor) {
    return processor.evaluateVariableMappingExpression(mappings.combinedExpression());
  }
}
