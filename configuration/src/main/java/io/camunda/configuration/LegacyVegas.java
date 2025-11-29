/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

// Legacy vegas backpressure algorithm configs
public class LegacyVegas {

  private static final int DEFAULT_INITIAL_LIMIT = 1024;
  private static final int DEFAULT_MAX_CONCURRENCY = 1024 * 32;
  private static final double DEFAULT_ALPHA_LIMIT = 0.7;
  private static final double DEFAULT_BETA_LIMIT = 0.95;

  /** The initial limit */
  private int initialLimit = DEFAULT_INITIAL_LIMIT;

  /** The max concurrency */
  private int maxConcurrency = DEFAULT_MAX_CONCURRENCY;

  /** The alpha limit */
  private double alphaLimit = DEFAULT_ALPHA_LIMIT;

  /** The beta limit */
  private double betaLimit = DEFAULT_BETA_LIMIT;

  public int getInitialLimit() {
    return initialLimit;
  }

  public void setInitialLimit(final int initialLimit) {
    this.initialLimit = initialLimit;
  }

  public int getMaxConcurrency() {
    return maxConcurrency;
  }

  public void setMaxConcurrency(final int maxConcurrency) {
    this.maxConcurrency = maxConcurrency;
  }

  public double getAlphaLimit() {
    return alphaLimit;
  }

  public void setAlphaLimit(final double alphaLimit) {
    this.alphaLimit = alphaLimit;
  }

  public double getBetaLimit() {
    return betaLimit;
  }

  public void setBetaLimit(final double betaLimit) {
    this.betaLimit = betaLimit;
  }
}
