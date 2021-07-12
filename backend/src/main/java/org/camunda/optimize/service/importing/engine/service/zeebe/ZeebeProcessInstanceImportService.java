/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service.zeebe;

import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessInstanceConstants;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.ZeebeDataSourceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceDataDto;
import org.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceRecordDto;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.job.importing.ZeebeProcessInstanceElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.ZeebeProcessInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.util.BpmnModelUtil;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;

@AllArgsConstructor
@Slf4j
public class ZeebeProcessInstanceImportService implements ImportService<ZeebeProcessInstanceRecordDto> {

  private static final Set<BpmnElementType> TYPES_TO_IGNORE = Set.of(
    BpmnElementType.UNSPECIFIED, BpmnElementType.SEQUENCE_FLOW, BpmnElementType.TESTING_ONLY
  );

  private final ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private final ZeebeProcessInstanceWriter processInstanceWriter;
  private final String zeebeName;
  private final int partitionId;

  @Override
  public void executeImport(final List<ZeebeProcessInstanceRecordDto> zeebeProcessInstanceRecords,
                            final Runnable importCompleteCallback) {
    log.trace("Importing process definitions from zeebe records...");

    boolean newDataIsAvailable = !zeebeProcessInstanceRecords.isEmpty();
    if (newDataIsAvailable) {
      final List<ProcessInstanceDto> newOptimizeEntities =
        mapZeebeRecordsToOptimizeEntities(zeebeProcessInstanceRecords);
      final ElasticsearchImportJob<ProcessInstanceDto> elasticsearchImportJob =
        createElasticsearchImportJob(newOptimizeEntities, importCompleteCallback);
      addElasticsearchImportJobToQueue(elasticsearchImportJob);
    }
  }

  @Override
  public ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return elasticsearchImportJobExecutor;
  }

  private void addElasticsearchImportJobToQueue(ElasticsearchImportJob<ProcessInstanceDto> elasticsearchImportJob) {
    elasticsearchImportJobExecutor.executeImportJob(elasticsearchImportJob);
  }

  private List<ProcessInstanceDto> mapZeebeRecordsToOptimizeEntities(
    List<ZeebeProcessInstanceRecordDto> zeebeRecords) {
    return zeebeRecords.stream()
      .collect(Collectors.groupingBy(zeebeRecord -> zeebeRecord.getValue().getProcessInstanceKey()))
      .values().stream()
      .map(this::createProcessInstanceForData)
      .collect(Collectors.toList());
  }

  private ProcessInstanceDto createProcessInstanceForData(final List<ZeebeProcessInstanceRecordDto> recordsForInstance) {
    final ProcessInstanceDto instanceToAdd = createSkeletonProcessInstance(recordsForInstance);
    updateProcessStateAndDateProperties(instanceToAdd, recordsForInstance);
    updateFlowNodeEventsForProcess(instanceToAdd, recordsForInstance);
    return instanceToAdd;
  }

  private ProcessInstanceDto createSkeletonProcessInstance(final List<ZeebeProcessInstanceRecordDto> recordsForInstance) {
    // All instances in the list should have the same process data so we can simply take the first entry
    final ZeebeProcessInstanceRecordDto firstRecord = recordsForInstance.get(0);
    final ZeebeProcessInstanceDataDto firstRecordValue = firstRecord.getValue();
    final ProcessInstanceDto processInstanceDto = new ProcessInstanceDto();
    processInstanceDto.setProcessInstanceId(String.valueOf(firstRecordValue.getProcessInstanceKey()));
    processInstanceDto.setProcessDefinitionId(String.valueOf(firstRecordValue.getProcessDefinitionKey()));
    processInstanceDto.setProcessDefinitionKey(firstRecordValue.getBpmnProcessId());
    processInstanceDto.setProcessDefinitionVersion(String.valueOf(firstRecordValue.getVersion()));
    processInstanceDto.setDataSource(new ZeebeDataSourceDto(zeebeName, partitionId));
    // We don't currently store variables or incidents for zeebe process instances
    processInstanceDto.setIncidents(Collections.emptyList());
    processInstanceDto.setVariables(Collections.emptyList());
    return processInstanceDto;
  }

  private void updateProcessStateAndDateProperties(final ProcessInstanceDto instanceToAdd,
                                                   final List<ZeebeProcessInstanceRecordDto> recordsForInstance) {
    recordsForInstance.stream()
      .filter(zeebeRecord -> BpmnElementType.PROCESS.equals(zeebeRecord.getValue().getBpmnElementType()))
      .forEach(processInstance -> {
        switch (processInstance.getIntent()) {
          case ELEMENT_COMPLETED:
            updateStateIfValidTransition(instanceToAdd, ProcessInstanceConstants.COMPLETED_STATE);
            instanceToAdd.setEndDate(dateForTimestamp(processInstance));
            break;
          case ELEMENT_TERMINATED:
            updateStateIfValidTransition(instanceToAdd, ProcessInstanceConstants.EXTERNALLY_TERMINATED_STATE);
            instanceToAdd.setEndDate(dateForTimestamp(processInstance));
            break;
          case ELEMENT_ACTIVATING:
            updateStateIfValidTransition(instanceToAdd, ProcessInstanceConstants.ACTIVE_STATE);
            instanceToAdd.setStartDate(dateForTimestamp(processInstance));
            break;
          default:
            throw new OptimizeRuntimeException("Unsupported intent: " + processInstance.getIntent());
        }
        updateDurationIfMissing(instanceToAdd);
      });
  }

  private void updateFlowNodeEventsForProcess(final ProcessInstanceDto instanceToAdd,
                                              final List<ZeebeProcessInstanceRecordDto> recordsForInstance) {
    Map<Long, FlowNodeInstanceDto> flowNodeInstancesByRecordKey = new HashMap<>();
    recordsForInstance.stream()
      .filter(zeebeRecord -> !BpmnElementType.PROCESS.equals(zeebeRecord.getValue().getBpmnElementType())
        && !TYPES_TO_IGNORE.contains(zeebeRecord.getValue().getBpmnElementType()))
      .forEach(processFlowNodeInstance -> {
        final long recordKey = processFlowNodeInstance.getKey();
        FlowNodeInstanceDto flowNodeForKey = flowNodeInstancesByRecordKey.getOrDefault(
          recordKey, createSkeletonFlowNodeInstance(processFlowNodeInstance));
        final ProcessInstanceIntent instanceIntent = processFlowNodeInstance.getIntent();
        if (instanceIntent.equals(ELEMENT_COMPLETED)) {
          flowNodeForKey.setEndDate(dateForTimestamp(processFlowNodeInstance));
        } else if (instanceIntent.equals(ELEMENT_TERMINATED)) {
          flowNodeForKey.setCanceled(true);
          flowNodeForKey.setEndDate(dateForTimestamp(processFlowNodeInstance));
        } else if (instanceIntent.equals(ELEMENT_ACTIVATING)) {
          flowNodeForKey.setStartDate(dateForTimestamp(processFlowNodeInstance));
        }
        updateDurationIfMissing(flowNodeForKey);
        flowNodeInstancesByRecordKey.put(recordKey, flowNodeForKey);
      });
    instanceToAdd.setFlowNodeInstances(new ArrayList<>(flowNodeInstancesByRecordKey.values()));
  }

  private FlowNodeInstanceDto createSkeletonFlowNodeInstance(final ZeebeProcessInstanceRecordDto zeebeProcessInstanceRecordDto) {
    FlowNodeInstanceDto flowNodeInstanceDto = new FlowNodeInstanceDto();
    flowNodeInstanceDto.setFlowNodeId(zeebeProcessInstanceRecordDto.getValue().getElementId());
    flowNodeInstanceDto.setProcessInstanceId(
      String.valueOf(zeebeProcessInstanceRecordDto.getValue().getProcessInstanceKey()));
    flowNodeInstanceDto.setFlowNodeInstanceId(String.valueOf(zeebeProcessInstanceRecordDto.getKey()));
    flowNodeInstanceDto.setFlowNodeType(
      BpmnModelUtil.getFlowNodeTypeForBpmnElementType(zeebeProcessInstanceRecordDto.getValue().getBpmnElementType()));
    flowNodeInstanceDto.setCanceled(false);
    return flowNodeInstanceDto;
  }

  private OffsetDateTime dateForTimestamp(final ZeebeProcessInstanceRecordDto zeebeRecord) {
    return OffsetDateTime.ofInstant(
      Instant.ofEpochMilli(zeebeRecord.getTimestamp()), ZoneId.systemDefault());
  }

  private void updateStateIfValidTransition(ProcessInstanceDto instance, String targetState) {
    if (instance.getState() == null || instance.getState().equals(ProcessInstanceConstants.ACTIVE_STATE)) {
      instance.setState(targetState);
    }
  }

  private ElasticsearchImportJob<ProcessInstanceDto> createElasticsearchImportJob(
    final List<ProcessInstanceDto> processDefinitions,
    final Runnable importCompleteCallback) {
    ZeebeProcessInstanceElasticsearchImportJob procDefImportJob = new ZeebeProcessInstanceElasticsearchImportJob(
      processInstanceWriter, importCompleteCallback
    );
    procDefImportJob.setEntitiesToImport(processDefinitions);
    return procDefImportJob;
  }

  private void updateDurationIfMissing(final ProcessInstanceDto instanceToAdd) {
    if (instanceToAdd.getDuration() == null && instanceToAdd.getStartDate() != null && instanceToAdd.getEndDate() != null) {
      instanceToAdd.setDuration(instanceToAdd.getStartDate().until(instanceToAdd.getEndDate(), ChronoUnit.MILLIS));
    }
  }

  private void updateDurationIfMissing(final FlowNodeInstanceDto flowNodeToAdd) {
    if (flowNodeToAdd.getTotalDurationInMs() == null && flowNodeToAdd.getStartDate() != null && flowNodeToAdd.getEndDate() != null) {
      flowNodeToAdd.setTotalDurationInMs(flowNodeToAdd.getStartDate()
                                           .until(flowNodeToAdd.getEndDate(), ChronoUnit.MILLIS));
    }
  }

}
