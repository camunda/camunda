/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

// Vegas backpressure algorithm configs
public class Vegas {

  private static final int DEFAULT_ALPHA = 3;
  private static final int DEFAULT_BETA = 6;
  private static final int DEFAULT_INITIAL_LIMIT = 20;

  /** The alpha value */
  private int alpha = DEFAULT_ALPHA;

  /** The beta value */
  private int beta = DEFAULT_BETA;

  /** The initial limit */
  private int initialLimit = DEFAULT_INITIAL_LIMIT;

  public int getAlpha() {
    return alpha;
  }

  public void setAlpha(final int alpha) {
    this.alpha = alpha;
  }

  public int getBeta() {
    return beta;
  }

  public void setBeta(final int beta) {
    this.beta = beta;
  }

  public int getInitialLimit() {
    return initialLimit;
  }

  public void setInitialLimit(final int initialLimit) {
    this.initialLimit = initialLimit;
  }
}
