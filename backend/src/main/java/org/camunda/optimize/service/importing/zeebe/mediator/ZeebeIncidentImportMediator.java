/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.zeebe.mediator;

import org.camunda.optimize.OptimizeMetrics;
import org.camunda.optimize.dto.zeebe.incident.ZeebeIncidentRecordDto;
import org.camunda.optimize.service.importing.PositionBasedImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.MediatorRank;
import org.camunda.optimize.service.importing.engine.service.zeebe.ZeebeIncidentImportService;
import org.camunda.optimize.service.importing.zeebe.fetcher.ZeebeIncidentFetcher;
import org.camunda.optimize.service.importing.zeebe.handler.ZeebeIncidentImportIndexHandler;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

import static io.camunda.zeebe.protocol.record.ValueType.INCIDENT;
import static org.camunda.optimize.MetricEnum.NEW_PAGE_FETCH_TIME_METRIC;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ZeebeIncidentImportMediator
  extends PositionBasedImportMediator<ZeebeIncidentImportIndexHandler, ZeebeIncidentRecordDto> {

  private final ZeebeIncidentFetcher zeebeIncidentFetcher;

  public ZeebeIncidentImportMediator(final ZeebeIncidentImportIndexHandler importIndexHandler,
                                     final ZeebeIncidentFetcher zeebeIncidentFetcher,
                                     final ZeebeIncidentImportService importService,
                                     final ConfigurationService configurationService,
                                     final BackoffCalculator idleBackoffCalculator) {
    this.importIndexHandler = importIndexHandler;
    this.zeebeIncidentFetcher = zeebeIncidentFetcher;
    this.importService = importService;
    this.configurationService = configurationService;
    this.idleBackoffCalculator = idleBackoffCalculator;
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.INSTANCE_SUB_ENTITIES;
  }

  @Override
  protected boolean importNextPage(final Runnable importCompleteCallback) {
    return importNextPagePositionBased(getIncidents(), importCompleteCallback);
  }

  @Override
  protected String getRecordType() {
    return INCIDENT.name();
  }

  @Override
  protected Integer getPartitionId() {
    return zeebeIncidentFetcher.getPartitionId();
  }

  private List<ZeebeIncidentRecordDto> getIncidents() {
    return OptimizeMetrics.getTimer(NEW_PAGE_FETCH_TIME_METRIC, getRecordType(), getPartitionId())
      .record(() -> zeebeIncidentFetcher.getZeebeRecordsForPrefixAndPartitionFrom(importIndexHandler.getNextPage()));
  }

}
