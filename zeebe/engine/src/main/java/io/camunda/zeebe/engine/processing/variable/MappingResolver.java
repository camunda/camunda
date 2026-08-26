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
import io.camunda.zeebe.util.Either;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

/**
 * A swappable strategy for resolving an element's input mappings into a single MsgPack result
 * document. Implementations differ in how (and whether) earlier mappings' results are visible to
 * later ones, but never apply the result to variable scope themselves — the caller keeps ownership
 * of doing that, so a future shadow/comparison mode can run multiple resolvers and diff their
 * documents without touching this interface again.
 */
@NullMarked
public interface MappingResolver {

  /**
   * Resolves the given input mappings into a single MsgPack result document.
   *
   * @param inputMappings the element's input mappings, in modeling order
   * @param processor the pre-scoped expression processor; its evaluation context is already scoped
   *     to the element instance, and carries {@link MappingExpressionProcessor#getMappingContext()}
   *     with element identity (element ID, scope key, process instance key, process definition key,
   *     tenant ID) for diagnostic use
   * @return either the resolved result document, or the failure of the first mapping that failed to
   *     evaluate
   */
  Either<Failure, DirectBuffer> resolveInputMappings(
      InputMappings inputMappings, MappingExpressionProcessor processor);
}
