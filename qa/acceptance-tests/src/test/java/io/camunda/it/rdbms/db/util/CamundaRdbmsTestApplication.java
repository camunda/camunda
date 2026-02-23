/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

import static io.camunda.spring.utils.DatabaseTypeUtils.UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE;

import io.atomix.cluster.MemberId;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.util.Map;
import java.util.function.Consumer;
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

  private final Camunda unifiedConfig;

  public CamundaRdbmsTestApplication(final Class<?>... springConfigurations) {
    super(springConfigurations);

    unifiedConfig = new Camunda();
    //noinspection resource
    withBean("camunda", unifiedConfig, Camunda.class);
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
    setSecondaryStorageToRdbms();
    final var rdbms = unifiedConfig.getData().getSecondaryStorage().getRdbms();
    rdbms.setUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
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
        // In order to ensure that a test runs against the intended database, we also need to set
        // Spring’s datasource properties. Otherwise, Spring might default to an embedded database
        // (H2). See also property substitution in dist/application.properties for further details.
        withAdditionalProperties(
            Map.of(
                "spring.datasource.url", rdbms.getUrl(),
                "spring.datasource.username", rdbms.getUsername(),
                "spring.datasource.password", rdbms.getPassword(),
                "camunda.data.secondary-storage.rdbms.auto-ddl", rdbms.getAutoDdl()));
      }
    }

    LOGGER.info("Start spring application ...");
    super.start();
    Awaitility.await("until spring context is started").until(this::isStarted);
    LOGGER.info("Spring application started");
    return this;
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
    super.stop();
    if (databaseContainer != null) {
      LOGGER.info("Stop database container '{}'...", databaseContainer.getContainerInfo());
      databaseContainer.close();
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

  /**
   * Modifies the unified configuration (camunda.* properties).
   *
   * @param modifier a configuration function that accepts the Camunda configuration object
   * @return itself for chaining
   */
  @Override
  public CamundaRdbmsTestApplication withUnifiedConfig(final Consumer<Camunda> modifier) {
    modifier.accept(unifiedConfig);
    return this;
  }

  public RdbmsService getRdbmsService() {
    if (!isStarted()) {
      throw new IllegalStateException("Application is not started");
    }
    return super.bean(RdbmsService.class);
  }

  private void setSecondaryStorageToRdbms() {
    // set environment variable camunda.data.secondary-storage.type to ensure that
    // ConditionalOnSecondaryStorageType behaves as expected
    super.withProperty(UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE, "rdbms");
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
