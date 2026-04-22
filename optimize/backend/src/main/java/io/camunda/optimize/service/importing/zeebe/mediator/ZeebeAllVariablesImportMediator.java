/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.mediator;

import static io.camunda.optimize.MetricEnum.NEW_PAGE_FETCH_TIME_METRIC;
import static io.camunda.zeebe.protocol.record.ValueType.VARIABLE;

import io.camunda.optimize.OptimizeMetrics;
import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import io.camunda.optimize.service.importing.PositionBasedImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.MediatorRank;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeAllVariablesImportService;
import io.camunda.optimize.service.importing.zeebe.db.ZeebeAllVariablesFetcher;
import io.camunda.optimize.service.importing.zeebe.handler.ZeebeAllVariablesImportIndexHandler;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;

public class ZeebeAllVariablesImportMediator
    extends PositionBasedImportMediator<
        ZeebeAllVariablesImportIndexHandler, ZeebeVariableRecordDto> {

  private final ZeebeAllVariablesFetcher fetcher;

  public ZeebeAllVariablesImportMediator(
      final ZeebeAllVariablesImportIndexHandler importIndexHandler,
      final ZeebeAllVariablesFetcher fetcher,
      final ZeebeAllVariablesImportService importService,
      final ConfigurationService configurationService,
      final BackoffCalculator idleBackoffCalculator) {
    this.importIndexHandler = importIndexHandler;
    this.fetcher = fetcher;
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
    return importNextPagePositionBased(fetchRecords(), importCompleteCallback);
  }

  @Override
  protected String getRecordType() {
    return VARIABLE.name();
  }

  @Override
  protected Integer getPartitionId() {
    return fetcher.getPartitionId();
  }

  private List<ZeebeVariableRecordDto> fetchRecords() {
    return OptimizeMetrics.getTimer(NEW_PAGE_FETCH_TIME_METRIC, getRecordType(), getPartitionId())
        .record(
            () ->
                fetcher.getZeebeRecordsForPrefixAndPartitionFrom(importIndexHandler.getNextPage()));
  }
}
