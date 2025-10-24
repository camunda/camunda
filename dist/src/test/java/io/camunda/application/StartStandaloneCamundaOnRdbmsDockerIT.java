/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@EnabledIfSystemProperty(named = "camunda.docker.test.enabled", matches = "true")
public class StartStandaloneCamundaOnRdbmsDockerIT extends AbstractCamundaDockerIT {

  @Test
  public void testStartStandaloneCamundaOnPostgres() {
    createContainer(this::createPostgresContainer).start();

    final GenericContainer<?> camundaContainer =
        createContainer(() -> createUnauthenticatedUnifiedConfigCamundaContainerWithPostgres());

    startContainer(camundaContainer);
  }

  @Test
  public void testStartStandaloneCamundaOnOracleWithoutMountedDriver() {
    createContainer(this::createOracleContainer).start();

    final GenericContainer<?> camundaContainer =
        createContainer(() -> createUnauthenticatedUnifiedConfigCamundaContainerWithOracle())
            .waitingFor(
                Wait.forLogMessage(
                    ".*Failed to load driver class oracle.jdbc.OracleDriver.*\\s", 1));

    startContainer(camundaContainer);
  }

  @Test
  public void testStartStandaloneCamundaOnOracleWithMountedDriver() throws ClassNotFoundException {
    createContainer(this::createOracleContainer).start();

    final Class<?> driverClass = Class.forName("oracle.jdbc.OracleDriver");
    final String jarPath =
        driverClass.getProtectionDomain().getCodeSource().getLocation().getPath();

    final GenericContainer<?> camundaContainer =
        createContainer(() -> createUnauthenticatedUnifiedConfigCamundaContainerWithOracle())
            .withFileSystemBind(jarPath, "/driver-lib/ojdbc.jar", BindMode.READ_ONLY);

    startContainer(camundaContainer);
  }
}
