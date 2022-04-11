/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.bpm.platform.servlet;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.persistence.StrongUuidGenerator;
import org.camunda.spin.plugin.impl.SpinProcessEnginePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.camunda.bpm.engine.ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE;
import static org.camunda.bpm.engine.ProcessEngineConfiguration.HISTORY_FULL;

@WebServlet(name = "DeployEngineServlet", urlPatterns = {"/deploy"})
public class DeployServlet extends HttpServlet {

  private static Logger logger = LoggerFactory.getLogger(DeployServlet.class);

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
      resp.setStatus(Response.Status.CONFLICT.getStatusCode());
    } else {
      final ProcessEngine processEngine = createProcessEngine(engineName);
      resp.getWriter().println(String.format("{\"name\":\"%s\"}", processEngine.getName()));
      resp.setStatus(Response.Status.OK.getStatusCode());
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
    configuration.setRestrictUserOperationLogToAuthenticatedUsers(false);
    configuration.setDefaultSerializationFormat(MediaType.APPLICATION_JSON);
    configuration.setIdGenerator(new StrongUuidGenerator());

    // For the details of what this config parameter does:
    // https://docs.camunda.org/manual/latest/reference/deployment-descriptors/tags/process-engine/#queryMaxResultsLimit
    //
    // We're adding this by default, because there are customers who restrict the result size of queries
    // against the engine. By setting it to the value of 10 000 we make sure that the limit works with
    // the Optimize default max page size.
    // Note: this can only be set from Camunda 7.12 onwards.
    try {
      Method setQueryMaxResultsLimit
        = StandaloneInMemProcessEngineConfiguration.class.getMethod("setQueryMaxResultsLimit", int.class);
      setQueryMaxResultsLimit.invoke(configuration, 10_000);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      logger.debug("You are using a version of the Camunda Platform prior to 7.12, where" +
                     "it's not possible to set the query max result limit.", e);
    }
    return configuration.buildProcessEngine();
  }
}
