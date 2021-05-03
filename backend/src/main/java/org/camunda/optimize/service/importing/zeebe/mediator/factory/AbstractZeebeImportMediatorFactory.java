/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.zeebe.mediator.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.DataImportSourceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;

import java.util.List;

@AllArgsConstructor
public abstract class AbstractZeebeImportMediatorFactory {
  protected final BeanFactory beanFactory;

  protected final ConfigurationService configurationService;
  protected final ObjectMapper objectMapper;
  protected final OptimizeElasticsearchClient esClient;

  public abstract List<ImportMediator> createMediators(DataImportSourceDto dataImportSourceDto);
}
