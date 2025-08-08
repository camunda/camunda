/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import io.camunda.zeebe.broker.system.configuration.ExperimentalCfg;
import java.util.Set;

public class Upgrade {
  private static final String PREFIX = "camunda.system.upgrade";

  private static final Set<String> LEGACY_ENABLE_VERSION_CHECK_PROPERTIES =
      Set.of("zeebe.broker.experimental.versionCheckRestrictionEnabled");

  /**
   * Toggles the version check restriction, used for migration. Useful for testing migration logic
   * on snapshot or alpha versions. Default: True, means it is not allowed to migrate to
   * incompatible version like: SNAPSHOT or alpha.
   */
  private boolean enableVersionCheck = ExperimentalCfg.DEFAULT_VERSION_CHECK_ENABLED;

  public boolean getEnableVersionCheck() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".enable-version-check",
        enableVersionCheck,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ENABLE_VERSION_CHECK_PROPERTIES);
  }

  public void setEnableVersionCheck(final boolean enableVersionCheck) {
    this.enableVersionCheck = enableVersionCheck;
  }
}
