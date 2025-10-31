/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

// Gradient2 backpressure algorithm configs
public class Gradient2 {

  private static final int DEFAULT_MIN_LIMIT = 10;
  private static final int DEFAULT_INITIAL_LIMIT = 20;
  private static final double DEFAULT_RTT_TOLERANCE = 2.0;
  private static final int DEFAULT_LONG_WINDOW = 600;

  /** The minimum limit */
  private int minLimit = DEFAULT_MIN_LIMIT;

  /** The initial limit */
  private int initialLimit = DEFAULT_INITIAL_LIMIT;

  /** The RTT tolerance */
  private double rttTolerance = DEFAULT_RTT_TOLERANCE;

  /** The long window */
  private int longWindow = DEFAULT_LONG_WINDOW;

  public int getMinLimit() {
    return minLimit;
  }

  public void setMinLimit(final int minLimit) {
    this.minLimit = minLimit;
  }

  public int getInitialLimit() {
    return initialLimit;
  }

  public void setInitialLimit(final int initialLimit) {
    this.initialLimit = initialLimit;
  }

  public double getRttTolerance() {
    return rttTolerance;
  }

  public void setRttTolerance(final double rttTolerance) {
    this.rttTolerance = rttTolerance;
  }

  public int getLongWindow() {
    return longWindow;
  }

  public void setLongWindow(final int longWindow) {
    this.longWindow = longWindow;
  }
}
