/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.backpressure;

/** A Vegas configuration that matches our old default values for the log storage appender. */
public final class LegacyVegasCfg {

  private int initialLimit = 1024;
  private int maxConcurrency = 1024 * 32;
  private double alphaLimit = 0.7;
  private double betaLimit = 0.95;

  public void setInitialLimit(final int initialLimit) {
    this.initialLimit = initialLimit;
  }

  public void setAlphaLimit(final double alphaLimit) {
    this.alphaLimit = alphaLimit;
  }

  public void setBetaLimit(final double betaLimit) {
    this.betaLimit = betaLimit;
  }

  public double alphaLimit() {
    return alphaLimit;
  }

  public double betaLimit() {
    return betaLimit;
  }

  public int initialLimit() {
    return initialLimit;
  }

  public int getMaxConcurrency() {
    return maxConcurrency;
  }

  public void setMaxConcurrency(final int maxConcurrency) {
    this.maxConcurrency = maxConcurrency;
  }

  @Override
  public String toString() {
    return "LegacyVegasCfg{"
        + "initialLimit="
        + initialLimit
        + ", maxConcurrency="
        + maxConcurrency
        + ", alphaLimit="
        + alphaLimit
        + ", betaLimit="
        + betaLimit
        + '}';
  }
}
