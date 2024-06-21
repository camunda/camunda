/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.bpm.platform.database;

import java.sql.SQLException;
import org.camunda.bpm.application.PostDeploy;
import org.camunda.bpm.application.PreUndeploy;
import org.camunda.bpm.application.ProcessApplication;
import org.camunda.bpm.application.impl.ServletProcessApplication;
import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates a tcp server that enables the connection to the h2 in memory database from other hosts.
 */
@ProcessApplication
public class DatabaseConnectionProcessApplication extends ServletProcessApplication {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DatabaseConnectionProcessApplication.class);

  @PostDeploy
  public void postDeploy() {
    shutdownExistingServer();
    try {
      Server.createTcpServer("-tcpPort", "9092", "-tcpAllowOthers").start();
    } catch (final SQLException e) {
      LOGGER.error("Was not able to start tcp server!", e);
    }
  }

  @PreUndeploy
  public void preUndeploy() {
    shutdownExistingServer();
  }

  private void shutdownExistingServer() {
    try {
      Server.shutdownTcpServer("tcp://localhost:9092", "", true, true);
    } catch (final SQLException e) {
      LOGGER.debug("There was no server to shutdown", e);
    }
  }
}
