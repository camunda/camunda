/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.engine.processing.streamprocessor.FollowUpEventMetadata;
import io.camunda.zeebe.engine.processing.streamprocessor.FollowUpEventMetadata.Builder;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.KeyValidator;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A state writer that uses the event applier, to alter the state for each written event.
 *
 * <p>Note that it does not write events to the stream itself, but it delegates this to the {@link
 * ProcessingResultBuilder}.
 *
 * <p>Note that it does not change the state itself, but delegates this to the {@link EventApplier}.
 */
final class ResultBuilderBackedEventApplyingStateWriter extends AbstractResultBuilderBackedWriter
    implements StateWriter {

  private final EventApplier eventApplier;
  private KeyValidator keyValidator;

  public ResultBuilderBackedEventApplyingStateWriter(
      final Supplier<ProcessingResultBuilder> resultBuilderSupplier,
      final EventApplier eventApplier) {
    super(resultBuilderSupplier);
    this.eventApplier = eventApplier;
  }

  public void setKeyValidator(final KeyValidator keyValidator) {
    this.keyValidator = keyValidator;
  }

  @Override
  public void appendFollowUpEvent(final long key, final Intent intent, final RecordValue value) {
    // key is validated in appendFollowUpEvent
    final int latestVersion = eventApplier.getLatestVersion(intent);
    appendFollowUpEvent(key, intent, value, latestVersion);
  }

  @Override
  public void appendFollowUpEvent(
      final long key, final Intent intent, final RecordValue value, final int recordVersion) {
    // key is validated in appendFollowUpEvent
    appendFollowUpEvent(key, intent, value, m -> m.recordVersion(recordVersion));
  }

  @Override
  public void appendFollowUpEvent(
      final long key,
      final Intent intent,
      final RecordValue value,
      final FollowUpEventMetadata metadata) {
    validateKey(key);
    final int recordVersion =
        metadata.getRecordVersion() == FollowUpEventMetadata.VERSION_NOT_SET
            ? eventApplier.getLatestVersion(intent)
            : metadata.getRecordVersion();

    final var recordMetadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .intent(intent)
            .recordVersion(recordVersion)
            .rejectionType(RejectionType.NULL_VAL)
            .rejectionReason("")
            .authorization(new AuthInfo().setClaims(metadata.getClaims()))
            .operationReference(metadata.getOperationReference())
            .batchOperationReference(metadata.getBatchOperationReference());

    resultBuilder().appendRecord(key, value, recordMetadata);
    eventApplier.applyState(key, intent, value, recordVersion);
  }

  @Override
  public void appendFollowUpEvent(
      final long key,
      final Intent intent,
      final RecordValue value,
      final Consumer<Builder> builderConsumer) {
    validateKey(key);
    StateWriter.super.appendFollowUpEvent(key, intent, value, builderConsumer);
  }

  @Override
  public boolean canWriteEventOfLength(final int eventLength) {
    return resultBuilder().canWriteEventOfLength(eventLength);
  }

  private void validateKey(final long key) {
    if (keyValidator != null) {
      keyValidator.validateKey(key);
    }
  }
}
