/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.mediator;

import static io.camunda.optimize.MetricEnum.NEW_PAGE_FETCH_TIME_METRIC;
import static io.camunda.zeebe.protocol.record.ValueType.DECISION_EVALUATION;

import io.camunda.optimize.OptimizeMetrics;
import io.camunda.optimize.dto.zeebe.decision.ZeebeDecisionInstanceRecordDto;
import io.camunda.optimize.service.importing.PositionBasedImportMediator;
import io.camunda.optimize.service.importing.engine.mediator.MediatorRank;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeDecisionInstanceImportService;
import io.camunda.optimize.service.importing.zeebe.db.ZeebeDecisionInstanceFetcher;
import io.camunda.optimize.service.importing.zeebe.handler.ZeebeDecisionInstanceImportIndexHandler;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ZeebeDecisionInstanceImportMediator
    extends PositionBasedImportMediator<
        ZeebeDecisionInstanceImportIndexHandler, ZeebeDecisionInstanceRecordDto> {

  private final ZeebeDecisionInstanceFetcher zeebeDecisionInstanceFetcher;

  public ZeebeDecisionInstanceImportMediator(
      final ZeebeDecisionInstanceImportIndexHandler importIndexHandler,
      final ZeebeDecisionInstanceFetcher zeebeDecisionInstanceFetcher,
      final ZeebeDecisionInstanceImportService importService,
      final ConfigurationService configurationService,
      final BackoffCalculator idleBackoffCalculator) {
    this.importIndexHandler = importIndexHandler;
    this.zeebeDecisionInstanceFetcher = zeebeDecisionInstanceFetcher;
    this.importService = importService;
    this.configurationService = configurationService;
    this.idleBackoffCalculator = idleBackoffCalculator;
  }

  @Override
  public MediatorRank getRank() {
    return MediatorRank.INSTANCE;
  }

  @Override
  protected boolean importNextPage(final Runnable importCompleteCallback) {
    return importNextPagePositionBased(getInstances(), importCompleteCallback);
  }

  @Override
  protected String getRecordType() {
    return DECISION_EVALUATION.name();
  }

  @Override
  protected Integer getPartitionId() {
    return zeebeDecisionInstanceFetcher.getPartitionId();
  }

  private List<ZeebeDecisionInstanceRecordDto> getInstances() {
    return OptimizeMetrics.getTimer(NEW_PAGE_FETCH_TIME_METRIC, getRecordType(), getPartitionId())
        .record(
            () ->
                zeebeDecisionInstanceFetcher.getZeebeRecordsForPrefixAndPartitionFrom(
                    importIndexHandler.getNextPage()));
  }
}
