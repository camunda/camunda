/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.util.Set;

public class Metrics {

  private static final String PREFIX = "camunda.monitoring.metrics";

  private static final Set<String> LEGACY_ENABLE_ACTOR_METRICS_PPROPERTIES =
      Set.of("zeebe.broker.experimental.features.enableActorMetrics");

  /** Controls whether to collect metrics about actor usage such as actor job execution latencies */
  private boolean actor = true;

  public boolean isActor() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".actor",
        actor,
        Boolean.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ENABLE_ACTOR_METRICS_PPROPERTIES);
  }

  public void setActor(final boolean actor) {
    this.actor = actor;
  }
}
