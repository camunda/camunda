/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_ENABLE_PROCESS_DEFINITION_RESTORE;
import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_ENABLE_RPA_REEXPORT_MIGRATION;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;

public class StartupCfg implements ConfigurationEntry {

  private boolean rpaReexportMigrationEnabled = DEFAULT_ENABLE_RPA_REEXPORT_MIGRATION;
  private boolean processDefinitionRestoreEnabled = DEFAULT_ENABLE_PROCESS_DEFINITION_RESTORE;

  public boolean isRpaReexportMigrationEnabled() {
    return rpaReexportMigrationEnabled;
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

  @Override
  public String toString() {
    return "StartupCfg{"
        + "rpaReexportMigrationEnabled="
        + rpaReexportMigrationEnabled
        + ", processDefinitionRestoreEnabled="
        + processDefinitionRestoreEnabled
        + '}';
  }
}
