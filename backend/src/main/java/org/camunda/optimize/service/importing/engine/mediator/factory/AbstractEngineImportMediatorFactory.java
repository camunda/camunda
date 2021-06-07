/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.mediator.factory;

import lombok.AllArgsConstructor;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;

import java.util.List;

@AllArgsConstructor
public abstract class AbstractEngineImportMediatorFactory {
  protected final BeanFactory beanFactory;
  protected final ImportIndexHandlerRegistry importIndexHandlerRegistry;
  protected final ConfigurationService configurationService;

  public abstract List<ImportMediator> createMediators(EngineContext engineContext);
}
