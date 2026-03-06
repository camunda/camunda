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
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.ASSIGNED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.CANCELED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.CREATING;

import io.camunda.optimize.dto.optimize.persistence.AssigneeOperationDto;
import io.camunda.optimize.dto.optimize.query.process.FlatUserTaskDto;
import io.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskDataDto;
import io.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskRecordDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.UserTaskWriter;
import io.camunda.optimize.service.importing.DatabaseImportJob;
import io.camunda.optimize.service.importing.DatabaseImportJobExecutor;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.job.FlatUserTaskDatabaseImportJob;
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

public class ZeebeUserTaskImportService implements ImportService<ZeebeUserTaskRecordDto> {

  public static final Set<UserTaskIntent> INTENTS_TO_IMPORT =
      Set.of(CREATING, ASSIGNED, COMPLETED, CANCELED);
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ZeebeUserTaskImportService.class);

  private final DatabaseImportJobExecutor databaseImportJobExecutor;
  private final ConfigurationService configurationService;
  private final UserTaskWriter userTaskWriter;
  private final DatabaseClient databaseClient;
  private final int partitionId;

  public ZeebeUserTaskImportService(
      final ConfigurationService configurationService,
      final UserTaskWriter userTaskWriter,
      final int partitionId,
      final DatabaseClient databaseClient) {
    this.databaseImportJobExecutor =
        new DatabaseImportJobExecutor(getClass().getSimpleName(), configurationService);
    this.configurationService = configurationService;
    this.userTaskWriter = userTaskWriter;
    this.partitionId = partitionId;
    this.databaseClient = databaseClient;
  }

  @Override
  public void executeImport(
      final List<ZeebeUserTaskRecordDto> zeebeRecords, final Runnable importCompleteCallback) {
    if (!zeebeRecords.isEmpty()) {
      final List<FlatUserTaskDto> flatUserTasks =
          filterAndMapZeebeRecordsToFlatUserTasks(zeebeRecords);
      final DatabaseImportJob<FlatUserTaskDto> importJob =
          createDatabaseImportJob(flatUserTasks, importCompleteCallback);
      databaseImportJobExecutor.executeImportJob(importJob);
    }
  }

  /**
   * Creates (but does not execute) a {@link DatabaseImportJob} for the given records.
   *
   * @return an {@link Optional} containing the prepared import job, or empty if there are no
   *     relevant records to import.
   */
  public Optional<DatabaseImportJob<FlatUserTaskDto>> createImportJob(
      final List<ZeebeUserTaskRecordDto> zeebeRecords) {
    if (zeebeRecords.isEmpty()) {
      return Optional.empty();
    }
    final List<FlatUserTaskDto> flatUserTasks = filterAndMapZeebeRecordsToFlatUserTasks(zeebeRecords);
    if (flatUserTasks.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(createDatabaseImportJob(flatUserTasks, () -> {}));
  }

  @Override
  public DatabaseImportJobExecutor getDatabaseImportJobExecutor() {
    return databaseImportJobExecutor;
  }

  private List<FlatUserTaskDto> filterAndMapZeebeRecordsToFlatUserTasks(
      final List<ZeebeUserTaskRecordDto> userTaskRecords) {
    final List<FlatUserTaskDto> flatUserTasks =
        userTaskRecords.stream()
            .filter(zeebeRecord -> INTENTS_TO_IMPORT.contains(zeebeRecord.getIntent()))
            .collect(
                Collectors.groupingBy(
                    zeebeRecord -> zeebeRecord.getValue().getProcessInstanceKey()))
            .values()
            .stream()
            .flatMap(records -> createFlatUserTasksForInstance(records).stream())
            .collect(Collectors.toList());
    LOG.debug(
        "Processing {} fetched zeebe userTask records, of which {} are relevant to Optimize and will be imported.",
        userTaskRecords.size(),
        flatUserTasks.size());
    return flatUserTasks;
  }

  private List<FlatUserTaskDto> createFlatUserTasksForInstance(
      final List<ZeebeUserTaskRecordDto> userTaskRecordsForInstance) {
    final ZeebeUserTaskDataDto firstRecordValue = userTaskRecordsForInstance.get(0).getValue();
    final String processDefinitionKey = firstRecordValue.getBpmnProcessId();
    final String processDefinitionVersion =
        String.valueOf(firstRecordValue.getProcessDefinitionVersion());
    final String processDefinitionId = String.valueOf(firstRecordValue.getProcessDefinitionKey());
    final String processInstanceId = String.valueOf(firstRecordValue.getProcessInstanceKey());

    // Track intermediate state per user task record key
    final Map<Long, FlatUserTaskDto> userTasksByKey = new HashMap<>();
    // Track assignee operations per key for duration calculations
    final Map<Long, List<AssigneeOperationDto>> assigneeOpsByKey = new HashMap<>();

    userTaskRecordsForInstance.forEach(
        zeebeRecord -> {
          final long recordKey = zeebeRecord.getKey();
          final ZeebeUserTaskDataDto recordValue = zeebeRecord.getValue();
          final FlatUserTaskDto userTaskDto =
              userTasksByKey.computeIfAbsent(
                  recordKey,
                  k -> createSkeletonFlatUserTask(processDefinitionKey, processDefinitionVersion,
                      processDefinitionId, processInstanceId, recordValue));
          final List<AssigneeOperationDto> assigneeOps =
              assigneeOpsByKey.computeIfAbsent(recordKey, k -> new ArrayList<>());

          final UserTaskIntent intent = zeebeRecord.getIntent();
          if (intent == CREATING) {
            userTaskDto.setStartDate(zeebeRecord.getDateForTimestamp());
            userTaskDto.setNew(true);
            if (!StringUtil.isNullOrEmpty(recordValue.getAssignee())) {
              addAssigneeOperation(zeebeRecord, assigneeOps);
            }
          } else if (intent == COMPLETED) {
            userTaskDto.setEndDate(zeebeRecord.getDateForTimestamp());
          } else if (intent == CANCELED) {
            userTaskDto.setCanceled(true);
            userTaskDto.setEndDate(zeebeRecord.getDateForTimestamp());
          } else if (intent == ASSIGNED) {
            addAssigneeOperation(zeebeRecord, assigneeOps);
          }
          userTaskDto.setAssignee(parseAssignee(recordValue));
          userTaskDto.setDueDate(recordValue.getDateForDueDate());
        });

    // Post-process: calculate durations
    userTasksByKey.forEach(
        (key, dto) -> {
          updateTotalDuration(dto);
          updateIdleAndWorkDuration(dto, assigneeOpsByKey.getOrDefault(key, List.of()));
        });

    return new ArrayList<>(userTasksByKey.values());
  }

  private FlatUserTaskDto createSkeletonFlatUserTask(
      final String processDefinitionKey,
      final String processDefinitionVersion,
      final String processDefinitionId,
      final String processInstanceId,
      final ZeebeUserTaskDataDto userTaskData) {
    final FlatUserTaskDto dto = new FlatUserTaskDto();
    dto.setProcessDefinitionKey(processDefinitionKey);
    dto.setProcessDefinitionVersion(processDefinitionVersion);
    dto.setProcessDefinitionId(processDefinitionId);
    dto.setProcessInstanceId(processInstanceId);
    dto.setFlowNodeInstanceId(String.valueOf(userTaskData.getElementInstanceKey()));
    dto.setFlowNodeId(userTaskData.getElementId());
    dto.setUserTaskInstanceId(String.valueOf(userTaskData.getUserTaskKey()));
    dto.setDefinitionKey(userTaskData.getBpmnProcessId());
    dto.setDefinitionVersion(String.valueOf(userTaskData.getProcessDefinitionVersion()));
    dto.setTenantId(userTaskData.getTenantId());
    dto.setCanceled(false);
    dto.setPartition(partitionId);
    return dto;
  }

  private void updateTotalDuration(final FlatUserTaskDto dto) {
    if (dto.getStartDate() != null && dto.getEndDate() != null) {
      dto.setTotalDurationInMs(
          dto.getStartDate().until(dto.getEndDate(), ChronoUnit.MILLIS));
    }
  }

  private void updateIdleAndWorkDuration(
      final FlatUserTaskDto dto, final List<AssigneeOperationDto> assigneeOps) {
    if (dto.getStartDate() == null) {
      return;
    }
    if (dto.getEndDate() == null && assigneeOps.isEmpty()) {
      return;
    }

    long totalIdleTimeInMs = 0;
    long totalWorkTimeInMs = 0;
    boolean workTimeHasChanged = false;
    boolean idleTimeHasChanged = false;

    if (!assigneeOps.isEmpty()) {
      final List<OffsetDateTime> allUnclaimTimestamps =
          assigneeOps.stream()
              .filter(op -> UNCLAIM_OPERATION_TYPE.toString().equals(op.getOperationType()))
              .map(AssigneeOperationDto::getTimestamp)
              .collect(Collectors.toList());
      allUnclaimTimestamps.add(dto.getStartDate());
      Optional.ofNullable(dto.getEndDate()).ifPresent(allUnclaimTimestamps::add);
      allUnclaimTimestamps.sort(Comparator.naturalOrder());

      final List<OffsetDateTime> allClaimTimestamps =
          assigneeOps.stream()
              .filter(op -> CLAIM_OPERATION_TYPE.toString().equals(op.getOperationType()))
              .map(AssigneeOperationDto::getTimestamp)
              .sorted(Comparator.naturalOrder())
              .toList();

      for (int i = 0; i < allUnclaimTimestamps.size() && i < allClaimTimestamps.size(); i++) {
        totalIdleTimeInMs +=
            Duration.between(allUnclaimTimestamps.get(i), allClaimTimestamps.get(i)).toMillis();
        idleTimeHasChanged = true;
      }

      for (int i = 0; i < allUnclaimTimestamps.size() - 1 && i < allClaimTimestamps.size(); i++) {
        totalWorkTimeInMs +=
            Duration.between(allClaimTimestamps.get(i), allUnclaimTimestamps.get(i + 1)).toMillis();
        workTimeHasChanged = true;
      }

      if (allUnclaimTimestamps.size() - allClaimTimestamps.size() == 2) {
        final OffsetDateTime lastUnclaim =
            allUnclaimTimestamps.get(allUnclaimTimestamps.size() - 1);
        final OffsetDateTime secondToLastUnclaim =
            allUnclaimTimestamps.get(allUnclaimTimestamps.size() - 2);
        totalIdleTimeInMs += Duration.between(lastUnclaim, secondToLastUnclaim).toMillis();
        idleTimeHasChanged = true;
      }
    } else if (dto.getTotalDurationInMs() != null) {
      if (Boolean.TRUE.equals(dto.getCanceled())) {
        totalIdleTimeInMs = dto.getTotalDurationInMs();
        totalWorkTimeInMs = 0;
      } else {
        totalIdleTimeInMs = 0;
        totalWorkTimeInMs = dto.getTotalDurationInMs();
      }
      workTimeHasChanged = true;
      idleTimeHasChanged = true;
    }

    if (idleTimeHasChanged) {
      dto.setIdleDurationInMs(totalIdleTimeInMs);
    }
    if (workTimeHasChanged) {
      dto.setWorkDurationInMs(totalWorkTimeInMs);
    }
  }

  private void addAssigneeOperation(
      final ZeebeUserTaskRecordDto zeebeRecord, final List<AssigneeOperationDto> assigneeOps) {
    final AssigneeOperationDto op = new AssigneeOperationDto();
    op.setId(String.valueOf(zeebeRecord.getKey()));
    op.setUserId(parseAssignee(zeebeRecord.getValue()));
    op.setOperationType(
        StringUtil.isNullOrEmpty(zeebeRecord.getValue().getAssignee())
            ? UNCLAIM_OPERATION_TYPE.toString()
            : CLAIM_OPERATION_TYPE.toString());
    op.setTimestamp(zeebeRecord.getDateForTimestamp());
    if (!assigneeOps.contains(op)) {
      assigneeOps.add(op);
    }
  }

  private String parseAssignee(final ZeebeUserTaskDataDto zeebeUserTaskData) {
    return StringUtil.isNullOrEmpty(zeebeUserTaskData.getAssignee())
        ? null
        : zeebeUserTaskData.getAssignee();
  }

  private DatabaseImportJob<FlatUserTaskDto> createDatabaseImportJob(
      final List<FlatUserTaskDto> flatUserTasks, final Runnable importCompleteCallback) {
    final FlatUserTaskDatabaseImportJob importJob =
        new FlatUserTaskDatabaseImportJob(
            userTaskWriter, configurationService, importCompleteCallback, databaseClient);
    importJob.setEntitiesToImport(flatUserTasks);
    return importJob;
  }
}

