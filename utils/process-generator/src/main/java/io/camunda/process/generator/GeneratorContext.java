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
  private final long seed;
  private final Random random;
  private final AtomicLong id = new AtomicLong(1);
  private int currentDepth = 1;
  private final List<ProcessExecutionStep> executionPath = new ArrayList<>();

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

  public void incrementCurrentDepth() {
    currentDepth++;
  }

  public void decrementCurrentDepth() {
    currentDepth--;
  }

  public boolean shouldGoDeeper() {
    return currentDepth < MAXIMUM_DEPTH;
  }

  public GeneratorContext addExecutionStep(final ProcessExecutionStep executionStep) {
    executionPath.add(executionStep);
    return this;
  }

  public long getSeed() {
    return seed;
  }
}
