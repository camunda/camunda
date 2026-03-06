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
import io.camunda.optimize.service.db.writer.UserTaskWriter;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeUserTaskImportService;
import io.camunda.optimize.service.importing.zeebe.cache.ZeebeImportSlidingWindowCache;
import io.camunda.optimize.service.importing.zeebe.db.ZeebeUserTaskFetcher;
import io.camunda.optimize.service.importing.zeebe.mediator.ZeebeUserTaskImportMediator;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class ZeebeUserTaskImportMediatorFactory extends AbstractZeebeImportMediatorFactory {

  private final UserTaskWriter userTaskWriter;

  public ZeebeUserTaskImportMediatorFactory(
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService,
      final UserTaskWriter userTaskWriter,
      final ObjectMapper objectMapper,
      final DatabaseClient databaseClient) {
    super(
        beanFactory,
        importIndexHandlerRegistry,
        configurationService,
        objectMapper,
        databaseClient);
    this.userTaskWriter = userTaskWriter;
  }

  @Override
  public List<ImportMediator> createMediators(final ZeebeDataSourceDto dataSourceDto) {
    final int partitionId = dataSourceDto.getPartitionId();
    return Collections.singletonList(
        new ZeebeUserTaskImportMediator(
            importIndexHandlerRegistry.getZeebeUserTaskImportIndexHandler(partitionId),
            beanFactory.getBean(
                ZeebeUserTaskFetcher.class,
                partitionId,
                databaseClient,
                objectMapper,
                configurationService),
            new ZeebeUserTaskImportService(
                configurationService, userTaskWriter, partitionId, databaseClient),
            configurationService,
            new BackoffCalculator(configurationService),
            new ZeebeImportSlidingWindowCache(partitionId, "USER_TASK")));
  }
}
