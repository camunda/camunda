/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.ingested.mediator.factory;

import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;

public abstract class AbstractIngestedImportMediatorFactory {

  protected final BeanFactory beanFactory;
  protected final ImportIndexHandlerRegistry importIndexHandlerRegistry;
  protected final ConfigurationService configurationService;

  public AbstractIngestedImportMediatorFactory(
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService) {
    this.beanFactory = beanFactory;
    this.importIndexHandlerRegistry = importIndexHandlerRegistry;
    this.configurationService = configurationService;
  }

  public abstract List<ImportMediator> createMediators();
}
