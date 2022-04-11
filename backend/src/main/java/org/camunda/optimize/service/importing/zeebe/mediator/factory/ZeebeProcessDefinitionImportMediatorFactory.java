/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.zeebe.mediator.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.writer.ProcessDefinitionWriter;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.service.zeebe.ZeebeProcessDefinitionImportService;
import org.camunda.optimize.service.importing.zeebe.fetcher.ZeebeProcessDefinitionFetcher;
import org.camunda.optimize.service.importing.zeebe.mediator.ZeebeProcessDefinitionImportMediator;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ZeebeProcessDefinitionImportMediatorFactory extends AbstractZeebeImportMediatorFactory {

  private final ProcessDefinitionWriter processDefinitionWriter;

  public ZeebeProcessDefinitionImportMediatorFactory(final BeanFactory beanFactory,
                                                     final ImportIndexHandlerRegistry importIndexHandlerRegistry,
                                                     final ConfigurationService configurationService,
                                                     final ProcessDefinitionWriter processDefinitionWriter,
                                                     final ObjectMapper objectMapper,
                                                     final OptimizeElasticsearchClient esClient) {
    super(beanFactory, importIndexHandlerRegistry, configurationService, objectMapper, esClient);
    this.processDefinitionWriter = processDefinitionWriter;
  }

  @Override
  public List<ImportMediator> createMediators(final ZeebeDataSourceDto zeebeDataSourceDto) {
    return Collections.singletonList(
      new ZeebeProcessDefinitionImportMediator(
        importIndexHandlerRegistry.getZeebeProcessDefinitionImportIndexHandler(zeebeDataSourceDto.getPartitionId()),
        beanFactory.getBean(
          ZeebeProcessDefinitionFetcher.class,
          zeebeDataSourceDto.getPartitionId(),
          esClient,
          objectMapper,
          configurationService
        ),
        new ZeebeProcessDefinitionImportService(
          configurationService,
          processDefinitionWriter,
          zeebeDataSourceDto.getPartitionId()
        ),
        configurationService,
        new BackoffCalculator(configurationService)
      )
    );
  }
}
