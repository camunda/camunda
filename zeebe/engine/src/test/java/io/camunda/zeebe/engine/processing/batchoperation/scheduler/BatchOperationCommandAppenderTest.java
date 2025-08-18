/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationInitializationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationPartitionLifecycleRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationErrorType;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BatchOperationCommandAppenderTest {

  private static final int PARTITION_ID = 1;
  private static final long BATCH_OPERATION_KEY = 123L;

  private BatchOperationCommandAppender appender;
  private TaskResultBuilder mockBuilder;

  @BeforeEach
  void setUp() {
    appender = new BatchOperationCommandAppender(PARTITION_ID);
    mockBuilder = mock(TaskResultBuilder.class);
  }

  @Test
  void shouldAppendInitializationCommandWithCursor() {
    // given
    final String cursor = "test-cursor";
    final int pageSize = 50;

    // when
    appender.appendInitializationCommand(mockBuilder, BATCH_OPERATION_KEY, cursor, pageSize);

    // then
    final var commandCaptor = ArgumentCaptor.forClass(BatchOperationInitializationRecord.class);
    verify(mockBuilder)
        .appendCommandRecord(
            eq(BATCH_OPERATION_KEY),
            eq(BatchOperationIntent.INITIALIZE),
            commandCaptor.capture(),
            any(FollowUpCommandMetadata.class));

    final var command = commandCaptor.getValue();
    assertThat(command.getBatchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
    assertThat(command.getSearchResultCursor()).isEqualTo(cursor);
    assertThat(command.getSearchQueryPageSize()).isEqualTo(pageSize);
  }

  @Test
  void shouldAppendInitializationCommandWithNullCursor() {
    // given
    final String cursor = null;
    final int pageSize = 100;

    // when
    appender.appendInitializationCommand(mockBuilder, BATCH_OPERATION_KEY, cursor, pageSize);

    // then
    final var commandCaptor = ArgumentCaptor.forClass(BatchOperationInitializationRecord.class);
    verify(mockBuilder)
        .appendCommandRecord(
            eq(BATCH_OPERATION_KEY),
            eq(BatchOperationIntent.INITIALIZE),
            commandCaptor.capture(),
            any(FollowUpCommandMetadata.class));

    final var command = commandCaptor.getValue();
    assertThat(command.getBatchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
    assertThat(command.getSearchResultCursor()).isEmpty(); // null converted to empty string
    assertThat(command.getSearchQueryPageSize()).isEqualTo(pageSize);
  }

  @Test
  void shouldAppendFinishInitializationCommand() {
    // when
    appender.appendFinishInitializationCommand(mockBuilder, BATCH_OPERATION_KEY);

    // then
    final var commandCaptor = ArgumentCaptor.forClass(BatchOperationInitializationRecord.class);
    verify(mockBuilder)
        .appendCommandRecord(
            eq(BATCH_OPERATION_KEY),
            eq(BatchOperationIntent.FINISH_INITIALIZATION),
            commandCaptor.capture(),
            any(FollowUpCommandMetadata.class));

    final var command = commandCaptor.getValue();
    assertThat(command.getBatchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
  }

  @Test
  void shouldAppendExecutionCommand() {
    // when
    appender.appendExecutionCommand(mockBuilder, BATCH_OPERATION_KEY);

    // then
    final var commandCaptor = ArgumentCaptor.forClass(BatchOperationExecutionRecord.class);
    verify(mockBuilder)
        .appendCommandRecord(
            eq(BATCH_OPERATION_KEY),
            eq(BatchOperationExecutionIntent.EXECUTE),
            commandCaptor.capture(),
            any(FollowUpCommandMetadata.class));

    final var command = commandCaptor.getValue();
    assertThat(command.getBatchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
  }

  @Test
  void shouldAppendFailureCommand() {
    // given
    final String errorMessage = "Test error message";
    final BatchOperationErrorType errorType = BatchOperationErrorType.QUERY_FAILED;

    // when
    appender.appendFailureCommand(mockBuilder, BATCH_OPERATION_KEY, errorMessage, errorType);

    // then
    final var commandCaptor = ArgumentCaptor.forClass(BatchOperationPartitionLifecycleRecord.class);
    verify(mockBuilder)
        .appendCommandRecord(
            eq(BATCH_OPERATION_KEY),
            eq(BatchOperationIntent.FAIL),
            commandCaptor.capture(),
            any(FollowUpCommandMetadata.class));

    final var command = commandCaptor.getValue();
    final var error = command.getError();
    assertThat(command.getBatchOperationKey()).isEqualTo(BATCH_OPERATION_KEY);
    assertThat(error.getMessage()).isEqualTo(errorMessage);
    assertThat(error.getType()).isEqualTo(errorType);
    assertThat(error.getPartitionId()).isEqualTo(PARTITION_ID);
  }

  @Test
  void shouldAppendFailureCommandWithDifferentErrorTypes() {
    // given
    final String errorMessage = "Another error";
    final BatchOperationErrorType errorType = BatchOperationErrorType.RESULT_BUFFER_SIZE_EXCEEDED;

    // when
    appender.appendFailureCommand(mockBuilder, BATCH_OPERATION_KEY, errorMessage, errorType);

    // then
    final var commandCaptor = ArgumentCaptor.forClass(BatchOperationPartitionLifecycleRecord.class);
    verify(mockBuilder)
        .appendCommandRecord(
            eq(BATCH_OPERATION_KEY),
            eq(BatchOperationIntent.FAIL),
            commandCaptor.capture(),
            any(FollowUpCommandMetadata.class));

    final var command = commandCaptor.getValue();
    final var error = command.getError();
    assertThat(error.getType()).isEqualTo(errorType);
    assertThat(error.getMessage()).isEqualTo(errorMessage);
  }

  @Test
  void shouldUseCorrectPartitionIdInFailureCommand() {
    // given
    final int differentPartitionId = 5;
    final var differentAppender = new BatchOperationCommandAppender(differentPartitionId);
    final String errorMessage = "Partition specific error";
    final BatchOperationErrorType errorType = BatchOperationErrorType.UNKNOWN;

    // when
    differentAppender.appendFailureCommand(
        mockBuilder, BATCH_OPERATION_KEY, errorMessage, errorType);

    // then
    final var commandCaptor = ArgumentCaptor.forClass(BatchOperationPartitionLifecycleRecord.class);
    verify(mockBuilder)
        .appendCommandRecord(
            eq(BATCH_OPERATION_KEY),
            eq(BatchOperationIntent.FAIL),
            commandCaptor.capture(),
            any(FollowUpCommandMetadata.class));

    final var command = commandCaptor.getValue();
    final var error = command.getError();
    assertThat(error.getPartitionId()).isEqualTo(differentPartitionId);
  }
}
