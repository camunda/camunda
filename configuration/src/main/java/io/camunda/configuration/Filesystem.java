/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.LinkedHashSet;
import java.util.Set;

public class Filesystem {
  private static final String PREFIX = "camunda.data.primary-storage.backup.filesystem";
  private static final Set<Set<String>> LEGACY_BASE_PATH_PROPERTIES = new LinkedHashSet<>(2);

  static {
    LEGACY_BASE_PATH_PROPERTIES.add(Set.of("zeebe.broker.data.backup.filesystem.basePath"));
    LEGACY_BASE_PATH_PROPERTIES.add(Set.of("camunda.data.backup.filesystem.base-path"));
  }

  /** Set the base path to store all related backup files in. */
  private String basePath;

  public String getBasePath() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
        PREFIX + ".base-path",
        basePath,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_BASE_PATH_PROPERTIES);
  }

  public void setBasePath(final String basePath) {
    this.basePath = basePath;
  }
}
