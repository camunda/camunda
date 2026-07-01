/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor.writers;

import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class ResultBuilderBackedTypedCommandWriter extends AbstractResultBuilderBackedWriter
    implements TypedCommandWriter {

  // Lookup of the cluster's currently active ECV ordinal. Wired by the engine after processing
  // state is available. Defaults to MAX so all commands are admissible for tests that don't
  // wire ECV.
  private IntSupplier activeOrdinalSupplier = () -> Integer.MAX_VALUE;

  ResultBuilderBackedTypedCommandWriter(
      final Supplier<ProcessingResultBuilder> resultBuilderSupplier) {
    super(resultBuilderSupplier);
  }

  /**
   * Wire the lookup of the cluster's currently active ECV ordinal. Engine command writes are gated
   * against this — a processor that tries to emit a follow-up command for a capability the cluster
   * has not yet activated will throw {@link IllegalStateException}, by design: it is a processor
   * bug for code to reach a write site that should not yet run.
   */
  void setActiveClusterVersionProvider(final IntSupplier activeOrdinalSupplier) {
    this.activeOrdinalSupplier = Objects.requireNonNull(activeOrdinalSupplier);
  }

  @Override
  public void appendNewCommand(final Intent intent, final RecordValue value) {
    appendRecord(-1, intent, value);
  }

  @Override
  public void appendFollowUpCommand(final long key, final Intent intent, final RecordValue value) {
    appendRecord(key, intent, value);
  }

  @Override
  public void appendFollowUpCommand(
      final long key,
      final Intent intent,
      final RecordValue value,
      final FollowUpCommandMetadata metadata) {
    appendRecord(key, intent, value, metadata);
  }

  @Override
  public boolean canWriteCommandOfLength(final int commandLength) {
    return resultBuilder().canWriteEventOfLength(commandLength);
  }

  private void appendRecord(final long key, final Intent intent, final RecordValue value) {
    appendRecord(key, intent, value, FollowUpCommandMetadata.empty());
  }

  private void appendRecord(
      final long key,
      final Intent intent,
      final RecordValue value,
      final FollowUpCommandMetadata commandMetadata) {
    enforceClusterVersionGate(intent);

    final var metadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .intent(intent)
            .rejectionType(RejectionType.NULL_VAL)
            .rejectionReason("")
            .operationReference(commandMetadata.operationReference())
            .batchOperationReference(commandMetadata.batchOperationReference())
            .authorization(commandMetadata.authInfo());

    resultBuilder().appendRecord(key, value, metadata);
  }

  private void enforceClusterVersionGate(final Intent intent) {
    // Single HashMap.get from the precomputed catalog index. For every non-gated command
    // (the common case), we return immediately without reading the active ECV at all.
    final int required = ClusterVersionCatalog.requiredOrdinalForCommandOrUngated(intent);
    if (required < 0) {
      return;
    }
    final int activeOrdinal = activeOrdinalSupplier.getAsInt();
    if (activeOrdinal >= required) {
      return;
    }
    throw new IllegalStateException(
        String.format(
            "Refusing to emit command %s: requires cluster version ordinal %d but current "
                + "active ordinal is %d. This indicates a processor bug — code reached a write "
                + "site that should not yet run.",
            intent, required, activeOrdinal));
  }
}
