/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.HistoryCleanupMapper.CleanupHistoryDto;
import io.camunda.db.rdbms.sql.ProcessBasedHistoryCleanupMapper;
import io.camunda.db.rdbms.sql.UserTaskMapper;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel;
import io.camunda.db.rdbms.write.domain.UserTaskDbModel.UserTaskState;
import io.camunda.db.rdbms.write.domain.UserTaskMigrationDbModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.time.OffsetDateTime;

public class UserTaskWriter {

  private final ExecutionQueue executionQueue;
  private final UserTaskMapper mapper;

  public UserTaskWriter(final ExecutionQueue executionQueue, final UserTaskMapper mapper) {
    this.executionQueue = executionQueue;
    this.mapper = mapper;
  }

  public void create(final UserTaskDbModel userTaskDbModel) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.USER_TASK,
            WriteStatementType.INSERT,
            userTaskDbModel.userTaskKey(),
            "io.camunda.db.rdbms.sql.UserTaskMapper.insert",
            userTaskDbModel));
    if (userTaskDbModel.candidateUsers() != null && !userTaskDbModel.candidateUsers().isEmpty()) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.USER_TASK,
              WriteStatementType.INSERT,
              userTaskDbModel.userTaskKey(),
              "io.camunda.db.rdbms.sql.UserTaskMapper.insertCandidateUsers",
              userTaskDbModel));
    }
    if (userTaskDbModel.candidateGroups() != null && !userTaskDbModel.candidateGroups().isEmpty()) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.USER_TASK,
              WriteStatementType.INSERT,
              userTaskDbModel.userTaskKey(),
              "io.camunda.db.rdbms.sql.UserTaskMapper.insertCandidateGroups",
              userTaskDbModel));
    }
  }

  public void update(final UserTaskDbModel userTaskDbModel) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.USER_TASK,
            WriteStatementType.UPDATE,
            userTaskDbModel.userTaskKey(),
            "io.camunda.db.rdbms.sql.UserTaskMapper.update",
            userTaskDbModel));
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.USER_TASK,
            WriteStatementType.DELETE,
            userTaskDbModel.userTaskKey(),
            "io.camunda.db.rdbms.sql.UserTaskMapper.deleteCandidateUsers",
            userTaskDbModel.userTaskKey()));
    if (userTaskDbModel.candidateUsers() != null && !userTaskDbModel.candidateUsers().isEmpty()) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.USER_TASK,
              WriteStatementType.INSERT,
              userTaskDbModel.userTaskKey(),
              "io.camunda.db.rdbms.sql.UserTaskMapper.insertCandidateUsers",
              userTaskDbModel));
    }
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.USER_TASK,
            WriteStatementType.DELETE,
            userTaskDbModel.userTaskKey(),
            "io.camunda.db.rdbms.sql.UserTaskMapper.deleteCandidateGroups",
            userTaskDbModel.userTaskKey()));
    if (userTaskDbModel.candidateGroups() != null && !userTaskDbModel.candidateGroups().isEmpty()) {
      executionQueue.executeInQueue(
          new QueueItem(
              ContextType.USER_TASK,
              WriteStatementType.INSERT,
              userTaskDbModel.userTaskKey(),
              "io.camunda.db.rdbms.sql.UserTaskMapper.insertCandidateGroups",
              userTaskDbModel));
    }
  }

  public void updateState(final long userTaskKey, final UserTaskState state) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.USER_TASK,
            WriteStatementType.UPDATE,
            userTaskKey,
            "io.camunda.db.rdbms.sql.UserTaskMapper.updateState",
            new UserTaskDbModel.Builder().userTaskKey(userTaskKey).state(state).build()));
  }

  public void scheduleForHistoryCleanup(
      final Long processInstanceKey, final OffsetDateTime historyCleanupDate) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.USER_TASK,
            WriteStatementType.UPDATE,
            processInstanceKey,
            "io.camunda.db.rdbms.sql.UserTaskMapper.updateHistoryCleanupDate",
            new ProcessBasedHistoryCleanupMapper.UpdateHistoryCleanupDateDto.Builder()
                .processInstanceKey(processInstanceKey)
                .historyCleanupDate(historyCleanupDate)
                .build()));
  }

  public void migrateToProcess(final UserTaskMigrationDbModel model) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.USER_TASK,
            WriteStatementType.UPDATE,
            model.userTaskKey(),
            "io.camunda.db.rdbms.sql.UserTaskMapper.migrateToProcess",
            model));
  }

  public int cleanupHistory(
      final int partitionId, final OffsetDateTime cleanupDate, final int rowsToRemove) {
    return mapper.cleanupHistory(
        new CleanupHistoryDto.Builder()
            .partitionId(partitionId)
            .cleanupDate(cleanupDate)
            .limit(rowsToRemove)
            .build());
  }
}
