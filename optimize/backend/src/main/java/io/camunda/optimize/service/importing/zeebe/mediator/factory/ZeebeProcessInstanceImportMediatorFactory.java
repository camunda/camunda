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
import io.camunda.optimize.service.db.writer.FlowNodeInstanceWriter;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeProcessInstanceImportService;
import io.camunda.optimize.service.importing.zeebe.db.ZeebeProcessInstanceFetcher;
import io.camunda.optimize.service.importing.zeebe.mediator.ZeebeProcessInstanceImportMediator;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class ZeebeProcessInstanceImportMediatorFactory extends AbstractZeebeImportMediatorFactory {

  private final ProcessInstanceWriter zeebeProcessInstanceWriter;
  private final FlowNodeInstanceWriter flowNodeInstanceWriter;

  public ZeebeProcessInstanceImportMediatorFactory(
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService,
      final ProcessInstanceWriter zeebeProcessInstanceWriter,
      final FlowNodeInstanceWriter flowNodeInstanceWriter,
      final ObjectMapper objectMapper,
      final DatabaseClient databaseClient) {
    super(
        beanFactory,
        importIndexHandlerRegistry,
        configurationService,
        objectMapper,
        databaseClient);
    this.zeebeProcessInstanceWriter = zeebeProcessInstanceWriter;
    this.flowNodeInstanceWriter = flowNodeInstanceWriter;
  }

  @Override
  public List<ImportMediator> createMediators(final ZeebeDataSourceDto zeebeDataSourceDto) {
    final int partitionId = zeebeDataSourceDto.getPartitionId();
    return Collections.singletonList(
        new ZeebeProcessInstanceImportMediator(
            importIndexHandlerRegistry.getZeebeProcessInstanceImportIndexHandler(partitionId),
            beanFactory.getBean(
                ZeebeProcessInstanceFetcher.class,
                partitionId,
                databaseClient,
                objectMapper,
                configurationService),
            new ZeebeProcessInstanceImportService(
                configurationService,
                zeebeProcessInstanceWriter,
                flowNodeInstanceWriter,
                partitionId,
                databaseClient),
            configurationService,
            new BackoffCalculator(configurationService),
            null));
  }
}
