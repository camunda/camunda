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
import io.camunda.optimize.service.db.writer.DecisionDefinitionWriter;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeDecisionDefinitionImportService;
import io.camunda.optimize.service.importing.zeebe.db.ZeebeDecisionDefinitionFetcher;
import io.camunda.optimize.service.importing.zeebe.mediator.ZeebeDecisionDefinitionImportMediator;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class ZeebeDecisionDefinitionImportMediatorFactory
    extends AbstractZeebeImportMediatorFactory {

  private final DecisionDefinitionWriter decisionDefinitionWriter;

  public ZeebeDecisionDefinitionImportMediatorFactory(
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService,
      final DecisionDefinitionWriter decisionDefinitionWriter,
      final ObjectMapper objectMapper,
      final DatabaseClient databaseClient) {
    super(
        beanFactory,
        importIndexHandlerRegistry,
        configurationService,
        objectMapper,
        databaseClient);
    this.decisionDefinitionWriter = decisionDefinitionWriter;
  }

  @Override
  public List<ImportMediator> createMediators(final ZeebeDataSourceDto zeebeDataSourceDto) {
    return Collections.singletonList(
        new ZeebeDecisionDefinitionImportMediator(
            importIndexHandlerRegistry.getZeebeDecisionDefinitionImportIndexHandler(
                zeebeDataSourceDto.getPartitionId()),
            beanFactory.getBean(
                ZeebeDecisionDefinitionFetcher.class,
                zeebeDataSourceDto.getPartitionId(),
                databaseClient,
                objectMapper,
                configurationService),
            new ZeebeDecisionDefinitionImportService(
                configurationService,
                decisionDefinitionWriter,
                zeebeDataSourceDto.getPartitionId(),
                databaseClient),
            configurationService,
            new BackoffCalculator(configurationService)));
  }
}
