/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.bpmn.random.blocks;

import io.camunda.zeebe.test.util.bpmn.random.BlockBuilder;
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPathContext;
import io.camunda.zeebe.test.util.bpmn.random.ExecutionPathSegment;
import java.util.List;

public abstract class AbstractBlockBuilder implements BlockBuilder {
  protected final String elementId;
  private final String prefix;

  protected AbstractBlockBuilder(final String elementId) {
    this.elementId = elementId;
    prefix = "";
  }

  protected AbstractBlockBuilder(final String prefix, final String elementId) {
    this.elementId = elementId;
    this.prefix = prefix;
  }

  @Override
  public ExecutionPathSegment findRandomExecutionPath(final ExecutionPathContext context) {
    final boolean shouldGenerateExecutionPath =
        context.hasFoundStartElement() || equalsOrContains(context.getStartElementIds());

    if (shouldGenerateExecutionPath) {
      if (context.getStartElementIds().contains(getElementId())) {
        context.foundStartElement();
      }
      return generateRandomExecutionPath(context);
    } else {
      return new ExecutionPathSegment();
    }
  }

  @Override
  public String getElementId() {
    return prefix + elementId;
  }

  @Override
  public List<BlockBuilder> getPossibleStartingBlocks() {
    return List.of(this);
  }

  @Override
  public List<String> getPossibleStartingElementIds() {
    return getPossibleStartingBlocks().stream().map(BlockBuilder::getElementId).toList();
  }

  @Override
  public boolean equalsOrContains(final List<String> startElementIds) {
    return getPossibleStartingElementIds().stream().anyMatch(startElementIds::contains);
  }
}
