/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.mediator.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;

public abstract class AbstractZeebeImportMediatorFactory {

  protected final BeanFactory beanFactory;
  protected ImportIndexHandlerRegistry importIndexHandlerRegistry;
  protected final ConfigurationService configurationService;
  protected final ObjectMapper objectMapper;
  protected final DatabaseClient databaseClient;

  public AbstractZeebeImportMediatorFactory(
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper,
      final DatabaseClient databaseClient) {
    this.beanFactory = beanFactory;
    this.importIndexHandlerRegistry = importIndexHandlerRegistry;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
    this.databaseClient = databaseClient;
  }

  public abstract List<ImportMediator> createMediators(ZeebeDataSourceDto dataSourceDto);
}
