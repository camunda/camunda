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
import io.camunda.optimize.service.db.writer.FlowNodeInstanceWriter;
import io.camunda.optimize.service.db.writer.IncidentWriter;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import io.camunda.optimize.service.db.writer.UserTaskWriter;
import io.camunda.optimize.service.db.writer.VariableWriter;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.service.ObjectVariableService;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeIncidentImportService;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeProcessInstanceImportService;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeUserTaskImportService;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeVariableImportService;
import io.camunda.optimize.service.importing.zeebe.db.ZeebeRecordFetcher;
import io.camunda.optimize.service.importing.zeebe.mediator.ZeebeCombinedImportMediator;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

/**
 * Factory that creates a {@link ZeebeCombinedImportMediator} for each Zeebe partition. The
 * combined mediator replaces the four separate per-type mediator factories
 * (ZeebeProcessInstanceImportMediatorFactory, ZeebeVariableImportMediatorFactory,
 * ZeebeIncidentImportMediatorFactory, ZeebeUserTaskImportMediatorFactory) and wires them all into a
 * single fetch-and-dispatch pipeline.
 */
@Component
public class ZeebeCombinedImportMediatorFactory extends AbstractZeebeImportMediatorFactory {

  private final ProcessInstanceWriter processInstanceWriter;
  private final FlowNodeInstanceWriter flowNodeInstanceWriter;
  private final VariableWriter variableWriter;
  private final ProcessDefinitionReader processDefinitionReader;
  private final ObjectVariableService objectVariableService;
  private final IncidentWriter incidentWriter;
  private final UserTaskWriter userTaskWriter;

  public ZeebeCombinedImportMediatorFactory(
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper,
      final DatabaseClient databaseClient,
      final ProcessInstanceWriter processInstanceWriter,
      final FlowNodeInstanceWriter flowNodeInstanceWriter,
      final VariableWriter variableWriter,
      final ProcessDefinitionReader processDefinitionReader,
      final ObjectVariableService objectVariableService,
      final IncidentWriter incidentWriter,
      final UserTaskWriter userTaskWriter) {
    super(
        beanFactory,
        importIndexHandlerRegistry,
        configurationService,
        objectMapper,
        databaseClient);
    this.processInstanceWriter = processInstanceWriter;
    this.flowNodeInstanceWriter = flowNodeInstanceWriter;
    this.variableWriter = variableWriter;
    this.processDefinitionReader = processDefinitionReader;
    this.objectVariableService = objectVariableService;
    this.incidentWriter = incidentWriter;
    this.userTaskWriter = userTaskWriter;
  }

  @Override
  public List<ImportMediator> createMediators(final ZeebeDataSourceDto zeebeDataSourceDto) {
    final int partitionId = zeebeDataSourceDto.getPartitionId();

    final ZeebeRecordFetcher combinedFetcher =
        beanFactory.getBean(
            ZeebeRecordFetcher.class,
            partitionId,
            databaseClient,
            objectMapper,
            configurationService);

    return Collections.singletonList(
        new ZeebeCombinedImportMediator(
            importIndexHandlerRegistry.getZeebeRecordImportIndexHandler(partitionId),
            combinedFetcher,
            new ZeebeProcessInstanceImportService(
                configurationService,
                processInstanceWriter,
                flowNodeInstanceWriter,
                partitionId,
                databaseClient),
            new ZeebeVariableImportService(
                configurationService,
                variableWriter,
                objectMapper,
                processDefinitionReader,
                objectVariableService,
                databaseClient),
            new ZeebeIncidentImportService(
                configurationService, incidentWriter, databaseClient),
            new ZeebeUserTaskImportService(
                configurationService, userTaskWriter, partitionId, databaseClient),
            objectMapper,
            configurationService,
            new BackoffCalculator(configurationService)));
  }
}
