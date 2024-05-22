/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service.zeebe;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;
import static org.camunda.optimize.service.db.DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME;

import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessInstanceConstants;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceDataDto;
import org.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceRecordDto;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.db.writer.ZeebeProcessInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

@Slf4j
public class ZeebeProcessInstanceImportService
    extends ZeebeProcessInstanceSubEntityImportService<ZeebeProcessInstanceRecordDto> {

  private static final Set<BpmnElementType> TYPES_TO_IGNORE =
      Set.of(BpmnElementType.UNSPECIFIED, BpmnElementType.SEQUENCE_FLOW);

  public static final Set<ProcessInstanceIntent> INTENTS_TO_IMPORT =
      Set.of(
          ProcessInstanceIntent.ELEMENT_COMPLETED,
          ProcessInstanceIntent.ELEMENT_TERMINATED,
          ProcessInstanceIntent.ELEMENT_ACTIVATING);

  public ZeebeProcessInstanceImportService(
      final ConfigurationService configurationService,
      final ZeebeProcessInstanceWriter processInstanceWriter,
      final int partitionId,
      final ProcessDefinitionReader processDefinitionReader,
      final DatabaseClient databaseClient) {
    super(
        configurationService,
        processInstanceWriter,
        partitionId,
        processDefinitionReader,
        databaseClient,
        ZEEBE_PROCESS_INSTANCE_INDEX_NAME);
  }

  @Override
  protected List<ProcessInstanceDto> filterAndMapZeebeRecordsToOptimizeEntities(
      List<ZeebeProcessInstanceRecordDto> zeebeRecords) {
    final List<ProcessInstanceDto> optimizeDtos =
        new ArrayList<>(
            zeebeRecords.stream()
                .filter(
                    zeebeRecord -> {
                      final BpmnElementType bpmnElementType =
                          zeebeRecord.getValue().getBpmnElementType();
                      return bpmnElementType != null && !TYPES_TO_IGNORE.contains(bpmnElementType);
                    })
                .filter(zeebeRecord -> INTENTS_TO_IMPORT.contains(zeebeRecord.getIntent()))
                .collect(
                    Collectors.groupingBy(
                        zeebeRecord -> zeebeRecord.getValue().getProcessInstanceKey(),
                        Collectors.mapping(
                            Function.identity(),
                            Collectors.collectingAndThen(
                                Collectors.toList(), this::createProcessInstanceForData))))
                .values());
    log.debug(
        "Processing {} fetched zeebe process instance records, of which {} are relevant to Optimize and will be imported.",
        zeebeRecords.size(),
        optimizeDtos.size());
    return optimizeDtos;
  }

  private ProcessInstanceDto createProcessInstanceForData(
      final List<ZeebeProcessInstanceRecordDto> recordsForInstance) {
    // All instances in the list should have the same process data, so we can simply take the first
    // entry
    final ZeebeProcessInstanceRecordDto firstRecord = recordsForInstance.get(0);
    final ZeebeProcessInstanceDataDto firstRecordValue = firstRecord.getValue();
    final ProcessInstanceDto instanceToAdd =
        createSkeletonProcessInstance(
            firstRecordValue.getBpmnProcessId(),
            firstRecordValue.getProcessInstanceKey(),
            firstRecordValue.getProcessDefinitionKey(),
            firstRecordValue.getTenantId());
    instanceToAdd.setProcessDefinitionVersion(String.valueOf(firstRecordValue.getVersion()));
    instanceToAdd.setIncidents(Collections.emptyList());
    instanceToAdd.setVariables(Collections.emptyList());

    updateProcessStateAndDateProperties(instanceToAdd, recordsForInstance);
    updateFlowNodeEventsForProcess(instanceToAdd, recordsForInstance);
    return instanceToAdd;
  }

  private void updateProcessStateAndDateProperties(
      final ProcessInstanceDto instanceToAdd,
      final List<ZeebeProcessInstanceRecordDto> recordsForInstance) {
    recordsForInstance.stream()
        .filter(
            zeebeRecord ->
                BpmnElementType.PROCESS.equals(zeebeRecord.getValue().getBpmnElementType()))
        .forEach(
            processInstance -> {
              switch (processInstance.getIntent()) {
                case ELEMENT_COMPLETED:
                  updateStateIfValidTransition(
                      instanceToAdd, ProcessInstanceConstants.COMPLETED_STATE);
                  instanceToAdd.setEndDate(processInstance.getDateForTimestamp());
                  break;
                case ELEMENT_TERMINATED:
                  updateStateIfValidTransition(
                      instanceToAdd, ProcessInstanceConstants.EXTERNALLY_TERMINATED_STATE);
                  instanceToAdd.setEndDate(processInstance.getDateForTimestamp());
                  break;
                case ELEMENT_ACTIVATING:
                  updateStateIfValidTransition(
                      instanceToAdd, ProcessInstanceConstants.ACTIVE_STATE);
                  instanceToAdd.setStartDate(processInstance.getDateForTimestamp());
                  break;
                default:
                  throw new OptimizeRuntimeException(
                      "Unsupported intent: " + processInstance.getIntent());
              }
              updateDurationIfCompleted(instanceToAdd);
            });
  }

  private void updateFlowNodeEventsForProcess(
      final ProcessInstanceDto instanceToAdd,
      final List<ZeebeProcessInstanceRecordDto> recordsForInstance) {
    Map<Long, FlowNodeInstanceDto> flowNodeInstancesByRecordKey = new HashMap<>();
    recordsForInstance.stream()
        .filter(
            zeebeRecord ->
                zeebeRecord.getValue().getBpmnElementType().getElementTypeName().isPresent())
        .filter(
            zeebeRecord ->
                !BpmnElementType.PROCESS.equals(zeebeRecord.getValue().getBpmnElementType()))
        .forEach(
            zeebeFlowNodeInstanceRecord -> {
              final long recordKey = zeebeFlowNodeInstanceRecord.getKey();
              FlowNodeInstanceDto flowNodeForKey =
                  flowNodeInstancesByRecordKey.getOrDefault(
                      recordKey, createSkeletonFlowNodeInstance(zeebeFlowNodeInstanceRecord));
              final ProcessInstanceIntent instanceIntent = zeebeFlowNodeInstanceRecord.getIntent();
              if (instanceIntent == ELEMENT_COMPLETED) {
                flowNodeForKey.setEndDate(zeebeFlowNodeInstanceRecord.getDateForTimestamp());
              } else if (instanceIntent == ELEMENT_TERMINATED) {
                flowNodeForKey.setCanceled(true);
                flowNodeForKey.setEndDate(zeebeFlowNodeInstanceRecord.getDateForTimestamp());
              } else if (instanceIntent == ELEMENT_ACTIVATING) {
                flowNodeForKey.setStartDate(zeebeFlowNodeInstanceRecord.getDateForTimestamp());
              }
              updateDurationIfCompleted(flowNodeForKey);
              flowNodeInstancesByRecordKey.put(recordKey, flowNodeForKey);
            });
    instanceToAdd.setFlowNodeInstances(new ArrayList<>(flowNodeInstancesByRecordKey.values()));
  }

  private FlowNodeInstanceDto createSkeletonFlowNodeInstance(
      final ZeebeProcessInstanceRecordDto zeebeProcessInstanceRecordDto) {
    final ZeebeProcessInstanceDataDto zeebeInstanceRecord =
        zeebeProcessInstanceRecordDto.getValue();
    return new FlowNodeInstanceDto(
            String.valueOf(zeebeInstanceRecord.getBpmnProcessId()),
            String.valueOf(zeebeInstanceRecord.getVersion()),
            zeebeInstanceRecord.getTenantId(),
            String.valueOf(zeebeInstanceRecord.getProcessInstanceKey()),
            zeebeInstanceRecord.getElementId(),
            zeebeInstanceRecord
                .getBpmnElementType()
                .getElementTypeName()
                .orElseThrow(
                    () ->
                        new OptimizeRuntimeException(
                            "Cannot create flow node instances for records without element types")),
            String.valueOf(zeebeProcessInstanceRecordDto.getKey()))
        .setCanceled(false);
  }

  private void updateStateIfValidTransition(ProcessInstanceDto instance, String targetState) {
    if (instance.getState() == null
        || instance.getState().equals(ProcessInstanceConstants.ACTIVE_STATE)) {
      instance.setState(targetState);
    }
  }

  private void updateDurationIfCompleted(final ProcessInstanceDto instanceToAdd) {
    if (instanceToAdd.getStartDate() != null && instanceToAdd.getEndDate() != null) {
      instanceToAdd.setDuration(
          instanceToAdd.getStartDate().until(instanceToAdd.getEndDate(), ChronoUnit.MILLIS));
    }
  }

  private void updateDurationIfCompleted(final FlowNodeInstanceDto flowNodeToAdd) {
    if (flowNodeToAdd.getStartDate() != null && flowNodeToAdd.getEndDate() != null) {
      flowNodeToAdd.setTotalDurationInMs(
          flowNodeToAdd.getStartDate().until(flowNodeToAdd.getEndDate(), ChronoUnit.MILLIS));
    }
  }
}
