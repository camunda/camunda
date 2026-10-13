/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.processing.engine;

import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_ENABLE_PROCESS_DEFINITION_RESTORE;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_ENABLE_RPA_REEXPORT_MIGRATION;

import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.Set;

/**
 * Defines configurations applied by the engine on startup. The prefix for this class is
 * camunda.processing.engine.startup.
 */
public class EngineStartup {
  private static final String PREFIX = "camunda.processing.engine.startup";

  private static final Set<String> LEGACY_RPA_REEXPORT_MIGRATION_ENABLED_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.startup.rpaReexportMigrationEnabled");

  /**
   * Configures whether the one-time migration that re-exports existing RPA resources runs on broker
   * startup.
   */
  private boolean rpaReexportMigrationEnabled = DEFAULT_ENABLE_RPA_REEXPORT_MIGRATION;

  /**
   * Configures whether the one-time cleanup that restores stray resting PENDING_DELETION process
   * definitions back to ACTIVE runs on broker startup.
   */
  private boolean processDefinitionRestoreEnabled = DEFAULT_ENABLE_PROCESS_DEFINITION_RESTORE;

  public boolean isRpaReexportMigrationEnabled() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".rpa-reexport-migration-enabled",
        rpaReexportMigrationEnabled,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_RPA_REEXPORT_MIGRATION_ENABLED_PROPERTIES);
  }

  public void setRpaReexportMigrationEnabled(final boolean rpaReexportMigrationEnabled) {
    this.rpaReexportMigrationEnabled = rpaReexportMigrationEnabled;
  }

  public boolean isProcessDefinitionRestoreEnabled() {
    return processDefinitionRestoreEnabled;
  }

  public void setProcessDefinitionRestoreEnabled(final boolean processDefinitionRestoreEnabled) {
    this.processDefinitionRestoreEnabled = processDefinitionRestoreEnabled;
  }
}
