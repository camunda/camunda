/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.util;

/**
 * A read size that halves when the store refuses a write for being too large, and returns to the
 * configured size once a write gets through. Not sticky, because the documents a read fans out into
 * depend on which entries it happens to contain.
 */
public final class AdaptiveBatchSize {

  private final int configured;
  private int current;

  public AdaptiveBatchSize(final int configured) {
    this.configured = configured;
    current = configured;
  }

  public int current() {
    return current;
  }

  public int configured() {
    return configured;
  }

  public void reset() {
    current = configured;
  }

  /** Halves the size, or reports that it is already at one and cannot be made smaller. */
  public boolean halve() {
    if (current <= 1) {
      return false;
    }

    current = current / 2;
    return true;
  }
}
