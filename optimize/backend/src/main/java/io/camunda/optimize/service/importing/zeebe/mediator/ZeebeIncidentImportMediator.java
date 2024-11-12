/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.mediator;

import static io.camunda.optimize.MetricEnum.NEW_PAGE_FETCH_TIME_METRIC;
import static io.camunda.zeebe.protocol.record.ValueType.INCIDENT;

import io.camunda.optimize.OptimizeMetrics;
import io.camunda.optimize.dto.zeebe.incident.ZeebeIncidentRecordDto;
import io.camunda.optimize.service.importing.PositionBasedImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.MediatorRank;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeIncidentImportService;
import io.camunda.optimize.service.importing.zeebe.db.ZeebeIncidentFetcher;
import io.camunda.optimize.service.importing.zeebe.handler.ZeebeIncidentImportIndexHandler;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ZeebeIncidentImportMediator
    extends PositionBasedImportMediator<ZeebeIncidentImportIndexHandler, ZeebeIncidentRecordDto> {

  private final ZeebeIncidentFetcher zeebeIncidentFetcher;

  public ZeebeIncidentImportMediator(
      final ZeebeIncidentImportIndexHandler importIndexHandler,
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
        .record(
            () ->
                zeebeIncidentFetcher.getZeebeRecordsForPrefixAndPartitionFrom(
                    importIndexHandler.getNextPage()));
  }
}
