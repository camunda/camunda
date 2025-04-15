/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import static io.camunda.optimize.dto.optimize.importing.UserTaskIdentityOperationType.CLAIM_OPERATION_TYPE;
import static io.camunda.optimize.dto.optimize.importing.UserTaskIdentityOperationType.UNCLAIM_OPERATION_TYPE;
import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_USER_TASK_INDEX_NAME;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_USER_TASK;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.ASSIGNED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.CANCELED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.CREATING;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.persistence.AssigneeOperationDto;
import io.camunda.optimize.dto.optimize.query.process.FlowNodeInstanceDto;
import io.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskDataDto;
import io.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskRecordDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.netty.util.internal.StringUtil;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class ZeebeUserTaskImportService
    extends ZeebeProcessInstanceSubEntityImportService<ZeebeUserTaskRecordDto> {

  public static final Set<UserTaskIntent> INTENTS_TO_IMPORT =
      Set.of(CREATING, ASSIGNED, COMPLETED, CANCELED);
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ZeebeUserTaskImportService.class);

  public ZeebeUserTaskImportService(
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
        ZEEBE_USER_TASK_INDEX_NAME);
  }

  @Override
  List<ProcessInstanceDto> filterAndMapZeebeRecordsToOptimizeEntities(
      final List<ZeebeUserTaskRecordDto> userTaskRecords) {
    final List<ProcessInstanceDto> optimizeDtos =
        userTaskRecords.stream()
            .filter(zeebeRecord -> INTENTS_TO_IMPORT.contains(zeebeRecord.getIntent()))
            .collect(
                Collectors.groupingBy(
                    zeebeRecord -> zeebeRecord.getValue().getProcessInstanceKey()))
            .values()
            .stream()
            .map(this::createProcessInstanceForData)
            .toList();
    LOG.debug(
        "Processing {} fetched zeebe userTask records, of which {} are relevant to Optimize and will be imported.",
        userTaskRecords.size(),
        optimizeDtos.size());
    return optimizeDtos;
  }

  private ProcessInstanceDto createProcessInstanceForData(
      final List<ZeebeUserTaskRecordDto> userTaskRecordsForInstance) {
    final ZeebeUserTaskDataDto firstRecordValue = userTaskRecordsForInstance.get(0).getValue();
    final ProcessInstanceDto instanceToAdd =
        createSkeletonProcessInstance(
            firstRecordValue.getBpmnProcessId(),
            firstRecordValue.getProcessInstanceKey(),
            firstRecordValue.getProcessDefinitionKey(),
            firstRecordValue.getTenantId());
    updateUserTaskData(instanceToAdd, userTaskRecordsForInstance);
    return instanceToAdd;
  }

  private void updateUserTaskData(
      final ProcessInstanceDto instanceToAdd,
      final List<ZeebeUserTaskRecordDto> userTaskRecordsForInstance) {
    final Map<Long, FlowNodeInstanceDto> userTaskInstancesByKey = new HashMap<>();
    userTaskRecordsForInstance.forEach(
        zeebeUserTaskInstanceRecord -> {
          final long recordKey = zeebeUserTaskInstanceRecord.getKey();
          final FlowNodeInstanceDto userTaskForKey =
              userTaskInstancesByKey.getOrDefault(
                  recordKey,
                  createSkeletonUserTaskInstance(zeebeUserTaskInstanceRecord.getValue()));
          final UserTaskIntent userTaskRecordIntent = zeebeUserTaskInstanceRecord.getIntent();
          if (userTaskRecordIntent == CREATING) {
            userTaskForKey.setStartDate(zeebeUserTaskInstanceRecord.getDateForTimestamp());
            if (!StringUtil.isNullOrEmpty(zeebeUserTaskInstanceRecord.getValue().getAssignee())) {
              updateUserTaskAssigneeOperations(zeebeUserTaskInstanceRecord, userTaskForKey);
            }
          } else if (userTaskRecordIntent == COMPLETED) {
            userTaskForKey.setEndDate(zeebeUserTaskInstanceRecord.getDateForTimestamp());
          } else if (userTaskRecordIntent == CANCELED) {
            userTaskForKey.setCanceled(true);
            userTaskForKey.setEndDate(zeebeUserTaskInstanceRecord.getDateForTimestamp());
          } else if (userTaskRecordIntent == ASSIGNED) {
            updateUserTaskAssigneeOperations(zeebeUserTaskInstanceRecord, userTaskForKey);
          }
          userTaskForKey.setAssignee(parseAssignee(zeebeUserTaskInstanceRecord.getValue()));
          userTaskForKey.setDueDate(zeebeUserTaskInstanceRecord.getValue().getDateForDueDate());
          userTaskInstancesByKey.put(recordKey, userTaskForKey);
        });
    userTaskInstancesByKey.values().forEach(this::updateUserTaskDurations);
    instanceToAdd.setFlowNodeInstances(new ArrayList<>(userTaskInstancesByKey.values()));
  }

  private FlowNodeInstanceDto createSkeletonUserTaskInstance(
      final ZeebeUserTaskDataDto userTaskData) {
    final FlowNodeInstanceDto flowNodeInstanceDto = new FlowNodeInstanceDto();
    flowNodeInstanceDto.setFlowNodeInstanceId(String.valueOf(userTaskData.getElementInstanceKey()));
    flowNodeInstanceDto.setFlowNodeId(userTaskData.getElementId());
    flowNodeInstanceDto.setFlowNodeType(FLOW_NODE_TYPE_USER_TASK);
    flowNodeInstanceDto.setProcessInstanceId(String.valueOf(userTaskData.getProcessInstanceKey()));
    flowNodeInstanceDto.setCanceled(false);
    flowNodeInstanceDto.setDefinitionKey(userTaskData.getBpmnProcessId());
    flowNodeInstanceDto.setDefinitionVersion(
        String.valueOf(userTaskData.getProcessDefinitionVersion()));
    flowNodeInstanceDto.setTenantId(userTaskData.getTenantId());
    flowNodeInstanceDto.setUserTaskInstanceId(String.valueOf(userTaskData.getUserTaskKey()));
    return flowNodeInstanceDto;
  }

  private void updateUserTaskDurations(final FlowNodeInstanceDto userTaskToAdd) {
    if (userTaskToAdd.getStartDate() != null && userTaskToAdd.getEndDate() != null) {
      userTaskToAdd.setTotalDurationInMs(
          userTaskToAdd.getStartDate().until(userTaskToAdd.getEndDate(), ChronoUnit.MILLIS));
    }

    // Only recalculate durations of userTasks which are completed or have assignee operations
    if (userTaskToAdd.getStartDate() != null
        && (userTaskToAdd.getEndDate() != null
            || !userTaskToAdd.getAssigneeOperations().isEmpty())) {

      long totalIdleTimeInMs = 0;
      long totalWorkTimeInMs = 0;
      boolean workTimeHasChanged = false;
      boolean idleTimeHasChanged = false;

      if (!userTaskToAdd.getAssigneeOperations().isEmpty()) {
        // Collect all timestamps of unclaim operations, counting the startDate as the first and the
        // endDate as the last unclaim
        final List<OffsetDateTime> allUnclaimTimestamps =
            userTaskToAdd.getAssigneeOperations().stream()
                .filter(
                    operation ->
                        UNCLAIM_OPERATION_TYPE.toString().equals(operation.getOperationType()))
                .map(AssigneeOperationDto::getTimestamp)
                .collect(Collectors.toList());
        allUnclaimTimestamps.add(userTaskToAdd.getStartDate());
        Optional.ofNullable(userTaskToAdd.getEndDate()).ifPresent(allUnclaimTimestamps::add);
        allUnclaimTimestamps.sort(Comparator.naturalOrder());

        // Collect all timestamps of claim operations
        final List<OffsetDateTime> allClaimTimestamps =
            userTaskToAdd.getAssigneeOperations().stream()
                .filter(
                    operation ->
                        CLAIM_OPERATION_TYPE.toString().equals(operation.getOperationType()))
                .map(AssigneeOperationDto::getTimestamp)
                .sorted(Comparator.naturalOrder())
                .toList();

        // Calculate idle time, which is the sum of differences between claim and unclaim timestamp
        // pairs, ie (claim_n -
        // unclaim_n)
        // Note there will always be at least one unclaim (startDate)
        for (int i = 0; i < allUnclaimTimestamps.size() && i < allClaimTimestamps.size(); i++) {
          final OffsetDateTime unclaimDate = allUnclaimTimestamps.get(i);
          final OffsetDateTime claimDate = allClaimTimestamps.get(i);
          totalIdleTimeInMs += Duration.between(unclaimDate, claimDate).toMillis();
          idleTimeHasChanged = true;
        }

        // Calculate work time, which is the sum of differences between unclaim and previous claim
        // timestamp pairs, ie
        // (unclaim_n+1 - claim_n)
        // Note the startDate is the first unclaim, so can be disregarded for this calculation
        for (int i = 0; i < allUnclaimTimestamps.size() - 1 && i < allClaimTimestamps.size(); i++) {
          final OffsetDateTime claimDate = allClaimTimestamps.get(i);
          final OffsetDateTime unclaimDate = allUnclaimTimestamps.get(i + 1);
          totalWorkTimeInMs += Duration.between(claimDate, unclaimDate).toMillis();
          workTimeHasChanged = true;
        }

        // Edge case: task was unclaimed and then completed without claim (== there are 2 more
        // unclaims than claims)
        // --> add time between end and last "real" unclaim as idle time
        if (allUnclaimTimestamps.size() - allClaimTimestamps.size() == 2) {
          final OffsetDateTime lastUnclaim =
              allUnclaimTimestamps.get(allUnclaimTimestamps.size() - 1);
          final OffsetDateTime secondToLastUnclaim =
              allUnclaimTimestamps.get(allUnclaimTimestamps.size() - 2);
          totalIdleTimeInMs += Duration.between(lastUnclaim, secondToLastUnclaim).toMillis();
          idleTimeHasChanged = true;
        }
      }

      // Edge case: no assignee operations exist but task was finished (task was completed or
      // canceled without claim)
      else if (userTaskToAdd.getTotalDurationInMs() != null) {
        if (Boolean.TRUE.equals(userTaskToAdd.getCanceled())) {
          // Task was cancelled --> assumed to have been idle the entire time
          totalIdleTimeInMs = userTaskToAdd.getTotalDurationInMs();
          totalWorkTimeInMs = 0;
        } else {
          // Task was not canceled --> assumed to have been worked on the entire time (presumably
          // programmatically)
          totalIdleTimeInMs = 0;
          totalWorkTimeInMs = userTaskToAdd.getTotalDurationInMs();
        }
        workTimeHasChanged = true;
        idleTimeHasChanged = true;
      }

      // Set work and idle time if they have been calculated. Otherwise, leave fields null.
      if (idleTimeHasChanged) {
        userTaskToAdd.setIdleDurationInMs(totalIdleTimeInMs);
      }
      if (workTimeHasChanged) {
        userTaskToAdd.setWorkDurationInMs(totalWorkTimeInMs);
      }
    }
  }

  private void updateUserTaskAssigneeOperations(
      final ZeebeUserTaskRecordDto zeebeUserTaskRecord, final FlowNodeInstanceDto flowNodeToAdd) {
    final List<AssigneeOperationDto> assigneeOperations = flowNodeToAdd.getAssigneeOperations();
    final AssigneeOperationDto newAssigneeOperation =
        createAssigneeOperationDto(zeebeUserTaskRecord);
    if (!assigneeOperations.contains(newAssigneeOperation)) {
      assigneeOperations.add(newAssigneeOperation);
    }
  }

  private AssigneeOperationDto createAssigneeOperationDto(
      final ZeebeUserTaskRecordDto zeebeUserTaskRecord) {
    final AssigneeOperationDto assigneeOperationDto = new AssigneeOperationDto();
    assigneeOperationDto.setId(String.valueOf(zeebeUserTaskRecord.getKey()));
    assigneeOperationDto.setUserId(parseAssignee(zeebeUserTaskRecord.getValue()));
    assigneeOperationDto.setOperationType(
        StringUtil.isNullOrEmpty(zeebeUserTaskRecord.getValue().getAssignee())
            ? UNCLAIM_OPERATION_TYPE.toString()
            : CLAIM_OPERATION_TYPE.toString());
    assigneeOperationDto.setTimestamp(zeebeUserTaskRecord.getDateForTimestamp());
    return assigneeOperationDto;
  }

  private String parseAssignee(final ZeebeUserTaskDataDto zeebeUserTaskData) {
    return StringUtil.isNullOrEmpty(zeebeUserTaskData.getAssignee())
        ? null
        : zeebeUserTaskData.getAssignee();
  }
}
