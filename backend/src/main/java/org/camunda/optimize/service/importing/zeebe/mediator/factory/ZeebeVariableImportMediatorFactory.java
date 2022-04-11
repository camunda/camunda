/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.zeebe.mediator.factory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.writer.ZeebeProcessInstanceWriter;
import org.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.ImportMediator;
import org.camunda.optimize.service.importing.engine.service.ObjectVariableService;
import org.camunda.optimize.service.importing.engine.service.zeebe.ZeebeVariableImportService;
import org.camunda.optimize.service.importing.zeebe.fetcher.ZeebeVariableFetcher;
import org.camunda.optimize.service.importing.zeebe.mediator.ZeebeVariableImportMediator;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ZeebeVariableImportMediatorFactory extends AbstractZeebeImportMediatorFactory {

  private final ZeebeProcessInstanceWriter zeebeProcessInstanceWriter;
  private final ProcessDefinitionReader processDefinitionReader;
  private final ObjectVariableService objectVariableService;

  public ZeebeVariableImportMediatorFactory(final BeanFactory beanFactory,
                                            final ImportIndexHandlerRegistry importIndexHandlerRegistry,
                                            final ConfigurationService configurationService,
                                            final ZeebeProcessInstanceWriter zeebeProcessInstanceWriter,
                                            final ObjectMapper objectMapper,
                                            final OptimizeElasticsearchClient esClient,
                                            final ProcessDefinitionReader processDefinitionReader,
                                            final ObjectVariableService objectVariableService) {
    super(beanFactory, importIndexHandlerRegistry, configurationService, objectMapper, esClient);
    this.zeebeProcessInstanceWriter = zeebeProcessInstanceWriter;
    this.processDefinitionReader = processDefinitionReader;
    this.objectVariableService = objectVariableService;
  }

  @Override
  public List<ImportMediator> createMediators(final ZeebeDataSourceDto zeebeDataSourceDto) {
    return Collections.singletonList(
      new ZeebeVariableImportMediator(
        importIndexHandlerRegistry.getZeebeVariableImportIndexHandler(zeebeDataSourceDto.getPartitionId()),
        beanFactory.getBean(
          ZeebeVariableFetcher.class,
          zeebeDataSourceDto.getPartitionId(),
          esClient,
          objectMapper,
          configurationService
        ),
        new ZeebeVariableImportService(
          configurationService,
          zeebeProcessInstanceWriter,
          zeebeDataSourceDto.getPartitionId(),
          new ObjectMapper(),
          processDefinitionReader,
          objectVariableService
        ),
        configurationService,
        new BackoffCalculator(configurationService)
      )
    );
  }

}
