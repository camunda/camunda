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
import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorageType;
import io.camunda.container.ExtendedConfigurationBuilder;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.qa.util.actuator.HealthActuator.NoopHealthActuator;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import java.util.function.Consumer;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

public class TestStandaloneBackupManager
    extends TestSpringApplication<TestStandaloneBackupManager> {

  private Long backupId;
  private final Camunda unifiedConfig;

  public TestStandaloneBackupManager() {
    super(
        UnifiedConfigurationModule.class,
        BackupManagerConfiguration.class,
        StandaloneBackupManager.class,
        NativeSearchClientsConfiguration.class,
        PhysicalTenantSearchClientReadersConfiguration.class,
        SearchClientReaderConfiguration.class);

    unifiedConfig = new Camunda();
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

  /**
   * Modifies the unified configuration (camunda.* properties).
   *
   * @param modifier a configuration function that accepts the Camunda configuration object
   * @return itself for chaining
   */
  @Override
  public TestStandaloneBackupManager withUnifiedConfig(final Consumer<Camunda> modifier) {
    modifier.accept(unifiedConfig);
    return this;
  }

  @Override
  protected String[] commandLineArgs() {
    return backupId == null ? super.commandLineArgs() : new String[] {String.valueOf(backupId)};
  }

  @Override
  protected SpringApplicationBuilder createSpringBuilder() {
    // Flatten the in-memory unified config into camunda.* properties at the latest possible point.
    // Refreshable so that fields cleared between stop/start don't remain.
    withRefreshableProperties(ExtendedConfigurationBuilder.flatPropertiesFor(unifiedConfig));
    return super.createSpringBuilder().web(WebApplicationType.NONE);
  }

  public TestStandaloneBackupManager withBackupId(final Long backupId) {
    this.backupId = backupId;
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
