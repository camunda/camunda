/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.partitioning.PartitionGetAccess;
import io.camunda.zeebe.engine.state.ScheduledTaskDbState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.impl.records.RecordBatchEntry;
import io.camunda.zeebe.util.Either;
import java.util.Optional;
import org.slf4j.Logger;

class ZeebePartitionGetAccess implements PartitionGetAccess {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final ConcurrencyControl concurrencyControl;
  private final int partitionId;
  private final PartitionGetControl getControl;
  private ScheduledTaskDbState dbState;

  ZeebePartitionGetAccess(
      final ConcurrencyControl concurrencyControl,
      final int partitionId,
      final PartitionGetControl getControl) {
    this.concurrencyControl = requireNonNull(concurrencyControl);
    this.partitionId = partitionId;
    this.getControl = requireNonNull(getControl);
  }

  @Override
  public Optional<PartitionGetAccess> forPartition(final int partitionId) {
    if (this.partitionId == partitionId) {
      return Optional.of(this);
    } else {
      return Optional.empty();
    }
  }

  @Override
  public ActorFuture<Record<? extends UnifiedRecordValue>> getEntity(
      final long key, final RecordMetadata metadata) {
    final ActorFuture<Record<? extends UnifiedRecordValue>> completed =
        concurrencyControl.createFuture();

    concurrencyControl.run(
        () -> {
          try {
            final var processingState = getProcessingState();
            final var entity =
                switch (metadata.getValueType()) {
                  case USER_TASK -> processingState.getUserTaskState().getUserTask(key);
                    // todo: add more cases
                  default ->
                      throw new IllegalStateException(
                          "Get '%s' not implemented".formatted(metadata.getValueType()));
                };

            final var intent =
                switch (metadata.getValueType()) {
                  case USER_TASK ->
                      processingState.getUserTaskState().getLifecycleState(key).toIntent();
                  default ->
                      throw new IllegalStateException(
                          "Get '%s' not implemented".formatted(metadata.getValueType()));
                };

            metadata.intent(intent);

            completed.complete(
                new CopiedRecord<>(entity, metadata, key, partitionId, -1L, -1L, -1L));
          } catch (final Exception e) {
            completed.completeExceptionally(e);
          }
        });

    return completed;
  }

  private ScheduledTaskState getProcessingState() {
    if (dbState == null) {
      final var transientMessageSubscriptionState = new TransientPendingSubscriptionState();
      final var transientProcessMessageSubscriptionState = new TransientPendingSubscriptionState();
      final var zeebeDb = getControl.getZeebeDb();
      final var zeebeDbContext = zeebeDb.createContext();

      // todo: create a new GetDbState class that gives read only access like the scheduled task
      //  db state
      dbState =
          new ScheduledTaskDbState(
              zeebeDb,
              zeebeDbContext,
              1,
              transientMessageSubscriptionState,
              transientProcessMessageSubscriptionState);
    }

    return dbState;
  }

  private static Either<WriteFailure, Long> tryWriteErrorEvent(
      final LogStreamWriter writer, final long processInstanceKey) {
    final var errorRecord = new ErrorRecord();
    errorRecord.initErrorRecord(new Exception("Instance was banned from outside."), -1);
    errorRecord.setProcessInstanceKey(processInstanceKey);

    final var recordMetadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.ERROR)
            .intent(ErrorIntent.CREATED)
            .recordVersion(RecordMetadata.DEFAULT_RECORD_VERSION)
            .rejectionType(RejectionType.NULL_VAL)
            .rejectionReason("");
    final var entry =
        RecordBatchEntry.createEntry(processInstanceKey, recordMetadata, -1, errorRecord);
    return writer.tryWrite(WriteContext.internal(), entry);
  }
}
