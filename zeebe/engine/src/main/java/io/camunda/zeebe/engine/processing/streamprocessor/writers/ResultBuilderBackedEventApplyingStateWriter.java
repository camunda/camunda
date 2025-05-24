/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.TriggeringRecordMetadata;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
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

  public ResultBuilderBackedEventApplyingStateWriter(
      final Supplier<ProcessingResultBuilder> resultBuilderSupplier,
      final EventApplier eventApplier) {
    super(resultBuilderSupplier);
    this.eventApplier = eventApplier;
  }

  @Override
  public void appendFollowUpEvent(final long key, final Intent intent, final RecordValue value) {
    appendFollowUpEvent(key, intent, value, TriggeringRecordMetadata.empty());
  }

  @Override
  public void appendFollowUpEvent(
      final long key,
      final Intent intent,
      final RecordValue value,
      final TriggeringRecordMetadata metadata) {
    final int latestVersion = eventApplier.getLatestVersion(intent);
    final var eventMetadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .intent(intent)
            .recordVersion(latestVersion)
            .rejectionType(RejectionType.NULL_VAL)
            .rejectionReason("")
            .operationReference(metadata.getOperationReference());
    resultBuilder().appendRecord(key, value, eventMetadata);
    eventApplier.applyState(key, intent, value, latestVersion, metadata);
  }

  @Override
  public boolean canWriteEventOfLength(final int eventLength) {
    return resultBuilder().canWriteEventOfLength(eventLength);
  }
}
