/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

import io.atomix.cluster.MemberId;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.RdbmsServiceFactory;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.JdbcDatabaseContainer;

public final class CamundaRdbmsTestApplication
    extends TestSpringApplication<CamundaRdbmsTestApplication> implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaRdbmsTestApplication.class);

  private GenericContainer<?> databaseContainer;
  private String isolatedH2Url;

  public CamundaRdbmsTestApplication(final Class<?>... springConfigurations) {
    super(springConfigurations);
  }

  public CamundaRdbmsTestApplication withDatabaseContainer(
      final GenericContainer<?> databaseContainer) {
    this.databaseContainer = databaseContainer;
    return this;
  }

  public CamundaRdbmsTestApplication withRdbms() {
    super.withProperty("logging.level.io.camunda.db.rdbms", "DEBUG")
        .withProperty("logging.level.org.mybatis", "DEBUG");
    setSecondaryStorageToRdbms();
    return this;
  }

  public CamundaRdbmsTestApplication withH2() {
    return configureH2("testdb");
  }

  public CamundaRdbmsTestApplication withIsolatedH2(final String databaseName) {
    final var application = configureH2(databaseName);
    isolatedH2Url = application.unifiedConfig.getData().getSecondaryStorage().getRdbms().getUrl();
    return application;
  }

  private CamundaRdbmsTestApplication configureH2(final String databaseName) {
    setSecondaryStorageToRdbms();
    final var rdbms = unifiedConfig.getData().getSecondaryStorage().getRdbms();
    rdbms.setUrl("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    rdbms.setUsername("sa");
    rdbms.setPassword("");
    return this;
  }

  @Override
  public CamundaRdbmsTestApplication start() {
    if (databaseContainer != null) {
      LOGGER.info("Start database container '{}'...", databaseContainer.getContainerInfo());
      databaseContainer.start();

      if (databaseContainer instanceof final JdbcDatabaseContainer<?> jdbcDatabaseContainer) {
        final var rdbms = unifiedConfig.getData().getSecondaryStorage().getRdbms();
        rdbms.setUrl(jdbcDatabaseContainer.getJdbcUrl());
        withAdditionalProperties(
            Map.of(
                "camunda.data.secondary-storage.rdbms.url", rdbms.getUrl(),
                "camunda.data.secondary-storage.rdbms.username", rdbms.getUsername(),
                "camunda.data.secondary-storage.rdbms.password", rdbms.getPassword(),
                "camunda.data.secondary-storage.rdbms.auto-ddl", rdbms.getAutoDdl()));
      } else if (databaseContainer
          instanceof final ReplicationClusterContainer replicationCluster) {
        final var rdbms = unifiedConfig.getData().getSecondaryStorage().getRdbms();
        rdbms.setUrl(replicationCluster.getJdbcUrl());
        rdbms.setUsername(replicationCluster.getUsername());
        rdbms.setPassword(replicationCluster.getPassword());
        withAdditionalProperties(
            Map.of(
                "camunda.data.secondary-storage.rdbms.url", rdbms.getUrl(),
                "camunda.data.secondary-storage.rdbms.username", rdbms.getUsername(),
                "camunda.data.secondary-storage.rdbms.password", rdbms.getPassword(),
                "camunda.data.secondary-storage.rdbms.auto-ddl", rdbms.getAutoDdl()));
      }
    }

    return startSpringApplication();
  }

  public CamundaRdbmsTestApplication restart() {
    super.stop();
    return startSpringApplication();
  }

  @Override
  protected SpringApplicationBuilder createSpringBuilder() {
    // because @ConditionalOnRestGatewayEnabled relies on the zeebe.broker.gateway.enable property,
    // we need to hook in at the last minute and set the property as it won't resolve from the
    // config bean
    withProperty("zeebe.broker.gateway.enable", true);
    return super.createSpringBuilder();
  }

  @Override
  public void close() {
    LOGGER.info("Resource closed - Stop spring application ...");
    try {
      super.stop();
    } finally {
      try {
        shutdownIsolatedH2();
      } finally {
        if (databaseContainer != null) {
          LOGGER.info("Stop database container '{}'...", databaseContainer.getContainerInfo());
          databaseContainer.close();
        }
      }
    }
  }

  @Override
  public CamundaRdbmsTestApplication self() {
    return this;
  }

  @Override
  public MemberId nodeId() {
    return null;
  }

  @Override
  public HealthActuator healthActuator() {
    return null;
  }

  @Override
  public boolean isGateway() {
    return false;
  }

  public RdbmsService getRdbmsService() {
    if (!isStarted()) {
      throw new IllegalStateException("Application is not started");
    }
    return super.bean(RdbmsServiceFactory.class)
        .createRdbmsService(DEFAULT_PHYSICAL_TENANT_ID, new SimpleMeterRegistry());
  }

  private CamundaRdbmsTestApplication startSpringApplication() {
    LOGGER.info("Start spring application ...");
    super.start();
    Awaitility.await("until spring context is started").until(this::isStarted);
    LOGGER.info("Spring application started");
    return this;
  }

  private void shutdownIsolatedH2() {
    if (isolatedH2Url == null) {
      return;
    }
    try (final var connection = DriverManager.getConnection(isolatedH2Url, "sa", "");
        final var statement = connection.createStatement()) {
      statement.execute("SHUTDOWN");
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to shut down isolated H2 database", e);
    }
  }

  private void setSecondaryStorageToRdbms() {
    // The unified-config type is emitted as camunda.data.secondary-storage.type when the unified
    // config is flattened at startup, which ConditionalOnSecondaryStorageType reads.
    unifiedConfig.getData().getSecondaryStorage().setType(SecondaryStorageType.rdbms);
    unifiedConfig
        .getData()
        .getSecondaryStorage()
        .getRdbms()
        .setUsername(TestSearchContainers.CAMUNDA_USER);
    unifiedConfig
        .getData()
        .getSecondaryStorage()
        .getRdbms()
        .setPassword(TestSearchContainers.CAMUNDA_PASSWORD);
    unifiedConfig.getData().getSecondaryStorage().getRdbms().getQuery().setMaxTotalHits(100);
  }
}
