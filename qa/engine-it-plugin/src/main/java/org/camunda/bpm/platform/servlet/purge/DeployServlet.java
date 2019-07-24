/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.bpm.platform.servlet.purge;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.camunda.spin.plugin.impl.SpinProcessEnginePlugin;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.camunda.bpm.engine.ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE;
import static org.camunda.bpm.engine.ProcessEngineConfiguration.HISTORY_FULL;

@WebServlet(name = "DeployEngineServlet", urlPatterns = {"/deploy"})
public class DeployServlet extends HttpServlet {

  @Override
  protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
    final String engineName = req.getParameter("name");
    final boolean engineWithNameExistsAlready = ProcessEngines.getProcessEngines()
      .values()
      .stream()
      .map(ProcessEngine::getName)
      .anyMatch(engineName::equals);

    if (engineWithNameExistsAlready) {
      resp.getWriter().println(String.format("{\"error\":\"Engine with name %s already exists.\"}", engineName));
      resp.setStatus(HttpServletResponse.SC_CONFLICT);
    } else {
      final ProcessEngine processEngine = createProcessEngine(engineName);
      resp.getWriter().println(String.format("{\"name\":\"%s\"}", processEngine.getName()));
      resp.setStatus(HttpServletResponse.SC_OK);
    }

    resp.setContentType("application/json");
    resp.getWriter().flush();

  }

  private ProcessEngine createProcessEngine(final String name) {
    final StandaloneInMemProcessEngineConfiguration configuration = new StandaloneInMemProcessEngineConfiguration();
    configuration.setProcessEngineName(name);
    configuration.setJdbcUrl("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
    configuration.setHistory(HISTORY_FULL);
    configuration.setDatabaseSchemaUpdate(DB_SCHEMA_UPDATE_TRUE);
    configuration.setAuthorizationEnabled(true);
    configuration.setJobExecutorDeploymentAware(true);
    configuration.getProcessEnginePlugins().add(new SpinProcessEnginePlugin());
    return configuration.buildProcessEngine();
  }
}
