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
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.writer.VariableWriter;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.service.ObjectVariableService;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeVariableImportService;
import io.camunda.optimize.service.importing.zeebe.db.ZeebeVariableFetcher;
import io.camunda.optimize.service.importing.zeebe.mediator.ZeebeVariableImportMediator;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class ZeebeVariableImportMediatorFactory extends AbstractZeebeImportMediatorFactory {

  private final VariableWriter variableWriter;
  private final ProcessDefinitionReader processDefinitionReader;
  private final ObjectVariableService objectVariableService;

  public ZeebeVariableImportMediatorFactory(
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService,
      final VariableWriter variableWriter,
      final ObjectMapper objectMapper,
      final DatabaseClient databaseClient,
      final ProcessDefinitionReader processDefinitionReader,
      final ObjectVariableService objectVariableService) {
    super(
        beanFactory,
        importIndexHandlerRegistry,
        configurationService,
        objectMapper,
        databaseClient);
    this.variableWriter = variableWriter;
    this.processDefinitionReader = processDefinitionReader;
    this.objectVariableService = objectVariableService;
  }

  @Override
  public List<ImportMediator> createMediators(final ZeebeDataSourceDto zeebeDataSourceDto) {
    final int partitionId = zeebeDataSourceDto.getPartitionId();
    return Collections.singletonList(
        new ZeebeVariableImportMediator(
            importIndexHandlerRegistry.getZeebeVariableImportIndexHandler(partitionId),
            beanFactory.getBean(
                ZeebeVariableFetcher.class,
                partitionId,
                databaseClient,
                objectMapper,
                configurationService),
            new ZeebeVariableImportService(
                configurationService,
                variableWriter,
                new ObjectMapper(),
                processDefinitionReader,
                objectVariableService,
                databaseClient),
            configurationService,
            new BackoffCalculator(configurationService),
            null));
  }
}
