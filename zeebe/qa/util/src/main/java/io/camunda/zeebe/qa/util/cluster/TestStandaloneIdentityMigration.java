/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import io.atomix.cluster.MemberId;
import io.camunda.application.Profile;
import io.camunda.application.StandaloneIdentityMigration;
import io.camunda.application.commons.migration.BlockingMigrationsRunner;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates an instance of the {@link BlockingMigrationsRunner} for identity Spring application.
 */
public final class TestStandaloneIdentityMigration
    extends TestSpringApplication<TestStandaloneIdentityMigration> {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(TestStandaloneIdentityMigration.class);

  private final IdentityMigrationProperties config;

  public TestStandaloneIdentityMigration(final IdentityMigrationProperties migrationProperties) {
    super(
        StandaloneIdentityMigration.class,
        BlockingMigrationsRunner.class,
        UnifiedConfigurationHelper.class,
        UnifiedConfiguration.class);
    config = migrationProperties;

    migrationProperties.getCluster().setPort(SocketUtil.getNextAddress().getPort());

    LOGGER.info("-> Cluster Port: {}", mappedPort(TestZeebePort.CLUSTER));

    //noinspection resource
    withBean("migrationConfig", migrationProperties, IdentityMigrationProperties.class)
        .withAdditionalProfile(Profile.IDENTITY_MIGRATION);

    withProperty("camunda.migration.identity.mode", migrationProperties.getMode());
    withProperty(
        "camunda.migration.identity.managementIdentity.base-url",
        migrationProperties.getManagementIdentity().getBaseUrl());
    withProperty(
        "camunda.migration.identity.managementIdentity.issuer-backend-url",
        migrationProperties.getManagementIdentity().getIssuerBackendUrl());
    withProperty(
        "camunda.migration.identity.managementIdentity.client-id",
        migrationProperties.getManagementIdentity().getClientId());
    withProperty(
        "camunda.migration.identity.managementIdentity.client-secret",
        migrationProperties.getManagementIdentity().getClientSecret());
    withProperty(
        "camunda.migration.identity.managementIdentity.audience",
        migrationProperties.getManagementIdentity().getAudience());
  }

  @Override
  public TestStandaloneIdentityMigration self() {
    return this;
  }

  @Override
  public MemberId nodeId() {
    return MemberId.from(config.getCluster().getMemberId());
  }

  @Override
  public String host() {
    return config.getCluster().getHost();
  }

  @Override
  public HealthActuator healthActuator() {
    return new HealthActuator.NoopHealthActuator();
  }

  @Override
  public boolean isGateway() {
    return false;
  }

  public TestStandaloneIdentityMigration withAppConfig(
      final Consumer<IdentityMigrationProperties> modifier) {
    modifier.accept(config);
    return this;
  }

  @Override
  public int mappedPort(final TestZeebePort port) {
    return switch (port) {
      case CLUSTER -> config.getCluster().getPort();
      default -> super.mappedPort(port);
    };
  }

  public int getExitCode() {
    return springContext.getBean(StandaloneIdentityMigration.class).getExitCode();
  }
}
