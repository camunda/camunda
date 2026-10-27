/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.scheduler;

import com.google.common.base.Strings;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationError;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationInitializationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationPartitionLifecycleRecord;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationErrorType;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for creating and appending batch operation commands to a {@link TaskResultBuilder}.
 */
public class BatchOperationCommands {
  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationCommands.class);
  private final int partitionId;

  public BatchOperationCommands(final int partitionId) {
    this.partitionId = partitionId;
  }

  public void appendInitializationCommand(
      final TaskResultBuilder builder,
      final long batchOperationKey,
      final String searchResultCursor,
      final int pageSize) {
    final var command =
        new BatchOperationInitializationRecord()
            .setBatchOperationKey(batchOperationKey)
            .setSearchResultCursor(Strings.nullToEmpty(searchResultCursor))
            .setSearchQueryPageSize(pageSize);

    LOG.trace("Appending batch operation {} initializing command", batchOperationKey);
    builder.appendCommandRecord(
        batchOperationKey,
        BatchOperationIntent.INITIALIZE,
        command,
        FollowUpCommandMetadata.of(b -> b.batchOperationReference(batchOperationKey)));
  }

  public void appendFinishInitializationCommand(
      final TaskResultBuilder builder, final long batchOperationKey) {
    final var command =
        new BatchOperationInitializationRecord().setBatchOperationKey(batchOperationKey);
    LOG.trace("Appending batch operation {} initializing finished command", batchOperationKey);

    builder.appendCommandRecord(
        batchOperationKey,
        BatchOperationIntent.FINISH_INITIALIZATION,
        command,
        FollowUpCommandMetadata.of(b -> b.batchOperationReference(batchOperationKey)));
  }

  public void appendExecutionCommand(
      final TaskResultBuilder builder, final long batchOperationKey) {
    final var command = new BatchOperationExecutionRecord().setBatchOperationKey(batchOperationKey);

    LOG.trace("Appending batch operation execution {}", batchOperationKey);
    builder.appendCommandRecord(
        batchOperationKey,
        BatchOperationExecutionIntent.EXECUTE,
        command,
        FollowUpCommandMetadata.of(b -> b.batchOperationReference(batchOperationKey)));
  }

  public void appendFailureCommand(
      final TaskResultBuilder builder,
      final long batchOperationKey,
      final String message,
      final BatchOperationErrorType errorType) {
    final var error =
        new BatchOperationError()
            .setType(errorType)
            .setPartitionId(partitionId)
            .setMessage(message);
    final var command =
        new BatchOperationPartitionLifecycleRecord()
            .setBatchOperationKey(batchOperationKey)
            .setError(error);

    LOG.trace("Appending batch operation {} failed event", batchOperationKey);
    builder.appendCommandRecord(
        batchOperationKey,
        BatchOperationIntent.FAIL,
        command,
        FollowUpCommandMetadata.of(b -> b.batchOperationReference(batchOperationKey)));
  }
}
