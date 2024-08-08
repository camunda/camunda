/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator;

import io.camunda.process.generator.execution.ProcessExecutionStep;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class GeneratorContext {

  private static final int MAXIMUM_DEPTH = 3;
  private static final int MAXIMUM_BRANCHES = 8;
  private final long seed;
  private final Random random;
  private final AtomicLong id = new AtomicLong(1);
  private int currentDepth = 1;
  private final List<ProcessExecutionStep> executionPath = new ArrayList<>();
  private int currentAmountOfBranches = 1;

  public GeneratorContext(final long seed) {
    this.seed = seed;
    random = new Random(seed);
  }

  public List<ProcessExecutionStep> getExecutionPath() {
    return executionPath;
  }

  public String createNewId() {
    return "id_" + id.getAndIncrement();
  }

  public int getRandomNumber(final int limit) {
    return random.nextInt(limit);
  }

  /** Returns a random number between the lower and upper bound, inclusive. */
  public int getRandomNumber(final int lowerBound, final int upperBound) {
    return random.nextInt(lowerBound, upperBound + 1);
  }

  public void incrementCurrentDepth() {
    currentDepth++;
  }

  public void decrementCurrentDepth() {
    currentDepth--;
  }

  public boolean canGoDeeper() {
    return currentDepth < MAXIMUM_DEPTH;
  }

  private int getMaximumAvailableAmountOfBranches() {
    return MAXIMUM_BRANCHES - currentAmountOfBranches;
  }

  public int getRandomNumberOfBranches(final int lowerBound, final int upperBound) {
    final int numberOfBranches = getRandomNumber(lowerBound, upperBound);
    final var numberOfBranchesToGenerate =
        Math.min(getMaximumAvailableAmountOfBranches(), numberOfBranches);
    currentAmountOfBranches += numberOfBranchesToGenerate;
    return numberOfBranchesToGenerate;
  }

  public boolean canAddBranches() {
    return currentAmountOfBranches + 1 < MAXIMUM_BRANCHES;
  }

  public GeneratorContext addExecutionStep(final ProcessExecutionStep executionStep) {
    executionPath.add(executionStep);
    return this;
  }

  public long getSeed() {
    return seed;
  }

  public boolean getRandomBoolean() {
    return random.nextBoolean();
  }
}
