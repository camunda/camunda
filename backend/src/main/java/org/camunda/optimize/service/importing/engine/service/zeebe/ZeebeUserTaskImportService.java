/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service.zeebe;

import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskDataDto;
import org.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskRecordDto;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.db.writer.ZeebeProcessInstanceWriter;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.CREATING;
import static org.camunda.optimize.service.db.DatabaseConstants.ZEEBE_USER_TASK_INDEX_NAME;
import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;

@Slf4j
public class ZeebeUserTaskImportService extends ZeebeProcessInstanceSubEntityImportService<ZeebeUserTaskRecordDto> {

  private static final Set<UserTaskIntent> INTENTS_TO_IMPORT = Set.of(
    CREATING,
    COMPLETED
  );

  public ZeebeUserTaskImportService(final ConfigurationService configurationService,
                                    final ZeebeProcessInstanceWriter processInstanceWriter, final int partitionId,
                                    final ProcessDefinitionReader processDefinitionReader,
                                    final DatabaseClient databaseClient) {
    super(
      configurationService,
      processInstanceWriter,
      partitionId,
      processDefinitionReader,
      databaseClient,
      ZEEBE_USER_TASK_INDEX_NAME
    );
  }

  @Override
  List<ProcessInstanceDto> filterAndMapZeebeRecordsToOptimizeEntities(final List<ZeebeUserTaskRecordDto> userTaskRecords) {
    final List<ProcessInstanceDto> optimizeDtos = userTaskRecords.stream()
      .filter(zeebeRecord -> INTENTS_TO_IMPORT.contains(zeebeRecord.getIntent()))
      .collect(Collectors.groupingBy(zeebeRecord -> zeebeRecord.getValue().getProcessInstanceKey()))
      .values().stream()
      .map(this::createProcessInstanceForData)
      .toList();
    log.debug(
      "Processing {} fetched zeebe userTask records, of which {} are relevant to Optimize and will be imported.",
      userTaskRecords.size(),
      optimizeDtos.size()
    );
    return optimizeDtos;
  }

  private ProcessInstanceDto createProcessInstanceForData(final List<ZeebeUserTaskRecordDto> userTaskRecordsForInstance) {
    final ZeebeUserTaskDataDto firstRecordValue = userTaskRecordsForInstance.get(0).getValue();
    final ProcessInstanceDto instanceToAdd = createSkeletonProcessInstance(
      firstRecordValue.getBpmnProcessId(),
      firstRecordValue.getProcessInstanceKey(),
      firstRecordValue.getProcessDefinitionKey(),
      firstRecordValue.getTenantId()
    );
    updateUserTaskData(instanceToAdd, userTaskRecordsForInstance);
    return instanceToAdd;
  }

  private void updateUserTaskData(final ProcessInstanceDto instanceToAdd,
                                  final List<ZeebeUserTaskRecordDto> userTaskRecordsForInstance) {
    Map<Long, FlowNodeInstanceDto> userTaskInstancesByKey = new HashMap<>();
    userTaskRecordsForInstance
      .forEach(zeebeUserTaskInstanceRecord -> {
        final long recordKey = zeebeUserTaskInstanceRecord.getKey();
        FlowNodeInstanceDto userTaskForKey = userTaskInstancesByKey.getOrDefault(
          recordKey, createSkeletonUserTaskInstance(zeebeUserTaskInstanceRecord.getValue()));
        final UserTaskIntent userTaskRecordIntent = zeebeUserTaskInstanceRecord.getIntent();
        if (userTaskRecordIntent == CREATING) {
          userTaskForKey.setStartDate(zeebeUserTaskInstanceRecord.getDateForTimestamp());
        } else if (userTaskRecordIntent == COMPLETED) {
          userTaskForKey.setEndDate(zeebeUserTaskInstanceRecord.getDateForTimestamp());
          updateDurationIfCompleted(userTaskForKey);
        }
        userTaskInstancesByKey.put(recordKey, userTaskForKey);
      });
    instanceToAdd.setFlowNodeInstances(new ArrayList<>(userTaskInstancesByKey.values()));
  }

  private FlowNodeInstanceDto createSkeletonUserTaskInstance(final ZeebeUserTaskDataDto userTaskData) {
    return new FlowNodeInstanceDto()
      .setFlowNodeInstanceId(String.valueOf(userTaskData.getElementInstanceKey()))
      .setFlowNodeId(userTaskData.getElementId())
      .setFlowNodeType(FLOW_NODE_TYPE_USER_TASK)
      .setProcessInstanceId(String.valueOf(userTaskData.getProcessInstanceKey()))
      .setCanceled(false)
      .setDefinitionKey(userTaskData.getBpmnProcessId())
      .setDefinitionVersion(String.valueOf(userTaskData.getProcessDefinitionVersion()))
      .setTenantId(userTaskData.getTenantId())
      .setUserTaskInstanceId(String.valueOf(userTaskData.getUserTaskKey()))
      .setDueDate(userTaskData.getDateForDueDate());
  }

  private void updateDurationIfCompleted(final FlowNodeInstanceDto flowNodeToAdd) {
    if (flowNodeToAdd.getStartDate() != null && flowNodeToAdd.getEndDate() != null) {
      flowNodeToAdd.setTotalDurationInMs(
        flowNodeToAdd.getStartDate().until(flowNodeToAdd.getEndDate(), ChronoUnit.MILLIS));
    }
  }

}
