/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.bpm.platform.database;

import org.camunda.bpm.application.PostDeploy;
import org.camunda.bpm.application.PreUndeploy;
import org.camunda.bpm.application.ProcessApplication;
import org.camunda.bpm.application.impl.ServletProcessApplication;
import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * Creates a tcp server that enables the connection to the h2 in memory database from other hosts.
 */
@ProcessApplication
public class DatabaseConnectionProcessApplication extends ServletProcessApplication {

  private static Logger logger = LoggerFactory.getLogger(DatabaseConnectionProcessApplication.class);

  @PostDeploy
  public void postDeploy() {
    shutdownExistingServer();
    try {
      Server.createTcpServer("-tcpPort", "9092", "-tcpAllowOthers").start();
    } catch (SQLException e) {
      logger.error("Was not able to start tcp server!" , e);
    }

  }

  @PreUndeploy
  public void preUndeploy() {
    shutdownExistingServer();
  }

  private void shutdownExistingServer() {
    try {
      Server.shutdownTcpServer("tcp://localhost:9092", "", true, true);
    } catch (SQLException e) {
      logger.debug("There was no server to shutdown", e);
    }
  }


}
