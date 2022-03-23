/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.zeebe.mediator;

import org.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import org.camunda.optimize.service.importing.PositionBasedImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.MediatorRank;
import org.camunda.optimize.service.importing.engine.service.zeebe.ZeebeVariableImportService;
import org.camunda.optimize.service.importing.zeebe.fetcher.ZeebeVariableFetcher;
import org.camunda.optimize.service.importing.zeebe.handler.ZeebeVariableImportIndexHandler;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.List;

public class ZeebeVariableImportMediator
  extends PositionBasedImportMediator<ZeebeVariableImportIndexHandler, ZeebeVariableRecordDto> {

  private final ZeebeVariableFetcher zeebeVariableFetcher;

  public ZeebeVariableImportMediator(final ZeebeVariableImportIndexHandler importIndexHandler,
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

  private List<ZeebeVariableRecordDto> getVariables() {
    return zeebeVariableFetcher.getZeebeRecordsForPrefixAndPartitionFrom(importIndexHandler.getNextPage());
  }
}
