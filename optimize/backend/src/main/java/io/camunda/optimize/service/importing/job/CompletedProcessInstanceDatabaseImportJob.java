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
import io.camunda.optimize.service.CamundaEventImportService;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.repository.ProcessInstanceRepository;
import io.camunda.optimize.service.db.writer.CompletedProcessInstanceWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import java.util.ArrayList;
import java.util.List;

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
