/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.Set;

/**
 * Defines configuration for the broker's internal query API, used for direct partition state reads.
 * The prefix for this class is camunda.system.query-api.
 */
public class QueryApi {
  private static final String PREFIX = "camunda.system.query-api";

  private static final Set<String> LEGACY_ENABLED_PROPERTIES =
      Set.of("zeebe.broker.experimental.queryApi.enabled");

  /** Enables the broker's internal query API. */
  private boolean enabled = false;

  public boolean isEnabled() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".enabled",
        enabled,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ENABLED_PROPERTIES);
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }
}
