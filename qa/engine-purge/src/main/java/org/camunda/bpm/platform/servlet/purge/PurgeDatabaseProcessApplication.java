/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.bpm.platform.servlet.purge;

import org.camunda.bpm.BpmPlatform;
import org.camunda.bpm.application.PostDeploy;
import org.camunda.bpm.application.ProcessApplication;
import org.camunda.bpm.application.impl.ServletProcessApplication;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.ManagementServiceImpl;

import javax.servlet.ServletContext;
import java.util.ArrayList;
import java.util.List;

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
   * Publish management service to servlet context.
   */
  @PostDeploy
  public void purgeDatabase(ProcessEngine ignoredProcessEngine) {

    List<ManagementServiceImpl> managementServices = new ArrayList<>();
    for (ProcessEngine processEngine : BpmPlatform.getProcessEngineService().getProcessEngines()) {
      managementServices.add((ManagementServiceImpl) processEngine.getManagementService());
    }
    ServletContext ctx = getServletContext();
    ctx.setAttribute("managementServices", managementServices);
  }

}
