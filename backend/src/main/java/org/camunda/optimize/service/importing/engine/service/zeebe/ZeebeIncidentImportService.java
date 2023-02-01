/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service.zeebe;

import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentType;
import org.camunda.optimize.dto.zeebe.incident.ZeebeIncidentDataDto;
import org.camunda.optimize.dto.zeebe.incident.ZeebeIncidentRecordDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.writer.ZeebeProcessInstanceWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

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

@Slf4j
public class ZeebeIncidentImportService extends ZeebeProcessInstanceSubEntityImportService<ZeebeIncidentRecordDto> {

  private static final Set<IncidentIntent> INTENTS_TO_IMPORT = Set.of(
    IncidentIntent.CREATED,
    IncidentIntent.RESOLVED
  );

  public ZeebeIncidentImportService(final ConfigurationService configurationService,
                                    final ZeebeProcessInstanceWriter processInstanceWriter,
                                    final int partitionId,
                                    final ProcessDefinitionReader processDefinitionReader) {
    super(configurationService, processInstanceWriter, partitionId, processDefinitionReader);
  }

  @Override
  protected List<ProcessInstanceDto> filterAndMapZeebeRecordsToOptimizeEntities(
    List<ZeebeIncidentRecordDto> zeebeRecords) {
    final List<ProcessInstanceDto> optimizeDtos = zeebeRecords.stream()
      .filter(zeebeRecord -> INTENTS_TO_IMPORT.contains(zeebeRecord.getIntent()))
      .collect(Collectors.groupingBy(zeebeRecord -> zeebeRecord.getValue().getProcessInstanceKey()))
      .values().stream()
      .map(this::createProcessInstanceForData)
      .collect(Collectors.toList());
    log.debug(
      "Processing {} fetched zeebe incident records, of which {} are relevant to Optimize and will be imported.",
      zeebeRecords.size(),
      optimizeDtos.size()
    );
    return optimizeDtos;
  }

  private ProcessInstanceDto createProcessInstanceForData(final List<ZeebeIncidentRecordDto> recordsForInstance) {
    final ZeebeIncidentDataDto firstRecordValue = recordsForInstance.get(0).getValue();
    final ProcessInstanceDto instanceToAdd = createSkeletonProcessInstance(
      firstRecordValue.getBpmnProcessId(),
      firstRecordValue.getProcessInstanceKey(),
      firstRecordValue.getProcessDefinitionKey()
    );
    return updateIncidents(instanceToAdd, recordsForInstance);
  }

  private ProcessInstanceDto updateIncidents(final ProcessInstanceDto instanceToAdd,
                                             List<ZeebeIncidentRecordDto> recordsForInstance) {
    Map<Long, IncidentDto> incidentsByRecordKey = new HashMap<>();
    recordsForInstance
      .forEach(incident -> {
        final long recordKey = incident.getKey();
        IncidentDto incidentForKey = incidentsByRecordKey.getOrDefault(
          recordKey, createSkeletonIncident(incident));
        if (incident.getIntent() == IncidentIntent.CREATED && incidentForKey.getIncidentStatus() != IncidentStatus.RESOLVED) {
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
    if (incidentDto.getDurationInMs() == null && incidentDto.getCreateTime() != null && incidentDto.getEndTime() != null) {
      incidentDto.setDurationInMs(incidentDto.getCreateTime().until(incidentDto.getEndTime(), ChronoUnit.MILLIS));
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
    return incidentDto;
  }

  private OffsetDateTime dateForTimestamp(final ZeebeIncidentRecordDto zeebeRecord) {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(zeebeRecord.getTimestamp()), ZoneId.systemDefault());
  }

}
