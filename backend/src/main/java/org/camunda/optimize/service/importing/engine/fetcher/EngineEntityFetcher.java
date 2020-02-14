/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.fetcher;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

import javax.ws.rs.client.Client;

@RequiredArgsConstructor
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class EngineEntityFetcher {
  public static final String UTF8 = "UTF-8";

  protected Logger logger = LoggerFactory.getLogger(getClass());

  protected final EngineContext engineContext;

  @Autowired
  @Getter
  @Setter
  protected ConfigurationService configurationService;

  public Client getEngineClient() {
    return engineContext.getEngineClient();
  }

  public String getEngineAlias() {
    return engineContext.getEngineAlias();
  }

}
