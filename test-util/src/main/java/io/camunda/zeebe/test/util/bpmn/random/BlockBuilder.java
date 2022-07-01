/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.bpmn.random;

import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import java.util.List;

/**
 * Implementations of this class build blocks of processes and segments of execution paths.
 *
 * <p>Implementations of this class must adhere to the following contract:
 *
 * <ul>
 *   <li>Any randomness in terms of the generated structure must happen at construction time
 *   <li>The method {@code buildFlowNodes(...)} must be deterministic
 *   <li>The method {@code findRandomExecutionPath(...)} must be deterministic for the same random
 *       noise generator
 *   <li>The methods {@code buildFlowNodes(...)} and {@code findRandomExecutionPath(...} call any
 *       relevant nested block builders recursively
 *   <li>Implementations must also provide an implementation of {@link BlockBuilderFactory}
 * </ul>
 */
public interface BlockBuilder {

  /**
   * Appends this blocks flow nodes to the node builder passed in as argument.
   *
   * <p><strong>Contract: </strong> this method must be deterministic. It must not have any random
   * behavior.
   */
  AbstractFlowNodeBuilder<?, ?> buildFlowNodes(final AbstractFlowNodeBuilder<?, ?> nodeBuilder);

  default ExecutionPathSegment findRandomExecutionPath(final ExecutionPathContext context) {
    final boolean shouldGenerateExecutionPath =
        context.hasFoundStartBlockBuilder() || equalsOrContains(context.getStartAtBlockBuilder());

    if (shouldGenerateExecutionPath) {
      if (this == context.getStartAtBlockBuilder()) {
        context.foundBlockBuilder();
      }
      return generateRandomExecutionPath(context);
    } else {
      return new ExecutionPathSegment();
    }
  }

  /** Creates a random execution path segment. */
  ExecutionPathSegment generateRandomExecutionPath(final ExecutionPathContext context);

  String getElementId();

  List<BlockBuilder> getPossibleStartingBlocks();

  default boolean equalsOrContains(final BlockBuilder blockBuilder) {
    return getPossibleStartingBlocks().contains(blockBuilder);
  }
}
