/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util.bpmn.random;

import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.test.util.bpmn.random.blocks.BlockSequenceBuilder.BlockSequenceBuilderFactory;
import java.util.Random;
import java.util.function.Consumer;

/** This class captures information that are needed during the construction of a random process */
public final class ConstructionContext {

  private static final Consumer<BpmnModelInstance> NOOP = x -> {};

  private final Random random;
  private final IDGenerator idGenerator;
  private final BlockSequenceBuilderFactory blockSequenceBuilderFactory;
  private final int maxBlocks;
  private final int maxDepth;
  private final int maxBranches;
  private final int currentDepth;
  private final Consumer<BpmnModelInstance> onAddCalledChildProcessCallback;

  /**
   * Create a construction context
   *
   * @param random the random generator to use for random elements
   * @param idGenerator the id generator to use
   * @param blockSequenceBuilderFactory the block sequence builder factory to use
   * @param maxBlocks the maximum number of blocks that should appear in a sequence
   * @param maxDepth the maximum level of depth for nested elements
   * @param maxBranches the maximum number of outgoing branches from a given node
   * @param currentDepth the current level of depth in the construction process
   * @param onAddCalledChildProcessCallback consumer that is called for every child process that is
   *     called from the main process
   */
  ConstructionContext(
      final Random random,
      final IDGenerator idGenerator,
      final BlockSequenceBuilderFactory blockSequenceBuilderFactory,
      final int maxBlocks,
      final int maxDepth,
      final int maxBranches,
      final int currentDepth,
      final Consumer<BpmnModelInstance> onAddCalledChildProcessCallback) {
    this.random = random;
    this.idGenerator = idGenerator;
    this.blockSequenceBuilderFactory = blockSequenceBuilderFactory;
    this.maxBlocks = maxBlocks;
    this.maxDepth = maxDepth;
    this.maxBranches = maxBranches;
    this.currentDepth = currentDepth;
    this.onAddCalledChildProcessCallback = onAddCalledChildProcessCallback;
  }

  public ConstructionContext(
      final Random random,
      final IDGenerator idGenerator,
      final BlockSequenceBuilderFactory factory,
      final Integer maxBlocks,
      final Integer maxDepth,
      final Integer maxBranches,
      final int currentDepth) {
    this(random, idGenerator, factory, maxBlocks, maxDepth, maxBranches, currentDepth, NOOP);
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

  /**
   * Returns a derived construction context with current depth incremented by 1
   *
   * @return derived construction context with current depth incremented by 1
   */
  public ConstructionContext withIncrementedDepth() {
    return new ConstructionContext(
        random,
        idGenerator,
        blockSequenceBuilderFactory,
        maxBlocks,
        maxDepth,
        maxBranches,
        currentDepth + 1,
        onAddCalledChildProcessCallback);
  }

  public ConstructionContext withAddCalledChildProcessCallback(
      final Consumer<BpmnModelInstance> callback) {
    return new ConstructionContext(
        random,
        idGenerator,
        blockSequenceBuilderFactory,
        maxBlocks,
        maxDepth,
        maxBranches,
        currentDepth,
        callback);
  }
}
