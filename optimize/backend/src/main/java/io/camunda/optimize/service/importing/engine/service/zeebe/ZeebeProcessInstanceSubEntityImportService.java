/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.job.ProcessInstanceDatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public abstract class ZeebeProcessInstanceSubEntityImportService<T> implements ImportService<T> {

  protected final DatabaseImportJobExecutor databaseImportJobExecutor;
  protected final ConfigurationService configurationService;
  protected final ProcessDefinitionReader processDefinitionReader;
  protected final int partitionId;
  private final ProcessInstanceWriter processInstanceWriter;
  private final DatabaseClient databaseClient;
  private final String sourceExportIndex;

  protected ZeebeProcessInstanceSubEntityImportService(
      final ConfigurationService configurationService,
      final ProcessInstanceWriter processInstanceWriter,
      final int partitionId,
      final ProcessDefinitionReader processDefinitionReader,
      final DatabaseClient databaseClient,
      final String sourceExportIndex) {
    databaseImportJobExecutor =
        new DatabaseImportJobExecutor(getClass().getSimpleName(), configurationService);
    this.processInstanceWriter = processInstanceWriter;
    this.partitionId = partitionId;
    this.configurationService = configurationService;
    this.processDefinitionReader = processDefinitionReader;
    this.databaseClient = databaseClient;
    this.sourceExportIndex = sourceExportIndex;
  }

  abstract List<ProcessInstanceDto> filterAndMapZeebeRecordsToOptimizeEntities(List<T> records);

  @Override
  public void executeImport(final List<T> zeebeRecords, final Runnable importCompleteCallback) {
    final boolean newDataIsAvailable = !zeebeRecords.isEmpty();
    if (newDataIsAvailable) {
      final List<ProcessInstanceDto> newOptimizeEntities =
          filterAndMapZeebeRecordsToOptimizeEntities(zeebeRecords);
      final DatabaseImportJob<ProcessInstanceDto> databaseImportJob =
          createDatabaseImportJob(newOptimizeEntities, importCompleteCallback);
      addDatabaseImportJobToQueue(databaseImportJob);
    }
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

  /**
   * Creates a skeleton {@link ProcessInstanceDto} from Zeebe identifiers, translating them into
   * Optimize's C7-based field naming convention.
   *
   * <p><b>Terminology note:</b> Optimize uses C7 naming throughout its data model and ES/OS
   * indices, which is the <em>inverse</em> of C8 conventions.
   *
   * <p>This mapping is intentional. Do not rename the DTO fields. See {@code
   * optimize/docs/adr/001-c7-naming-conventions.md} for the full rationale.
   *
   * @param processDefinitionKey the Zeebe {@code bpmnProcessId} — the non-unique BPMN process ID
   *     string (e.g. {@code "invoice-process"})
   * @param processInstanceId the Zeebe {@code processInstanceKey} — the unique {@code Long} key for
   *     this process instance
   * @param processDefinitionId the Zeebe {@code processDefinitionKey} — the unique {@code Long} key
   *     for the process definition version
   * @param tenantId the tenant identifier
   */
  protected ProcessInstanceDto createSkeletonProcessInstance(
      final String processDefinitionKey,
      final Long processInstanceId,
      final Long processDefinitionId,
      final String tenantId) {
    final ProcessInstanceDto processInstanceDto = new ProcessInstanceDto();
    processInstanceDto.setProcessDefinitionKey(processDefinitionKey);
    processInstanceDto.setProcessInstanceId(String.valueOf(processInstanceId));
    processInstanceDto.setProcessDefinitionId(String.valueOf(processDefinitionId));
    processInstanceDto.setTenantId(tenantId);
    processInstanceDto.setDataSource(
        new ZeebeDataSourceDto(configurationService.getConfiguredZeebe().getName(), partitionId));
    return processInstanceDto;
  }

  private void addDatabaseImportJobToQueue(
      final DatabaseImportJob<ProcessInstanceDto> databaseImportJob) {
    databaseImportJobExecutor.executeImportJob(databaseImportJob);
  }

  protected final DatabaseImportJob<ProcessInstanceDto> createDatabaseImportJob(
      final List<ProcessInstanceDto> processInstanceDtos, final Runnable importCompleteCallback) {
    final ProcessInstanceDatabaseImportJob job =
        newImportJob(
            importCompleteCallback, processInstanceWriter, sourceExportIndex, databaseClient);
    job.setEntitiesToImport(processInstanceDtos);
    return job;
  }

  /**
   * Hook for subclasses to substitute a custom {@link ProcessInstanceDatabaseImportJob}. The parent
   * passes its own collaborators in as arguments so subclasses do not need direct access to the
   * private fields.
   */
  protected ProcessInstanceDatabaseImportJob newImportJob(
      final Runnable importCompleteCallback,
      final ProcessInstanceWriter processInstanceWriter,
      final String sourceExportIndex,
      final DatabaseClient databaseClient) {
    return new ProcessInstanceDatabaseImportJob(
        processInstanceWriter,
        configurationService,
        importCompleteCallback,
        sourceExportIndex,
        databaseClient);
  }

  /**
   * Converts a Zeebe record timestamp (epoch milliseconds) to an {@link OffsetDateTime} in the
   * system default time zone.
   *
   * @param timestamp the Zeebe record timestamp in milliseconds since epoch
   * @return the corresponding {@link OffsetDateTime}
   */
  protected OffsetDateTime dateForTimestamp(final long timestamp) {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
  }
}
