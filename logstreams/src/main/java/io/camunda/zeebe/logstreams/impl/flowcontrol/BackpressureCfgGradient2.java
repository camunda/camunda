/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import static io.camunda.zeebe.logstreams.impl.flowcontrol.BackpressureConstants.ENV_BP_APPENDER_GRADIENT2_INIT_LIMIT;
import static io.camunda.zeebe.logstreams.impl.flowcontrol.BackpressureConstants.ENV_BP_APPENDER_GRADIENT2_LONG_WINDOW;
import static io.camunda.zeebe.logstreams.impl.flowcontrol.BackpressureConstants.ENV_BP_APPENDER_GRADIENT2_MAX_CONCURRENCY;
import static io.camunda.zeebe.logstreams.impl.flowcontrol.BackpressureConstants.ENV_BP_APPENDER_GRADIENT2_MIN_LIMIT;
import static io.camunda.zeebe.logstreams.impl.flowcontrol.BackpressureConstants.ENV_BP_APPENDER_GRADIENT2_QUEUE_SIZE;
import static io.camunda.zeebe.logstreams.impl.flowcontrol.BackpressureConstants.ENV_BP_APPENDER_GRADIENT2_RTT_TOLERANCE;

import com.netflix.concurrency.limits.limit.AbstractLimit;
import com.netflix.concurrency.limits.limit.Gradient2Limit;
import io.camunda.zeebe.util.Environment;

/**
 * This class should be later be located in the broker configs - due to the primitive usage
 * currently we are not able to access the BrokerCfg, this is the reason why the configuration is
 * only based on environment variables.
 */
final class BackpressureCfgGradient2 implements BackpressureCfg {

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

  public BackpressureCfgGradient2 setInitialLimit(final int initialLimit) {
    this.initialLimit = initialLimit;
    return this;
  }

  public int getMaxConcurrency() {
    return maxConcurrency;
  }

  public BackpressureCfgGradient2 setMaxConcurrency(final int maxConcurrency) {
    this.maxConcurrency = maxConcurrency;
    return this;
  }

  public int getQueueSize() {
    return queueSize;
  }

  public BackpressureCfgGradient2 setQueueSize(final int queueSize) {
    this.queueSize = queueSize;
    return this;
  }

  public int getMinLimit() {
    return minLimit;
  }

  public BackpressureCfgGradient2 setMinLimit(final int minLimit) {
    this.minLimit = minLimit;
    return this;
  }

  public int getLongWindow() {
    return longWindow;
  }

  public BackpressureCfgGradient2 setLongWindow(final int longWindow) {
    this.longWindow = longWindow;
    return this;
  }

  public double getRttTolerance() {
    return rttTolerance;
  }

  public BackpressureCfgGradient2 setRttTolerance(final double rttTolerance) {
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
    return "BackpressureCfgGradient2{"
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
