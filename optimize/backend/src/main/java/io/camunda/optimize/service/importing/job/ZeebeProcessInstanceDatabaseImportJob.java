/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.ZeebeProcessInstanceWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;

public class ZeebeProcessInstanceDatabaseImportJob extends DatabaseImportJob<ProcessInstanceDto> {

  private final ZeebeProcessInstanceWriter zeebeProcessInstanceWriter;
  private final ConfigurationService configurationService;
  private final String sourceExportIndex;

  public ZeebeProcessInstanceDatabaseImportJob(
      final ZeebeProcessInstanceWriter zeebeProcessInstanceWriter,
      final ConfigurationService configurationService,
      final Runnable importCompleteCallback,
      final String sourceExportIndex,
      final DatabaseClient databaseClient) {
    super(importCompleteCallback, databaseClient);
    this.zeebeProcessInstanceWriter = zeebeProcessInstanceWriter;
    this.configurationService = configurationService;
    this.sourceExportIndex = sourceExportIndex;
  }

  @Override
  protected void persistEntities(final List<ProcessInstanceDto> processInstances) {
    final List<ImportRequestDto> importRequests =
        zeebeProcessInstanceWriter.generateProcessInstanceImports(
            processInstances, sourceExportIndex);
    databaseClient.executeImportRequestsAsBulk(
        "Zeebe process instances",
        importRequests,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }
}
