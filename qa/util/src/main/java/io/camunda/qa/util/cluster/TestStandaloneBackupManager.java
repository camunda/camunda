/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.cluster;

import io.atomix.cluster.MemberId;
import io.camunda.application.StandaloneBackupManager;
import io.camunda.application.StandaloneBackupManager.BackupManagerConfiguration;
import io.camunda.application.commons.configuration.UnifiedConfigurationModule;
import io.camunda.application.commons.search.NativeSearchClientsConfiguration;
import io.camunda.application.commons.search.PhysicalTenantSearchClientReadersConfiguration;
import io.camunda.application.commons.search.SearchClientReaderConfiguration;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.qa.util.actuator.HealthActuator.NoopHealthActuator;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

public class TestStandaloneBackupManager
    extends TestSpringApplication<TestStandaloneBackupManager> {

  private Long backupId;
  private boolean skipSchemaCheck;

  public TestStandaloneBackupManager() {
    super(
        UnifiedConfigurationModule.class,
        BackupManagerConfiguration.class,
        StandaloneBackupManager.class,
        NativeSearchClientsConfiguration.class,
        PhysicalTenantSearchClientReadersConfiguration.class,
        SearchClientReaderConfiguration.class);
  }

  @Override
  public TestStandaloneBackupManager self() {
    return this;
  }

  @Override
  public MemberId nodeId() {
    return MemberId.from("backup");
  }

  @Override
  public HealthActuator healthActuator() {
    return new NoopHealthActuator();
  }

  @Override
  public boolean isGateway() {
    return false;
  }

  @Override
  protected String[] commandLineArgs() {
    if (backupId == null) {
      return super.commandLineArgs();
    }

    return skipSchemaCheck
        ? new String[] {String.valueOf(backupId), "--skip-schema-check"}
        : new String[] {String.valueOf(backupId)};
  }

  @Override
  protected SpringApplicationBuilder createSpringBuilder() {
    return super.createSpringBuilder().web(WebApplicationType.NONE);
  }

  public TestStandaloneBackupManager withBackupId(final Long backupId) {
    this.backupId = backupId;
    return this;
  }

  public TestStandaloneBackupManager withSkipSchemaCheck(final boolean skipSchemaCheck) {
    this.skipSchemaCheck = skipSchemaCheck;
    return this;
  }

  /**
   * Convenience method for setting the secondary storage type in the unified configuration.
   *
   * @param type the secondary storage type
   * @return itself for chaining
   */
  public TestStandaloneBackupManager withSecondaryStorageType(final SecondaryStorageType type) {
    unifiedConfig.getData().getSecondaryStorage().setType(type);
    return this;
  }
}
