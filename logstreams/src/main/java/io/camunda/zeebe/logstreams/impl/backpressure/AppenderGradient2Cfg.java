/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.logstreams.impl.backpressure;

import static io.zeebe.logstreams.impl.backpressure.BackpressureConstants.ENV_BP_APPENDER_GRADIENT2_INIT_LIMIT;
import static io.zeebe.logstreams.impl.backpressure.BackpressureConstants.ENV_BP_APPENDER_GRADIENT2_LONG_WINDOW;
import static io.zeebe.logstreams.impl.backpressure.BackpressureConstants.ENV_BP_APPENDER_GRADIENT2_MAX_CONCURRENCY;
import static io.zeebe.logstreams.impl.backpressure.BackpressureConstants.ENV_BP_APPENDER_GRADIENT2_MIN_LIMIT;
import static io.zeebe.logstreams.impl.backpressure.BackpressureConstants.ENV_BP_APPENDER_GRADIENT2_QUEUE_SIZE;
import static io.zeebe.logstreams.impl.backpressure.BackpressureConstants.ENV_BP_APPENDER_GRADIENT2_RTT_TOLERANCE;

import com.netflix.concurrency.limits.limit.AbstractLimit;
import com.netflix.concurrency.limits.limit.Gradient2Limit;
import io.zeebe.util.Environment;

/**
 * This class should be later be located in the broker configs - due to the primitive usage
 * currently we are not able to access the BrokerCfg, this is the reason why the configuration is
 * only based on environment variables.
 */
public final class AppenderGradient2Cfg implements AlgorithmCfg {

  private int initialLimit = 1024;
  private int maxConcurrency = 1024 * 32;
  private int queueSize = 32;
  private int minLimit = 256;
  private int longWindow = 1200;
  private double rttTolerance = 1.5;

  @Override
  public void applyEnvironment(final Environment environment) {
    environment.getInt(ENV_BP_APPENDER_GRADIENT2_INIT_LIMIT).ifPresent(this::setInitialLimit);
    environment
        .getInt(ENV_BP_APPENDER_GRADIENT2_MAX_CONCURRENCY)
        .ifPresent(this::setMaxConcurrency);
    environment.getInt(ENV_BP_APPENDER_GRADIENT2_QUEUE_SIZE).ifPresent(this::setQueueSize);
    environment.getInt(ENV_BP_APPENDER_GRADIENT2_MIN_LIMIT).ifPresent(this::setMinLimit);
    environment.getInt(ENV_BP_APPENDER_GRADIENT2_LONG_WINDOW).ifPresent(this::setLongWindow);
    environment.getDouble(ENV_BP_APPENDER_GRADIENT2_RTT_TOLERANCE).ifPresent(this::setRttTolerance);
  }

  public int getInitialLimit() {
    return initialLimit;
  }

  public AppenderGradient2Cfg setInitialLimit(final int initialLimit) {
    this.initialLimit = initialLimit;
    return this;
  }

  public int getMaxConcurrency() {
    return maxConcurrency;
  }

  public AppenderGradient2Cfg setMaxConcurrency(final int maxConcurrency) {
    this.maxConcurrency = maxConcurrency;
    return this;
  }

  public int getQueueSize() {
    return queueSize;
  }

  public AppenderGradient2Cfg setQueueSize(final int queueSize) {
    this.queueSize = queueSize;
    return this;
  }

  public int getMinLimit() {
    return minLimit;
  }

  public AppenderGradient2Cfg setMinLimit(final int minLimit) {
    this.minLimit = minLimit;
    return this;
  }

  public int getLongWindow() {
    return longWindow;
  }

  public AppenderGradient2Cfg setLongWindow(final int longWindow) {
    this.longWindow = longWindow;
    return this;
  }

  public double getRttTolerance() {
    return rttTolerance;
  }

  public AppenderGradient2Cfg setRttTolerance(final double rttTolerance) {
    this.rttTolerance = rttTolerance;
    return this;
  }

  @Override
  public AbstractLimit get() {
    return Gradient2Limit.newBuilder()
        .initialLimit(initialLimit)
        .maxConcurrency(maxConcurrency)
        .queueSize(queueSize)
        .minLimit(minLimit)
        .longWindow(longWindow)
        .rttTolerance(rttTolerance)
        .build();
  }

  @Override
  public String toString() {
    return "AppenderGradient2Cfg{"
        + "initialLimit="
        + initialLimit
        + ", maxConcurrency="
        + maxConcurrency
        + ", queueSize="
        + queueSize
        + ", minLimit="
        + minLimit
        + ", longWindow="
        + longWindow
        + ", rttTolerance="
        + rttTolerance
        + '}';
  }
}
