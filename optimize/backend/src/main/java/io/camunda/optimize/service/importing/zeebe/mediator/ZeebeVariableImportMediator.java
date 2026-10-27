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
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeVariableImportService;
import io.camunda.optimize.service.importing.zeebe.db.ZeebeVariableFetcher;
import io.camunda.optimize.service.importing.zeebe.handler.ZeebeVariableImportIndexHandler;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;

public class ZeebeVariableImportMediator
    extends PositionBasedImportMediator<ZeebeVariableImportIndexHandler, ZeebeVariableRecordDto> {

  private final ZeebeVariableFetcher zeebeVariableFetcher;

  public ZeebeVariableImportMediator(
      final ZeebeVariableImportIndexHandler importIndexHandler,
      final ZeebeVariableFetcher zeebeVariableFetcher,
      final ZeebeVariableImportService importService,
      final ConfigurationService configurationService,
      final BackoffCalculator idleBackoffCalculator) {
    this.importIndexHandler = importIndexHandler;
    this.zeebeVariableFetcher = zeebeVariableFetcher;
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
    return importNextPagePositionBased(getVariables(), importCompleteCallback);
  }

  @Override
  protected String getRecordType() {
    return VARIABLE.name();
  }

  @Override
  protected Integer getPartitionId() {
    return zeebeVariableFetcher.getPartitionId();
  }

  private List<ZeebeVariableRecordDto> getVariables() {
    return OptimizeMetrics.getTimer(NEW_PAGE_FETCH_TIME_METRIC, getRecordType(), getPartitionId())
        .record(
            () ->
                zeebeVariableFetcher.getZeebeRecordsForPrefixAndPartitionFrom(
                    importIndexHandler.getNextPage()));
  }
}
