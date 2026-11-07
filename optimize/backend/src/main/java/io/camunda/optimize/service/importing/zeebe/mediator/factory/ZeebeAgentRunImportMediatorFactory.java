/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.mediator.factory;

import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_INCIDENT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.reader.ZeebeRecordRefreshIntervalReader;
import io.camunda.optimize.service.db.writer.AgentRunWriter;
import io.camunda.optimize.service.importing.ImportIndexHandlerRegistry;
import io.camunda.optimize.service.importing.ImportMediator;
import io.camunda.optimize.service.importing.engine.service.zeebe.AgentRunEnrichmentService;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeAgentRunImportService;
import io.camunda.optimize.service.importing.zeebe.db.ZeebeAgentInstanceFetcher;
import io.camunda.optimize.service.importing.zeebe.mediator.ZeebeAgentRunImportMediator;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

@Component
public class ZeebeAgentRunImportMediatorFactory extends AbstractZeebeImportMediatorFactory {

  private final AgentRunWriter agentRunWriter;
  private final AgentRunEnrichmentService enrichmentService;
  private final ZeebeRecordRefreshIntervalReader refreshIntervalReader;

  public ZeebeAgentRunImportMediatorFactory(
      final BeanFactory beanFactory,
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final ConfigurationService configurationService,
      final AgentRunWriter agentRunWriter,
      final AgentRunEnrichmentService enrichmentService,
      final ZeebeRecordRefreshIntervalReader refreshIntervalReader,
      final ObjectMapper objectMapper,
      final DatabaseClient databaseClient) {
    super(
        beanFactory,
        importIndexHandlerRegistry,
        configurationService,
        objectMapper,
        databaseClient);
    this.agentRunWriter = agentRunWriter;
    this.enrichmentService = enrichmentService;
    this.refreshIntervalReader = refreshIntervalReader;
  }

  @Override
  public List<ImportMediator> createMediators(final ZeebeDataSourceDto zeebeDataSourceDto) {
    final int partitionId = zeebeDataSourceDto.getPartitionId();
    final String prefix = configurationService.getConfiguredZeebe().getName() + "-";
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
                enrichmentService,
                zeebeDataSourceDto.getPartitionId(),
                databaseClient),
            configurationService,
            new BackoffCalculator(configurationService),
            // Arrays.asList keeps a handler the registry doesn't have as null, which the gate
            // skips.
            () ->
                Arrays.asList(
                    importIndexHandlerRegistry.getZeebeProcessInstanceImportIndexHandler(
                        partitionId),
                    importIndexHandlerRegistry.getZeebeIncidentImportIndexHandler(partitionId)),
            Suppliers.memoize(
                () ->
                    Stream.of(ZEEBE_PROCESS_INSTANCE_INDEX_NAME, ZEEBE_INCIDENT_INDEX_NAME)
                        .map(index -> refreshIntervalReader.getRefreshInterval(prefix + index))
                        .max(Duration::compareTo)
                        .orElseThrow())));
  }
}
