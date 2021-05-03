/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.logstreams.impl.backpressure;

import static io.zeebe.logstreams.impl.backpressure.BackpressureConstants.ENV_BP_APPENDER_VEGAS_ALPHA_LIMIT;
import static io.zeebe.logstreams.impl.backpressure.BackpressureConstants.ENV_BP_APPENDER_VEGAS_BETA_LIMIT;
import static io.zeebe.logstreams.impl.backpressure.BackpressureConstants.ENV_BP_APPENDER_VEGAS_INIT_LIMIT;
import static io.zeebe.logstreams.impl.backpressure.BackpressureConstants.ENV_BP_APPENDER_VEGAS_MAX_CONCURRENCY;

import com.netflix.concurrency.limits.limit.AbstractLimit;
import com.netflix.concurrency.limits.limit.VegasLimit;
import io.zeebe.util.Environment;

/**
 * This class should be later be located in the broker configs - due to the primitive usage
 * currently we are not able to access the BrokerCfg, this is the reason why the configuration is
 * only based on environment variables.
 */
public final class AppenderVegasCfg implements AlgorithmCfg {

  private int initialLimit = 1024;
  private int maxConcurrency = 1024 * 32;

  /*
   * - Copied from the Vegas JavaDoc -
   * Queue size is calculated using the formula,
   *  queue_use = limit − BWE×RTTnoLoad = limit × (1 − RTTnoLoad/RTTactual)
   *
   * For traditional TCP Vegas alpha is typically 2-3 and beta is typically 4-6.  To allow for better growth and
   * stability at higher limits we set alpha=Max(3, 10% of the current limit) and beta=Max(6, 20% of the current limit)
   *
   * ------
   *
   * We tested with 10% and 20%, but do not get good results with the default values of
   * * alpha = Max(3, limit * 0.7)
   * * beta= Max(3, limit * 0.95)
   * We get much better results
   */
  private double alphaLimit = 0.7;
  private double betaLimit = 0.95;

  @Override
  public void applyEnvironment(final Environment environment) {
    environment.getInt(ENV_BP_APPENDER_VEGAS_INIT_LIMIT).ifPresent(this::setInitialLimit);
    environment.getInt(ENV_BP_APPENDER_VEGAS_MAX_CONCURRENCY).ifPresent(this::setMaxConcurrency);
    environment.getDouble(ENV_BP_APPENDER_VEGAS_ALPHA_LIMIT).ifPresent(this::setAlphaLimit);
    environment.getDouble(ENV_BP_APPENDER_VEGAS_BETA_LIMIT).ifPresent(this::setBetaLimit);
  }

  public int getInitialLimit() {
    return initialLimit;
  }

  public AppenderVegasCfg setInitialLimit(final int initialLimit) {
    this.initialLimit = initialLimit;
    return this;
  }

  public int getMaxConcurrency() {
    return maxConcurrency;
  }

  public AppenderVegasCfg setMaxConcurrency(final int maxConcurrency) {
    this.maxConcurrency = maxConcurrency;
    return this;
  }

  public double getAlphaLimit() {
    return alphaLimit;
  }

  public AppenderVegasCfg setAlphaLimit(final double alphaLimit) {
    this.alphaLimit = alphaLimit;
    return this;
  }

  public double getBetaLimit() {
    return betaLimit;
  }

  public AppenderVegasCfg setBetaLimit(final double betaLimit) {
    this.betaLimit = betaLimit;
    return this;
  }

  @Override
  public AbstractLimit get() {
    return VegasLimit.newBuilder()
        .alpha(limit -> Math.max(3, (int) (limit * alphaLimit)))
        .beta(limit -> Math.max(6, (int) (limit * betaLimit)))
        .initialLimit(initialLimit)
        .maxConcurrency(maxConcurrency)
        // per default Vegas uses log10
        .increase(limit -> limit + Math.log(limit))
        .decrease(limit -> limit - Math.log(limit))
        .build();
  }

  @Override
  public String toString() {
    return "AppenderVegasCfg{"
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
