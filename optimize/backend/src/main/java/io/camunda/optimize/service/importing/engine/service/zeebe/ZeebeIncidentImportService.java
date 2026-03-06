/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import io.camunda.optimize.dto.optimize.persistence.incident.FlatIncidentDto;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentType;
import io.camunda.optimize.dto.zeebe.incident.ZeebeIncidentDataDto;
import io.camunda.optimize.dto.zeebe.incident.ZeebeIncidentRecordDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.IncidentWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.job.FlatIncidentDatabaseImportJob;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class ZeebeIncidentImportService implements ImportService<ZeebeIncidentRecordDto> {

  private static final Set<IncidentIntent> INTENTS_TO_IMPORT =
      Set.of(IncidentIntent.CREATED, IncidentIntent.RESOLVED);
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ZeebeIncidentImportService.class);

  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final IncidentWriter incidentWriter;
  private final ConfigurationService configurationService;
  private final DatabaseClient databaseClient;

  public ZeebeIncidentImportService(
      final ConfigurationService configurationService,
      final IncidentWriter incidentWriter,
      final DatabaseClient databaseClient) {
    this.databaseImportJobExecutor =
        new DatabaseImportJobExecutor(getClass().getSimpleName(), configurationService);
    this.incidentWriter = incidentWriter;
    this.configurationService = configurationService;
    this.databaseClient = databaseClient;
  }

  @Override
  public void executeImport(
      final List<ZeebeIncidentRecordDto> zeebeRecords, final Runnable importCompleteCallback) {
    if (!zeebeRecords.isEmpty()) {
      final List<FlatIncidentDto> flatIncidents =
          filterAndMapZeebeRecordsToFlatIncidents(zeebeRecords);
      final DatabaseImportJob<FlatIncidentDto> importJob =
          createDatabaseImportJob(flatIncidents, importCompleteCallback);
      databaseImportJobExecutor.executeImportJob(importJob);
    }
  }

  /**
   * Creates (but does not execute) a {@link DatabaseImportJob} for the given records. The mediator
   * is responsible for submitting the returned job to its own {@link DatabaseImportJobExecutor}.
   *
   * @return an {@link Optional} containing the prepared import job, or empty if there are no
   *     relevant records to import.
   */
  public Optional<DatabaseImportJob<FlatIncidentDto>> createImportJob(
      final List<ZeebeIncidentRecordDto> zeebeRecords) {
    if (zeebeRecords.isEmpty()) {
      return Optional.empty();
    }
    final List<FlatIncidentDto> flatIncidents =
        filterAndMapZeebeRecordsToFlatIncidents(zeebeRecords);
    if (flatIncidents.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(createDatabaseImportJob(flatIncidents, () -> {}));
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

  private List<FlatIncidentDto> filterAndMapZeebeRecordsToFlatIncidents(
      final List<ZeebeIncidentRecordDto> zeebeRecords) {
    final List<FlatIncidentDto> flatIncidents =
        zeebeRecords.stream()
            .filter(zeebeRecord -> INTENTS_TO_IMPORT.contains(zeebeRecord.getIntent()))
            .collect(
                Collectors.groupingBy(
                    zeebeRecord -> zeebeRecord.getValue().getProcessInstanceKey()))
            .values()
            .stream()
            .flatMap(
                recordsForInstance -> createFlatIncidentsForInstance(recordsForInstance).stream())
            .collect(Collectors.toList());
    LOG.debug(
        "Processing {} fetched zeebe incident records, of which {} are relevant to Optimize and will be imported.",
        zeebeRecords.size(),
        flatIncidents.size());
    return flatIncidents;
  }

  private List<FlatIncidentDto> createFlatIncidentsForInstance(
      final List<ZeebeIncidentRecordDto> recordsForInstance) {
    final ZeebeIncidentDataDto firstRecordValue = recordsForInstance.get(0).getValue();
    final String processDefinitionKey = firstRecordValue.getBpmnProcessId();
    final String processDefinitionId = String.valueOf(firstRecordValue.getProcessDefinitionKey());
    final String processInstanceId = String.valueOf(firstRecordValue.getProcessInstanceKey());
    final Map<Long, FlatIncidentDto> incidentsByRecordKey = new HashMap<>();
    recordsForInstance.forEach(
        incidentRecord -> {
          final long recordKey = incidentRecord.getKey();
          final FlatIncidentDto flatIncident =
              incidentsByRecordKey.getOrDefault(
                  recordKey,
                  createSkeletonFlatIncident(
                      incidentRecord,
                      processDefinitionKey,
                      processDefinitionId,
                      processInstanceId));
          if (incidentRecord.getIntent() == IncidentIntent.CREATED
              && flatIncident.getIncidentStatus() != IncidentStatus.RESOLVED) {
            flatIncident.setIncidentStatus(IncidentStatus.OPEN);
            flatIncident.setCreateTime(dateForTimestamp(incidentRecord));
          } else if (incidentRecord.getIntent() == IncidentIntent.RESOLVED) {
            flatIncident.setIncidentStatus(IncidentStatus.RESOLVED);
            flatIncident.setEndTime(dateForTimestamp(incidentRecord));
          }
          updateDurationIfMissing(flatIncident);
          incidentsByRecordKey.put(recordKey, flatIncident);
        });
    return new ArrayList<>(incidentsByRecordKey.values());
  }

  private FlatIncidentDto createSkeletonFlatIncident(
      final ZeebeIncidentRecordDto record,
      final String processDefinitionKey,
      final String processDefinitionId,
      final String processInstanceId) {
    final ZeebeIncidentDataDto data = record.getValue();
    final FlatIncidentDto flatIncident = new FlatIncidentDto();
    flatIncident.setId(String.valueOf(record.getKey()));
    flatIncident.setProcessDefinitionKey(processDefinitionKey);
    flatIncident.setProcessDefinitionId(processDefinitionId);
    flatIncident.setProcessInstanceId(processInstanceId);
    flatIncident.setIncidentType(IncidentType.valueOfId(data.getErrorType().toString()));
    flatIncident.setActivityId(String.valueOf(data.getElementInstanceKey()));
    flatIncident.setIncidentMessage(data.getErrorMessage());
    flatIncident.setDefinitionKey(data.getBpmnProcessId());
    flatIncident.setTenantId(data.getTenantId());
    return flatIncident;
  }

  private void updateDurationIfMissing(final FlatIncidentDto flatIncident) {
    if (flatIncident.getDurationInMs() == null
        && flatIncident.getCreateTime() != null
        && flatIncident.getEndTime() != null) {
      flatIncident.setDurationInMs(
          flatIncident.getCreateTime().until(flatIncident.getEndTime(), ChronoUnit.MILLIS));
    }
  }

  private OffsetDateTime dateForTimestamp(final ZeebeIncidentRecordDto zeebeRecord) {
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(zeebeRecord.getTimestamp()), ZoneId.systemDefault());
  }

  private DatabaseImportJob<FlatIncidentDto> createDatabaseImportJob(
      final List<FlatIncidentDto> flatIncidents, final Runnable importCompleteCallback) {
    final FlatIncidentDatabaseImportJob importJob =
        new FlatIncidentDatabaseImportJob(
            incidentWriter, configurationService, importCompleteCallback, databaseClient);
    importJob.setEntitiesToImport(flatIncidents);
    return importJob;
  }
}
