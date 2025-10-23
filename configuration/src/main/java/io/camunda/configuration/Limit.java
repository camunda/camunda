/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Limit {

  private static final String PREFIX = "camunda.processing.flow-control.request";

  private static final boolean DEFAULT_ENABLED = true;
  private static final boolean DEFAULT_USE_WINDOWED = true;
  private static final String DEFAULT_ALGORITHM = "aimd";

  private static final Set<String> LEGACY_ENABLED_PROPERTIES =
      Set.of("zeebe.broker.flowControl.request.enabled", "zeebe.broker.backpressure.enabled");
  private static final Set<String> LEGACY_USE_WINDOWED_PROPERTIES =
      Set.of(
          "zeebe.broker.flowControl.request.useWindowed", "zeebe.broker.backpressure.useWindowed");
  private static final Set<String> LEGACY_ALGORITHM_PROPERTIES =
      Set.of("zeebe.broker.flowControl.request.algorithm", "zeebe.broker.backpressure.algorithm");
  private static final Set<String> LEGACY_AIMD_REQUEST_TIMEOUT_PROPERTIES =
      Set.of(
          "zeebe.broker.flowControl.request.aimd.requestTimeout",
          "zeebe.broker.backpressure.aimd.requestTimeout");
  private static final Set<String> LEGACY_AIMD_INITIAL_LIMIT_PROPERTIES =
      Set.of(
          "zeebe.broker.flowControl.request.aimd.initialLimit",
          "zeebe.broker.backpressure.aimd.initialLimit");
  private static final Set<String> LEGACY_AIMD_MIN_LIMIT_PROPERTIES =
      Set.of(
          "zeebe.broker.flowControl.request.aimd.minLimit",
          "zeebe.broker.backpressure.aimd.minLimit");
  private static final Set<String> LEGACY_AIMD_MAX_LIMIT_PROPERTIES =
      Set.of(
          "zeebe.broker.flowControl.request.aimd.maxLimit",
          "zeebe.broker.backpressure.aimd.maxLimit");
  private static final Set<String> LEGACY_AIMD_BACKOFF_RATIO_PROPERTIES =
      Set.of(
          "zeebe.broker.flowControl.request.aimd.backoffRatio",
          "zeebe.broker.backpressure.aimd.backoffRatio");
  private static final Set<String> LEGACY_FIXED_LIMIT_PROPERTIES =
      Set.of(
          "zeebe.broker.flowControl.request.fixed.limit", "zeebe.broker.backpressure.fixed.limit");
  private static final Set<String> LEGACY_VEGAS_ALPHA_PROPERTIES =
      Set.of(
          "zeebe.broker.flowControl.request.vegas.alpha", "zeebe.broker.backpressure.vegas.alpha");
  private static final Set<String> LEGACY_VEGAS_BETA_PROPERTIES =
      Set.of("zeebe.broker.flowControl.request.vegas.beta", "zeebe.broker.backpressure.vegas.beta");
  private static final Set<String> LEGACY_VEGAS_INITIAL_LIMIT_PROPERTIES =
      Set.of(
          "zeebe.broker.flowControl.request.vegas.initialLimit",
          "zeebe.broker.backpressure.vegas.initialLimit");
  private static final Set<String> LEGACY_GRADIENT_MIN_LIMIT_PROPERTIES =
      Set.of(
          "zeebe.broker.flowControl.request.gradient.minLimit",
          "zeebe.broker.backpressure.gradient.minLimit");
  private static final Set<String> LEGACY_GRADIENT_INITIAL_LIMIT_PROPERTIES =
      Set.of(
          "zeebe.broker.flowControl.request.gradient.initialLimit",
          "zeebe.broker.backpressure.gradient.initialLimit");
  private static final Set<String> LEGACY_GRADIENT_RTT_TOLERANCE_PROPERTIES =
      Set.of(
          "zeebe.broker.flowControl.request.gradient.rttTolerance",
          "zeebe.broker.backpressure.gradient.rttTolerance");
  private static final Set<String> LEGACY_GRADIENT2_MIN_LIMIT_PROPERTIES =
      Set.of(
          "zeebe.broker.flowControl.request.gradient2.minLimit",
          "zeebe.broker.backpressure.gradient2.minLimit");
  private static final Set<String> LEGACY_GRADIENT2_INITIAL_LIMIT_PROPERTIES =
      Set.of(
          "zeebe.broker.flowControl.request.gradient2.initialLimit",
          "zeebe.broker.backpressure.gradient2.initialLimit");
  private static final Set<String> LEGACY_GRADIENT2_RTT_TOLERANCE_PROPERTIES =
      Set.of(
          "zeebe.broker.flowControl.request.gradient2.rttTolerance",
          "zeebe.broker.backpressure.gradient2.rttTolerance");
  private static final Set<String> LEGACY_GRADIENT2_LONG_WINDOW_PROPERTIES =
      Set.of(
          "zeebe.broker.flowControl.request.gradient2.longWindow",
          "zeebe.broker.backpressure.gradient2.longWindow");
  private static final Set<String> LEGACY_LEGACY_VEGAS_INITIAL_LIMIT_PROPERTIES =
      Set.of(
          "zeebe.broker.flowControl.request.legacyVegas.initialLimit",
          "zeebe.broker.backpressure.legacyVegas.initialLimit");
  private static final Set<String> LEGACY_LEGACY_VEGAS_MAX_CONCURRENCY_PROPERTIES =
      Set.of(
          "zeebe.broker.flowControl.request.legacyVegas.maxConcurrency",
          "zeebe.broker.backpressure.legacyVegas.maxConcurrency");
  private static final Set<String> LEGACY_LEGACY_VEGAS_ALPHA_LIMIT_PROPERTIES =
      Set.of(
          "zeebe.broker.flowControl.request.legacyVegas.alphaLimit",
          "zeebe.broker.backpressure.legacyVegas.alphaLimit");
  private static final Set<String> LEGACY_LEGACY_VEGAS_BETA_LIMIT_PROPERTIES =
      Set.of(
          "zeebe.broker.flowControl.request.legacyVegas.betaLimit",
          "zeebe.broker.backpressure.legacyVegas.betaLimit");

  /** Enable request limit */
  private boolean enabled = DEFAULT_ENABLED;

  /** Use windowed limit */
  private boolean windowed = DEFAULT_USE_WINDOWED;

  /** The algorithm to use for limiting (aimd, fixed, vegas, gradient, gradient2, legacy-vegas) */
  private String algorithm = DEFAULT_ALGORITHM;

  // Algorithm-specific configurations
  @NestedConfigurationProperty private final Aimd aimd = new Aimd();
  @NestedConfigurationProperty private final Fixed fixed = new Fixed();
  @NestedConfigurationProperty private final Vegas vegas = new Vegas();
  @NestedConfigurationProperty private final Gradient gradient = new Gradient();
  @NestedConfigurationProperty private final Gradient2 gradient2 = new Gradient2();
  @NestedConfigurationProperty private final LegacyVegas legacyVegas = new LegacyVegas();

  public boolean isEnabled() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".enabled",
        enabled,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_ENABLED_PROPERTIES);
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isWindowed() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".windowed",
        windowed,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_USE_WINDOWED_PROPERTIES);
  }

  public void setWindowed(final boolean windowed) {
    this.windowed = windowed;
  }

  public String getAlgorithm() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".algorithm",
        algorithm,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_ALGORITHM_PROPERTIES);
  }

  public void setAlgorithm(final String algorithm) {
    this.algorithm = algorithm;
  }

  public Aimd getAimd() {
    return aimd;
  }

  public Fixed getFixed() {
    return fixed;
  }

  public Vegas getVegas() {
    return vegas;
  }

  public Gradient getGradient() {
    return gradient;
  }

  public Gradient2 getGradient2() {
    return gradient2;
  }

  public LegacyVegas getLegacyVegas() {
    return legacyVegas;
  }

  // Legacy property getters for backward compatibility validation
  public Duration getAimdRequestTimeout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".aimd.request-timeout",
        aimd.getRequestTimeout(),
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_AIMD_REQUEST_TIMEOUT_PROPERTIES);
  }

  public int getAimdInitialLimit() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".aimd.initial-limit",
        aimd.getInitialLimit(),
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_AIMD_INITIAL_LIMIT_PROPERTIES);
  }

  public int getAimdMinLimit() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".aimd.min-limit",
        aimd.getMinLimit(),
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_AIMD_MIN_LIMIT_PROPERTIES);
  }

  public int getAimdMaxLimit() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".aimd.max-limit",
        aimd.getMaxLimit(),
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_AIMD_MAX_LIMIT_PROPERTIES);
  }

  public double getAimdBackoffRatio() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".aimd.backoff-ratio",
        aimd.getBackoffRatio(),
        Double.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_AIMD_BACKOFF_RATIO_PROPERTIES);
  }

  public int getFixedLimit() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".fixed.limit",
        fixed.getLimit(),
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_FIXED_LIMIT_PROPERTIES);
  }

  public int getVegasAlpha() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".vegas.alpha",
        vegas.getAlpha(),
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_VEGAS_ALPHA_PROPERTIES);
  }

  public int getVegasBeta() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".vegas.beta",
        vegas.getBeta(),
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_VEGAS_BETA_PROPERTIES);
  }

  public int getVegasInitialLimit() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".vegas.initial-limit",
        vegas.getInitialLimit(),
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_VEGAS_INITIAL_LIMIT_PROPERTIES);
  }

  public int getGradientMinLimit() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".gradient.min-limit",
        gradient.getMinLimit(),
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_GRADIENT_MIN_LIMIT_PROPERTIES);
  }

  public int getGradientInitialLimit() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".gradient.initial-limit",
        gradient.getInitialLimit(),
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_GRADIENT_INITIAL_LIMIT_PROPERTIES);
  }

  public double getGradientRttTolerance() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".gradient.rtt-tolerance",
        gradient.getRttTolerance(),
        Double.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_GRADIENT_RTT_TOLERANCE_PROPERTIES);
  }

  public int getGradient2MinLimit() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".gradient2.min-limit",
        gradient2.getMinLimit(),
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_GRADIENT2_MIN_LIMIT_PROPERTIES);
  }

  public int getGradient2InitialLimit() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".gradient2.initial-limit",
        gradient2.getInitialLimit(),
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_GRADIENT2_INITIAL_LIMIT_PROPERTIES);
  }

  public double getGradient2RttTolerance() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".gradient2.rtt-tolerance",
        gradient2.getRttTolerance(),
        Double.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_GRADIENT2_RTT_TOLERANCE_PROPERTIES);
  }

  public int getGradient2LongWindow() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".gradient2.long-window",
        gradient2.getLongWindow(),
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_GRADIENT2_LONG_WINDOW_PROPERTIES);
  }

  public int getLegacyVegasInitialLimit() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".legacy-vegas.initial-limit",
        legacyVegas.getInitialLimit(),
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_LEGACY_VEGAS_INITIAL_LIMIT_PROPERTIES);
  }

  public int getLegacyVegasMaxConcurrency() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".legacy-vegas.max-concurrency",
        legacyVegas.getMaxConcurrency(),
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_LEGACY_VEGAS_MAX_CONCURRENCY_PROPERTIES);
  }

  public double getLegacyVegasAlphaLimit() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".legacy-vegas.alpha-limit",
        legacyVegas.getAlphaLimit(),
        Double.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_LEGACY_VEGAS_ALPHA_LIMIT_PROPERTIES);
  }

  public double getLegacyVegasBetaLimit() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".legacy-vegas.beta-limit",
        legacyVegas.getBetaLimit(),
        Double.class,
        BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_LEGACY_VEGAS_BETA_LIMIT_PROPERTIES);
  }
}
