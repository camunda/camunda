package org.camunda.bpm.platform.servlet.purge;

import org.camunda.bpm.application.PostDeploy;
import org.camunda.bpm.application.ProcessApplication;
import org.camunda.bpm.application.impl.ServletProcessApplication;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.ManagementServiceImpl;

import javax.servlet.ServletContext;

/**
 * Simple process application to purge engine database in a clean manner.
 *
 * Intended to be deployed along side with shared engine and used by unit tests to
 * properly reset state of database to clean.
 *
 */
@ProcessApplication
public class PurgeDatabaseProcessApplication extends ServletProcessApplication {

  /**
   * Publish managment service to servlet context.
   */
  @PostDeploy
  public void purgeDatabase(ProcessEngine processEngine) {
    ManagementServiceImpl managementService = (ManagementServiceImpl) processEngine.getManagementService();

    ServletContext ctx = getServletContext();
    ctx.setAttribute("managementService", managementService);
  }

}
