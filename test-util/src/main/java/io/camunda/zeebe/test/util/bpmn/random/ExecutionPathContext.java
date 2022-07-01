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
import java.util.Random;

public class ExecutionPathContext {

  final String startAtElementId;
  final List<BlockBuilder> secondaryStartBlockBuilders;
  boolean foundBlockBuilder = false;
  final Random random;

  public ExecutionPathContext(final String startAtElementId, final Random random) {
    this.startAtElementId = startAtElementId;
    secondaryStartBlockBuilders = new ArrayList<>();
    this.random = random;
  }

  public String getStartAtElementId() {
    return startAtElementId;
  }

  public boolean hasFoundStartBlockBuilder() {
    return foundBlockBuilder;
  }

  /**
   * Sets a flag in the context that we have found the block that we want the process to start at.
   * All blocks that we come across after this flag is set to true will always have their execution
   * path generated.
   *
   * @return this
   */
  public ExecutionPathContext foundBlockBuilder() {
    foundBlockBuilder = true;
    return this;
  }

  public void addSecondaryStartBlockBuilder(final BlockBuilder blockBuilder) {
    secondaryStartBlockBuilders.add(blockBuilder);
  }

  public List<String> getStartElementIds() {
    final List<String> startElementIds = new ArrayList<>();
    startElementIds.add(startAtElementId);
    startElementIds.addAll(
        secondaryStartBlockBuilders.stream().map(BlockBuilder::getElementId).toList());
    return startElementIds;
  }

  public Random getRandom() {
    return random;
  }
}
