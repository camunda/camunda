/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED_ONLY_IF_VALUES_MATCH;

import java.util.Set;

public class Retention {

  private static final String PREFIX = "camunda.data.secondary-storage.retention";
  private static final Set<String> LEGACY_ENABLED_PROPERTIES =
      Set.of(
          "camunda.database.retention.enabled",
          "zeebe.broker.exporters.camundaexporter.args.history.retention.enabled");
  private static final Set<String> LEGACY_MINIMUM_AGE_PROPERTIES =
      Set.of(
          "camunda.database.retention.minimumAge",
          "zeebe.broker.exporters.camundaexporter.args.history.retention.minimumAge");

  /** if true, the ILM Policy is created and applied to the index templates */
  private boolean enabled = false;

  /** defines how old the data must be, before the data is deleted as a duration */
  private String minimumAge = "30d";

  public boolean isEnabled() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".enabled",
        enabled,
        Boolean.class,
        SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_ENABLED_PROPERTIES);
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public String getMinimumAge() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".minimum-age",
        minimumAge,
        String.class,
        SUPPORTED_ONLY_IF_VALUES_MATCH,
        LEGACY_MINIMUM_AGE_PROPERTIES);
  }

  public void setMinimumAge(final String minimumAge) {
    this.minimumAge = minimumAge;
  }
}
