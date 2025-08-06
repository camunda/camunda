/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.List;
import java.util.Set;

public class Restore {

  private static final String PREFIX = "camunda.system.restore.";

  private static final Set<String> LEGACY_RESTORE_VALIDATE_CFG_PROPERTIES =
      Set.of("zeebe.restore.validateConfig");
  private static final Set<String> LEGACY_RESTORE_IGNORE_FILES_TARGET_PROPERTIES =
      Set.of("zeebe.restore.ignoreFilesInTarget");

  private boolean validateConfig = true;
  private List<String> ignoreFilesInTarget = List.of("lost+found");

  public boolean isValidateConfig() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "validate-config",
        validateConfig,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_RESTORE_VALIDATE_CFG_PROPERTIES);
  }

  public void setValidateConfig(final boolean validateConfig) {
    this.validateConfig = validateConfig;
  }

  public List<String> getIgnoreFilesInTarget() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + "ignore-files-in-target",
        ignoreFilesInTarget,
        List.class, // FIXME: assumes always list of strings
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_RESTORE_IGNORE_FILES_TARGET_PROPERTIES);
  }

  public void setIgnoreFilesInTarget(final List<String> ignoreFilesInTarget) {
    this.ignoreFilesInTarget = ignoreFilesInTarget;
  }
}
