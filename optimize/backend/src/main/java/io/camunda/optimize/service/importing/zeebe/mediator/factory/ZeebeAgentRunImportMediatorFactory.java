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
import io.camunda.optimize.service.db.writer.AgentDefinitionWriter;
import io.camunda.optimize.service.db.writer.AgentRunWriter;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeAgentRunImportService;
import io.camunda.optimize.service.importing.zeebe.db.ZeebeAgentInstanceFetcher;
import io.camunda.optimize.service.importing.zeebe.mediator.ZeebeAgentRunImportMediator;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class ZeebeAgentRunImportMediatorFactory extends AbstractZeebeImportMediatorFactory {

  private final AgentRunWriter agentRunWriter;
  private final AgentDefinitionWriter agentDefinitionWriter;

  public ZeebeAgentRunImportMediatorFactory(
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService,
      final AgentRunWriter agentRunWriter,
      final AgentDefinitionWriter agentDefinitionWriter,
      final ObjectMapper objectMapper,
      final DatabaseClient databaseClient) {
    super(
        beanFactory,
        importIndexHandlerRegistry,
        configurationService,
        objectMapper,
        databaseClient);
    this.agentRunWriter = agentRunWriter;
    this.agentDefinitionWriter = agentDefinitionWriter;
  }

  @Override
  public List<ImportMediator> createMediators(final ZeebeDataSourceDto zeebeDataSourceDto) {
    return Collections.singletonList(
        new ZeebeAgentRunImportMediator(
            importIndexHandlerRegistry.getZeebeAgentRunImportIndexHandler(
                zeebeDataSourceDto.getPartitionId()),
            beanFactory.getBean(
                ZeebeAgentInstanceFetcher.class,
                zeebeDataSourceDto.getPartitionId(),
                databaseClient,
                objectMapper,
                configurationService),
            new ZeebeAgentRunImportService(
                configurationService,
                agentRunWriter,
                agentDefinitionWriter,
                zeebeDataSourceDto.getPartitionId(),
                databaseClient),
            configurationService,
            new BackoffCalculator(configurationService)));
  }
}
