/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.bpmn.random;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ExecutionPathContext {

  final BlockBuilder startAtBlockBuilder;
  final List<BlockBuilder> secondaryStartBlockBuilders;
  boolean foundBlockBuilder = false;

  public ExecutionPathContext(final BlockBuilder startAtBlockBuilder) {
    this.startAtBlockBuilder = startAtBlockBuilder;
    secondaryStartBlockBuilders = new ArrayList<>();
  }

  public BlockBuilder getStartAtBlockBuilder() {
    return startAtBlockBuilder;
  }

  public boolean hasFoundStartBlockBuilder() {
    return foundBlockBuilder;
  }

  public ExecutionPathContext foundBlockBuilder() {
    foundBlockBuilder = true;
    return this;
  }

  public void addSecondaryStartBlockBuilder(final BlockBuilder blockBuilder) {
    secondaryStartBlockBuilders.add(blockBuilder);
  }

  public List<String> getStartElementIds() {
    final List<String> startElementIds = new ArrayList<>();
    startElementIds.add(startAtBlockBuilder.getElementId());
    startElementIds.addAll(
        secondaryStartBlockBuilders.stream()
            .map(BlockBuilder::getElementId)
            .collect(Collectors.toList()));
    return startElementIds;
  }
}
