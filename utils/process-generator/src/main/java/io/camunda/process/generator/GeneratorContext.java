/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.generator;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class GeneratorContext {

  private static final int MAXIMUM_DEPTH = 3;
  private final Random random;
  private final AtomicLong id = new AtomicLong(1);
  private int currentDepth = 1;

  public GeneratorContext(final Random random) {
    this.random = random;
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
}
