/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.query.process.FlatFlowNodeInstanceDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.FlowNodeInstanceWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;

public class FlatFlowNodeInstanceDatabaseImportJob
    extends DatabaseImportJob<FlatFlowNodeInstanceDto> {

  private final FlowNodeInstanceWriter flowNodeInstanceWriter;
  private final ConfigurationService configurationService;

  public FlatFlowNodeInstanceDatabaseImportJob(
      final FlowNodeInstanceWriter flowNodeInstanceWriter,
      final ConfigurationService configurationService,
      final Runnable importCompleteCallback,
      final DatabaseClient databaseClient) {
    super(importCompleteCallback, databaseClient);
    this.flowNodeInstanceWriter = flowNodeInstanceWriter;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(final List<FlatFlowNodeInstanceDto> flowNodeInstances) {
    final List<ImportRequestDto> importRequests =
        flowNodeInstanceWriter.generateFlatFlowNodeInstanceImports(flowNodeInstances);
    databaseClient.executeImportRequestsAsBulk(
        "flat flow node instances",
        importRequests,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
