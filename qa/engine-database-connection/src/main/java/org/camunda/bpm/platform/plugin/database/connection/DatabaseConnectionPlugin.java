package org.camunda.bpm.platform.plugin.database.connection;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * Creates a tcp server that enables the connection to the
 * h2 in memory database.
 */
public class DatabaseConnectionPlugin extends AbstractProcessEnginePlugin {

  private Logger logger = LoggerFactory.getLogger(getClass());

  public void postProcessEngineBuild(ProcessEngine processEngine) {
    try {
      Server.shutdownTcpServer("tcp://localhost:9092", "", true, true);
    } catch (SQLException e) {
      logger.debug("There was no server to shutdown", e);
    }
    try {
      Server.createTcpServer("-tcpPort", "9092", "-tcpAllowOthers").start();
    } catch (SQLException e) {
      logger.error("Was not able to start tcp server!" , e);
    }

  }


}
