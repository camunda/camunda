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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class ZeebeIncidentImportService
    extends ZeebeProcessInstanceSubEntityImportService<ZeebeIncidentRecordDto> {

  private static final Set<IncidentIntent> INTENTS_TO_IMPORT =
      Set.of(IncidentIntent.CREATED, IncidentIntent.RESOLVED);
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ZeebeIncidentImportService.class);

  public ZeebeIncidentImportService(
      final ConfigurationService configurationService,
      final ProcessInstanceWriter processInstanceWriter,
      final int partitionId,
      final ProcessDefinitionReader processDefinitionReader,
      final DatabaseClient databaseClient) {
    super(
        configurationService,
        processInstanceWriter,
        partitionId,
        processDefinitionReader,
        databaseClient,
        ZEEBE_INCIDENT_INDEX_NAME);
  }

  @Override
  protected List<ProcessInstanceDto> filterAndMapZeebeRecordsToOptimizeEntities(
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
    log.debug(
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
            incidentForKey.setCreateTime(dateForTimestamp(incident));
          } else if (incident.getIntent() == IncidentIntent.RESOLVED) {
            incidentForKey.setIncidentStatus(IncidentStatus.RESOLVED);
            incidentForKey.setEndTime(dateForTimestamp(incident));
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

  private OffsetDateTime dateForTimestamp(final ZeebeIncidentRecordDto zeebeRecord) {
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(zeebeRecord.getTimestamp()), ZoneId.systemDefault());
  }
}
