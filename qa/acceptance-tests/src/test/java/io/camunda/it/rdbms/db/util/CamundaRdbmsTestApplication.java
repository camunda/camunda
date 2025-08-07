/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

import static io.camunda.spring.utils.DatabaseTypeUtils.PROPERTY_CAMUNDA_DATABASE_TYPE;

import io.atomix.cluster.MemberId;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
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

  public CamundaRdbmsTestApplication(final Class<?>... springConfigurations) {
    super(springConfigurations);
  }

  public CamundaRdbmsTestApplication withDatabaseContainer(
      final GenericContainer<?> databaseContainer) {
    this.databaseContainer = databaseContainer;
    return this;
  }

  public CamundaRdbmsTestApplication withRdbms() {
    super.withProperty(PROPERTY_CAMUNDA_DATABASE_TYPE, "rdbms")
        .withProperty("logging.level.io.camunda.db.rdbms", "DEBUG")
        .withProperty("logging.level.org.mybatis", "DEBUG");
    return this;
  }

  public CamundaRdbmsTestApplication withH2() {
    super.withProperty(
            "camunda.database.url", "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
        .withProperty("camunda.database.username", "sa")
        .withProperty("camunda.database.password", "");
    return this;
  }

  @Override
  public CamundaRdbmsTestApplication start() {
    if (databaseContainer != null) {
      LOGGER.info("Start database container '{}'...", databaseContainer.getContainerInfo());
      databaseContainer.start();

      if (databaseContainer instanceof final JdbcDatabaseContainer<?> jdbcDatabaseContainer) {
        super.withProperty("camunda.database.url", jdbcDatabaseContainer.getJdbcUrl())
            .withProperty("camunda.database.username", jdbcDatabaseContainer.getUsername())
            .withProperty("camunda.database.password", jdbcDatabaseContainer.getPassword());
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

  public RdbmsService getRdbmsService() {
    if (!isStarted()) {
      throw new IllegalStateException("Application is not started");
    }
    return super.bean(RdbmsService.class);
  }
}
