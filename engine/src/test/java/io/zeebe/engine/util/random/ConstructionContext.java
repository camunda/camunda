/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util.random;

import java.util.Random;

public class ConstructionContext {

  private final Random random;
  private final IDGenerator idGenerator;
  private final BlockSequenceBuilderFactory blockSequenceBuilderFactory;
  private final int maxBlocks;
  private final int maxDepth;
  private final int maxBranches;
  private final int currentDepth;

  public ConstructionContext(
      Random random,
      IDGenerator idGenerator,
      BlockSequenceBuilderFactory blockSequenceBuilderFactory,
      int maxBlocks,
      int maxDepth,
      int maxBranches,
      int currentDepth) {
    this.random = random;
    this.idGenerator = idGenerator;
    this.blockSequenceBuilderFactory = blockSequenceBuilderFactory;
    this.maxBlocks = maxBlocks;
    this.maxDepth = maxDepth;
    this.maxBranches = maxBranches;
    this.currentDepth = currentDepth;
  }

  public Random getRandom() {
    return random;
  }

  public IDGenerator getIdGenerator() {
    return idGenerator;
  }

  public BlockSequenceBuilderFactory getBlockSequenceBuilderFactory() {
    return blockSequenceBuilderFactory;
  }

  public int getMaxBlocks() {
    return maxBlocks;
  }

  public int getMaxDepth() {
    return maxDepth;
  }

  public int getMaxBranches() {
    return maxBranches;
  }

  public int getCurrentDepth() {
    return currentDepth;
  }

  public ConstructionContext withIncrementedDepth() {
    return new ConstructionContext(
        random,
        idGenerator,
        blockSequenceBuilderFactory,
        maxBlocks,
        maxDepth,
        maxBranches,
        currentDepth + 1);
  }
}
