/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_INCIDENT_INDEX_NAME;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentType;
import io.camunda.optimize.dto.zeebe.incident.ZeebeIncidentDataDto;
import io.camunda.optimize.dto.zeebe.incident.ZeebeIncidentRecordDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class ZeebeIncidentImportService
    extends ZeebeProcessInstanceSubEntityImportService<ZeebeIncidentRecordDto> {

  private static final Set<IncidentIntent> INTENTS_TO_IMPORT =
      Set.of(IncidentIntent.CREATED, IncidentIntent.RESOLVED);
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ZeebeIncidentImportService.class);

  private final AgentRunEnrichmentService agentRunEnrichmentService;
  private final LongSupplier agentRunImportPosition;

  public ZeebeIncidentImportService(
      final ConfigurationService configurationService,
      final ProcessInstanceWriter processInstanceWriter,
      final int partitionId,
      final ProcessDefinitionReader processDefinitionReader,
      final DatabaseClient databaseClient) {
    this(
        configurationService,
        processInstanceWriter,
        partitionId,
        processDefinitionReader,
        databaseClient,
        null,
        () -> Long.MIN_VALUE);
  }

  /**
   * @param agentRunImportPosition the position up to which the agent run import of the partition
   *     has fetched records
   */
  public ZeebeIncidentImportService(
      final ConfigurationService configurationService,
      final ProcessInstanceWriter processInstanceWriter,
      final int partitionId,
      final ProcessDefinitionReader processDefinitionReader,
      final DatabaseClient databaseClient,
      final AgentRunEnrichmentService agentRunEnrichmentService,
      final LongSupplier agentRunImportPosition) {
    super(
        configurationService,
        processInstanceWriter,
        partitionId,
        processDefinitionReader,
        databaseClient,
        ZEEBE_INCIDENT_INDEX_NAME);
    this.agentRunEnrichmentService = agentRunEnrichmentService;
    this.agentRunImportPosition = agentRunImportPosition;
  }

  /**
   * Incidents are imported before the agent runs of their process instance, which wait for this
   * import. Only when the Zeebe exporter retried a partly failed bulk can an incident become
   * visible after the agent run import passed it; its runs are then enriched again once it is
   * stored.
   */
  @Override
  public void executeImport(
      final List<ZeebeIncidentRecordDto> zeebeRecords, final Runnable importCompleteCallback) {
    final Map<String, String> late =
        lateIncidentProcessInstances(zeebeRecords, agentRunImportPosition.getAsLong());
    if (late.isEmpty() || agentRunEnrichmentService == null) {
      super.executeImport(zeebeRecords, importCompleteCallback);
      return;
    }
    super.executeImport(
        zeebeRecords,
        () -> {
          try {
            agentRunEnrichmentService.reEnrichAfterLateIncidents(late);
          } catch (final RuntimeException e) {
            LOG.warn("Could not re-enrich agent runs after late incidents", e);
          }
          importCompleteCallback.run();
        });
  }

  /** processDefinitionKey by processInstanceId of incidents the agent run import already passed. */
  static Map<String, String> lateIncidentProcessInstances(
      final List<ZeebeIncidentRecordDto> records, final long agentRunPosition) {
    return records.stream()
        .filter(record -> record.getPosition() <= agentRunPosition)
        .collect(
            Collectors.toMap(
                record -> String.valueOf(record.getValue().getProcessInstanceKey()),
                record -> record.getValue().getBpmnProcessId(),
                (a, b) -> a));
  }

  @Override
  List<ProcessInstanceDto> filterAndMapZeebeRecordsToOptimizeEntities(
      final List<ZeebeIncidentRecordDto> zeebeRecords) {
    final List<ProcessInstanceDto> optimizeDtos =
        zeebeRecords.stream()
            .filter(zeebeRecord -> INTENTS_TO_IMPORT.contains(zeebeRecord.getIntent()))
            .collect(
                Collectors.groupingBy(
                    zeebeRecord -> zeebeRecord.getValue().getProcessInstanceKey()))
            .values()
            .stream()
            .map(this::createProcessInstanceForData)
            .collect(Collectors.toList());
    LOG.debug(
        "Processing {} fetched zeebe incident records, of which {} are relevant to Optimize and will be imported.",
        zeebeRecords.size(),
        optimizeDtos.size());
    return optimizeDtos;
  }

  private ProcessInstanceDto createProcessInstanceForData(
      final List<ZeebeIncidentRecordDto> recordsForInstance) {
    final ZeebeIncidentDataDto firstRecordValue = recordsForInstance.get(0).getValue();
    final ProcessInstanceDto instanceToAdd =
        createSkeletonProcessInstance(
            firstRecordValue.getBpmnProcessId(),
            firstRecordValue.getProcessInstanceKey(),
            firstRecordValue.getProcessDefinitionKey(),
            firstRecordValue.getTenantId());
    return updateIncidents(instanceToAdd, recordsForInstance);
  }

  private ProcessInstanceDto updateIncidents(
      final ProcessInstanceDto instanceToAdd,
      final List<ZeebeIncidentRecordDto> recordsForInstance) {
    final Map<Long, IncidentDto> incidentsByRecordKey = new HashMap<>();
    recordsForInstance.forEach(
        incident -> {
          final long recordKey = incident.getKey();
          final IncidentDto incidentForKey =
              incidentsByRecordKey.getOrDefault(recordKey, createSkeletonIncident(incident));
          if (incident.getIntent() == IncidentIntent.CREATED
              && incidentForKey.getIncidentStatus() != IncidentStatus.RESOLVED) {
            incidentForKey.setIncidentStatus(IncidentStatus.OPEN);
            incidentForKey.setCreateTime(dateForTimestamp(incident.getTimestamp()));
          } else if (incident.getIntent() == IncidentIntent.RESOLVED) {
            incidentForKey.setIncidentStatus(IncidentStatus.RESOLVED);
            incidentForKey.setEndTime(dateForTimestamp(incident.getTimestamp()));
          }
          updateDurationIfMissing(incidentForKey);
          incidentsByRecordKey.put(recordKey, incidentForKey);
        });
    instanceToAdd.setIncidents(new ArrayList<>(incidentsByRecordKey.values()));
    return instanceToAdd;
  }

  private void updateDurationIfMissing(final IncidentDto incidentDto) {
    if (incidentDto.getDurationInMs() == null
        && incidentDto.getCreateTime() != null
        && incidentDto.getEndTime() != null) {
      incidentDto.setDurationInMs(
          incidentDto.getCreateTime().until(incidentDto.getEndTime(), ChronoUnit.MILLIS));
    }
  }

  private IncidentDto createSkeletonIncident(final ZeebeIncidentRecordDto zeebeIncidentRecordDto) {
    final ZeebeIncidentDataDto incidentDataDto = zeebeIncidentRecordDto.getValue();
    final IncidentDto incidentDto = new IncidentDto();
    incidentDto.setId(String.valueOf(zeebeIncidentRecordDto.getKey()));
    incidentDto.setDefinitionKey(zeebeIncidentRecordDto.getValue().getBpmnProcessId());
    incidentDto.setIncidentType(IncidentType.valueOfId(incidentDataDto.getErrorType().toString()));
    incidentDto.setActivityId(String.valueOf(incidentDataDto.getElementInstanceKey()));
    incidentDto.setIncidentMessage(incidentDataDto.getErrorMessage());
    incidentDto.setTenantId(incidentDataDto.getTenantId());
    return incidentDto;
  }
}
