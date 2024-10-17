/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.util;

import io.atomix.cluster.MemberId;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.zeebe.qa.util.actuator.HealthActuator;
import io.camunda.zeebe.qa.util.cluster.TestSpringApplication;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.JdbcDatabaseContainer;

public final class CamundaRdbmsTestApplication
    extends TestSpringApplication<CamundaRdbmsTestApplication>
    implements ExtensionContext.Store.CloseableResource {

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

  @Override
  public CamundaRdbmsTestApplication start() {
    LOGGER.info("Start database container '{}'...", databaseContainer.getContainerInfo());
    databaseContainer.start();

    if (databaseContainer instanceof final JdbcDatabaseContainer<?> jdbcDatabaseContainer) {
      super.withProperty("camunda.database.type", "rdbms")
          .withProperty("spring.datasource.url", jdbcDatabaseContainer.getJdbcUrl())
          .withProperty("spring.datasource.username", jdbcDatabaseContainer.getUsername())
          .withProperty("spring.datasource.password", jdbcDatabaseContainer.getPassword())
          .withProperty("spring.datasource.password", jdbcDatabaseContainer.getPassword());
    }

    LOGGER.info("Start spring application ...");
    return super.start();
  }

  @Override
  public void close() {
    LOGGER.info("Stop spring application ...");
    super.stop();
    LOGGER.info("Stop database container '{}'...", databaseContainer.getContainerInfo());
    databaseContainer.close();
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
    return super.bean(RdbmsService.class);
  }
}
