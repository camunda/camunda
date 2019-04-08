/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.fetcher;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.client.Client;

public abstract class EngineEntityFetcher {

  public static final String UTF8 = "UTF-8";

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected EngineContext engineContext;

  @Autowired
  protected ConfigurationService configurationService;

  public EngineEntityFetcher(EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  public Client getEngineClient() {
    return engineContext.getEngineClient();
  }

  public String getEngineAlias() {
    return engineContext.getEngineAlias();
  }

  public void setConfigurationService(final ConfigurationService configurationService) {
    this.configurationService = configurationService;
  }
}
