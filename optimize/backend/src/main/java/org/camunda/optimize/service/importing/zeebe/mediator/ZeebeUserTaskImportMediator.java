/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.zeebe.mediator;

import static org.camunda.optimize.MetricEnum.NEW_PAGE_FETCH_TIME_METRIC;

import io.camunda.zeebe.protocol.record.ValueType;
import java.util.List;
import org.camunda.optimize.OptimizeMetrics;
import org.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskRecordDto;
import org.camunda.optimize.service.importing.PositionBasedImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.MediatorRank;
import org.camunda.optimize.service.importing.engine.service.zeebe.ZeebeUserTaskImportService;
import org.camunda.optimize.service.importing.zeebe.db.ZeebeUserTaskFetcher;
import org.camunda.optimize.service.importing.zeebe.handler.ZeebeUserTaskImportIndexHandler;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

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
