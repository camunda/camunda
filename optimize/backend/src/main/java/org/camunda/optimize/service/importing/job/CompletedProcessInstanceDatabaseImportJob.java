/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.job;

import java.util.ArrayList;
import java.util.List;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.CamundaEventImportService;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.repository.ProcessInstanceRepository;
import org.camunda.optimize.service.db.writer.CompletedProcessInstanceWriter;
import org.camunda.optimize.service.importing.DatabaseImportJob;

public class CompletedProcessInstanceDatabaseImportJob
    extends DatabaseImportJob<ProcessInstanceDto> {

  private final CompletedProcessInstanceWriter completedProcessInstanceWriter;
  private final CamundaEventImportService camundaEventService;
  private final ProcessInstanceRepository processInstanceRepository;

  public CompletedProcessInstanceDatabaseImportJob(
      final CompletedProcessInstanceWriter completedProcessInstanceWriter,
      final CamundaEventImportService camundaEventService,
      final Runnable importCompleteCallback,
      final DatabaseClient databaseClient,
      final ProcessInstanceRepository processInstanceRepository) {
    super(importCompleteCallback, databaseClient);
    this.completedProcessInstanceWriter = completedProcessInstanceWriter;
    this.camundaEventService = camundaEventService;
    this.processInstanceRepository = processInstanceRepository;
  }

  @Override
  protected void persistEntities(List<ProcessInstanceDto> completedProcessInstances) {
    List<ImportRequestDto> imports = new ArrayList<>();
    imports.addAll(
        completedProcessInstanceWriter.generateProcessInstanceImports(completedProcessInstances));
    imports.addAll(
        camundaEventService.generateCompletedProcessInstanceImports(completedProcessInstances));
    processInstanceRepository.bulkImport("Completed process instances", imports);
  }
}
