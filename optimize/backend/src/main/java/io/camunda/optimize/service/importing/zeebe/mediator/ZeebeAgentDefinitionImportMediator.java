/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.mediator;

import static io.camunda.optimize.MetricEnum.NEW_PAGE_FETCH_TIME_METRIC;
import static io.camunda.zeebe.protocol.record.ValueType.AGENT_DEFINITION;

import io.camunda.optimize.OptimizeMetrics;
import io.camunda.optimize.dto.zeebe.agentdefinition.ZeebeAgentDefinitionRecordDto;
import io.camunda.optimize.service.importing.PositionBasedImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.MediatorRank;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeAgentDefinitionImportService;
import io.camunda.optimize.service.importing.zeebe.db.ZeebeAgentDefinitionFetcher;
import io.camunda.optimize.service.importing.zeebe.handler.ZeebeAgentDefinitionImportIndexHandler;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ZeebeAgentDefinitionImportMediator
    extends PositionBasedImportMediator<
        ZeebeAgentDefinitionImportIndexHandler, ZeebeAgentDefinitionRecordDto> {

  private final ZeebeAgentDefinitionFetcher zeebeAgentDefinitionFetcher;

  public ZeebeAgentDefinitionImportMediator(
      final ZeebeAgentDefinitionImportIndexHandler importIndexHandler,
      final ZeebeAgentDefinitionFetcher zeebeAgentDefinitionFetcher,
      final ZeebeAgentDefinitionImportService importService,
      final ConfigurationService configurationService,
      final BackoffCalculator idleBackoffCalculator) {
    this.importIndexHandler = importIndexHandler;
    this.zeebeAgentDefinitionFetcher = zeebeAgentDefinitionFetcher;
    this.importService = importService;
    this.configurationService = configurationService;
    this.idleBackoffCalculator = idleBackoffCalculator;
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.DEFINITION;
  }

  @Override
  protected boolean importNextPage(final Runnable importCompleteCallback) {
    return importNextPagePositionBased(getAgentDefinitions(), importCompleteCallback);
  }

  @Override
  protected String getRecordType() {
    return AGENT_DEFINITION.name();
  }

  @Override
  protected Integer getPartitionId() {
    return zeebeAgentDefinitionFetcher.getPartitionId();
  }

  private List<ZeebeAgentDefinitionRecordDto> getAgentDefinitions() {
    return OptimizeMetrics.getTimer(NEW_PAGE_FETCH_TIME_METRIC, getRecordType(), getPartitionId())
        .record(
            () ->
                zeebeAgentDefinitionFetcher.getZeebeRecordsForPrefixAndPartitionFrom(
                    importIndexHandler.getNextPage()));
  }
}
