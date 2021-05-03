/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.configuration.backpressure;

import io.zeebe.broker.system.configuration.ConfigurationEntry;

public final class BackpressureCfg implements ConfigurationEntry {

  private boolean enabled = true;
  private boolean useWindowed = true;
  private LimitAlgorithm algorithm = LimitAlgorithm.VEGAS;
  private final AIMDCfg aimd = new AIMDCfg();
  private final FixedCfg fixed = new FixedCfg();
  private final VegasCfg vegas = new VegasCfg();
  private final GradientCfg gradient = new GradientCfg();
  private final Gradient2Cfg gradient2 = new Gradient2Cfg();

  public boolean isEnabled() {
    return enabled;
  }

  public BackpressureCfg setEnabled(final boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  public boolean useWindowed() {
    return useWindowed;
  }

  public void setUseWindowed(final boolean useWindowed) {
    this.useWindowed = useWindowed;
  }

  public LimitAlgorithm getAlgorithm() {
    return algorithm;
  }

  public void setAlgorithm(final String algorithm) {
    this.algorithm = LimitAlgorithm.valueOf(algorithm.toUpperCase());
  }

  public AIMDCfg getAimd() {
    return aimd;
  }

  public FixedCfg getFixed() {
    return fixed;
  }

  public VegasCfg getVegas() {
    return vegas;
  }

  public GradientCfg getGradient() {
    return gradient;
  }

  public Gradient2Cfg getGradient2() {
    return gradient2;
  }

  @Override
  public String toString() {
    return "BackpressureCfg{"
        + "enabled="
        + enabled
        + ", useWindowed="
        + useWindowed
        + ", algorithm='"
        + algorithm
        + '\''
        + ", aimd="
        + aimd
        + ", fixed="
        + fixed
        + ", vegas="
        + vegas
        + ", gradient="
        + gradient
        + ", gradient2="
        + gradient2
        + '}';
  }

  public enum LimitAlgorithm {
    VEGAS,
    GRADIENT,
    GRADIENT2,
    FIXED,
    AIMD
  }
}
