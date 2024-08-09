/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.mediator;

import static io.camunda.optimize.MetricEnum.NEW_PAGE_FETCH_TIME_METRIC;

import io.camunda.optimize.OptimizeMetrics;
import io.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskRecordDto;
import io.camunda.optimize.service.importing.PositionBasedImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.MediatorRank;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeUserTaskImportService;
import io.camunda.optimize.service.importing.zeebe.db.ZeebeUserTaskFetcher;
import io.camunda.optimize.service.importing.zeebe.handler.ZeebeUserTaskImportIndexHandler;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.List;

public class ZeebeUserTaskImportMediator
    extends PositionBasedImportMediator<ZeebeUserTaskImportIndexHandler, ZeebeUserTaskRecordDto> {

  private ZeebeUserTaskFetcher zeebeUserTaskFetcher;

  public ZeebeUserTaskImportMediator(
      final ZeebeUserTaskImportIndexHandler importIndexHandler,
      final ZeebeUserTaskFetcher zeebeUserTaskFetcher,
      final ZeebeUserTaskImportService importService,
      final ConfigurationService configurationService,
      final BackoffCalculator idleBackoffCalculator) {
    this.importIndexHandler = importIndexHandler;
    this.zeebeUserTaskFetcher = zeebeUserTaskFetcher;
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
    return importNextPagePositionBased(getUserTasks(), importCompleteCallback);
  }

  @Override
  protected String getRecordType() {
    return ValueType.USER_TASK.name();
  }

  @Override
  protected Integer getPartitionId() {
    return zeebeUserTaskFetcher.getPartitionId();
  }

  private List<ZeebeUserTaskRecordDto> getUserTasks() {
    return OptimizeMetrics.getTimer(NEW_PAGE_FETCH_TIME_METRIC, getRecordType(), getPartitionId())
        .record(
            () ->
                zeebeUserTaskFetcher.getZeebeRecordsForPrefixAndPartitionFrom(
                    importIndexHandler.getNextPage()));
  }
}
