/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.zeebe.mediator;

import org.camunda.optimize.OptimizeMetrics;
import org.camunda.optimize.dto.zeebe.definition.ZeebeProcessDefinitionRecordDto;
import org.camunda.optimize.service.importing.PositionBasedImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.MediatorRank;
import org.camunda.optimize.service.importing.engine.service.zeebe.ZeebeProcessDefinitionImportService;
import org.camunda.optimize.service.importing.zeebe.fetcher.ZeebeProcessDefinitionFetcher;
import org.camunda.optimize.service.importing.zeebe.handler.ZeebeProcessDefinitionImportIndexHandler;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

import static io.camunda.zeebe.protocol.record.ValueType.PROCESS;
import static org.camunda.optimize.MetricEnum.NEW_PAGE_FETCH_TIME_METRIC;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ZeebeProcessDefinitionImportMediator
  extends PositionBasedImportMediator<ZeebeProcessDefinitionImportIndexHandler, ZeebeProcessDefinitionRecordDto> {

  private final ZeebeProcessDefinitionFetcher zeebeProcessDefinitionFetcher;

  public ZeebeProcessDefinitionImportMediator(final ZeebeProcessDefinitionImportIndexHandler importIndexHandler,
                                              final ZeebeProcessDefinitionFetcher zeebeProcessDefinitionFetcher,
                                              final ZeebeProcessDefinitionImportService importService,
                                              final ConfigurationService configurationService,
                                              final BackoffCalculator idleBackoffCalculator) {
    this.importIndexHandler = importIndexHandler;
    this.zeebeProcessDefinitionFetcher = zeebeProcessDefinitionFetcher;
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
    return importNextPagePositionBased(getDefinitions(), importCompleteCallback);
  }

  @Override
  protected String getRecordType() {
    return PROCESS.name();
  }

  @Override
  protected Integer getPartitionId() {
    return zeebeProcessDefinitionFetcher.getPartitionId();
  }

  private List<ZeebeProcessDefinitionRecordDto> getDefinitions() {
    return OptimizeMetrics.getTimer(NEW_PAGE_FETCH_TIME_METRIC, getRecordType(), getPartitionId())
      .record(() -> zeebeProcessDefinitionFetcher.getZeebeRecordsForPrefixAndPartitionFrom(importIndexHandler.getNextPage()));
  }

}
