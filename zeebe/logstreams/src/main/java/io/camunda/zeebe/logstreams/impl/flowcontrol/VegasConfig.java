/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import com.netflix.concurrency.limits.limit.AbstractLimit;
import com.netflix.concurrency.limits.limit.VegasLimit;

/**
 * This class should be later be located in the broker configs - due to the primitive usage
 * currently we are not able to access the BrokerCfg, this is the reason why the configuration is
 * only based on environment variables.
 */
public final class VegasConfig {

  private final int initialLimit = 1024;
  private final int maxConcurrency = 1024 * 32;

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
  private final double alphaLimit = 0.7;
  private final double betaLimit = 0.95;

  public int getInitialLimit() {
    return initialLimit;
  }

  public int getMaxConcurrency() {
    return maxConcurrency;
  }

  public double getAlphaLimit() {
    return alphaLimit;
  }

  public double getBetaLimit() {
    return betaLimit;
  }

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
    return "Vegas{"
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
