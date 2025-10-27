/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.util.Set;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Monitoring {

  private static final String PREFIX = "camunda.monitoring";

  private static final Set<String> LEGACY_JFR_PROPERTIES = Set.of("camunda.flags.jfr.metrics");

  /** Configure metrics */
  @NestedConfigurationProperty Metrics metrics = new Metrics();

  private boolean isJfr;

  public Metrics getMetrics() {
    return metrics;
  }

  public void setMetrics(final Metrics metrics) {
    this.metrics = metrics;
  }

  public boolean isJfr() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".jfr",
        isJfr,
        Boolean.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_JFR_PROPERTIES);
  }

  public void setJfr(final boolean jfr) {
    isJfr = jfr;
  }
}
