package org.camunda.bpm.platform.servlet.purge;

import org.camunda.bpm.application.PostDeploy;
import org.camunda.bpm.application.ProcessApplication;
import org.camunda.bpm.application.impl.ServletProcessApplication;
import org.camunda.bpm.engine.ProcessEngine;

import javax.servlet.ServletContext;

/**
 * Simple process application to deploy and manipulate lifecycle of various
 * process definitions that are required during integration testing process.
 */
@ProcessApplication
public class DeployProcessApplication extends ServletProcessApplication {

  /**
   * Publish processEngine service to servlet context.
   */
  @PostDeploy
  public void exposeEngineToServletContext(ProcessEngine processEngine) {

    ServletContext ctx = getServletContext();
    ctx.setAttribute("processEngine", processEngine);
    ctx.setAttribute("processApplication", this);
  }

}
