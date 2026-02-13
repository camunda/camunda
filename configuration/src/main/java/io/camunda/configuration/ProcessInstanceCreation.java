/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.util.Set;

public class ProcessInstanceCreation {
  private static final String PREFIX = "camunda.process-instance-creation";
  private static final boolean DEFAULT_BUSINESS_ID_UNIQUENESS_ENABLED = false;
  private static final Set<String> LEGACY_BUSINESS_ID_UNIQUENESS_ENABLED_PROPERTIES =
      Set.of(
          "zeebe.broker.experimental.engine.processInstanceCreation.businessIdUniquenessEnabled");

  /**
   * Controls uniqueness enforcement of business IDs across active process instances.
   *
   * <ul>
   *   <li><b>Disabled (default):</b> Multiple active process instances can share the same business
   *       ID. No tracking or validation is performed.
   *   <li><b>Enabled:</b> Creating a process instance with a business ID that is already in use by
   *       an active process instance will be rejected. Business IDs of process instances created
   *       before enabling this setting are not tracked, so duplicates with those are not detected.
   * </ul>
   */
  private boolean businessIdUniquenessEnabled = DEFAULT_BUSINESS_ID_UNIQUENESS_ENABLED;

  public boolean isBusinessIdUniquenessEnabled() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".business-id-uniqueness-enabled",
        businessIdUniquenessEnabled,
        Boolean.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_BUSINESS_ID_UNIQUENESS_ENABLED_PROPERTIES);
  }

  public void setBusinessIdUniquenessEnabled(final boolean businessIdUniquenessEnabled) {
    this.businessIdUniquenessEnabled = businessIdUniquenessEnabled;
  }
}
