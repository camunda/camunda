/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secretreference;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.SecretReferenceState;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.ArrayList;
import java.util.List;

@ExcludeAuthorizationCheck
public final class SecretReferenceResolutionFailProcessor
    implements TypedRecordProcessor<SecretReferenceRecord> {

  private static final int MAX_BATCH_SIZE = 100;

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final KeyGenerator keyGenerator;
  private final SecretReferenceState secretReferenceState;

  public SecretReferenceResolutionFailProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final SecretReferenceState secretReferenceState) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    this.keyGenerator = keyGenerator;
    this.secretReferenceState = secretReferenceState;
  }

  @Override
  public void processRecord(final TypedRecord<SecretReferenceRecord> record) {
    final var value = record.getValue();
    final var storeId = value.getStoreId();
    final var secretReference = value.getSecretReference();

    // Rejecting a redundant RESOLUTION_FAIL cannot strand waiting jobs. The pending marker is only
    // removed by the RESOLUTION_COMPLETED and RESOLUTION_FAILED events, and each of them is
    // appended atomically with the command that drains the jobs waiting on the reference
    // (BATCH_REACTIVATE_JOBS and BATCH_CREATE_INCIDENTS respectively). So a reference that is no
    // longer pending while jobs are still waiting already has a drain chain committed on the log.
    if (!secretReferenceState.isPending(storeId, secretReference)) {
      rejectionWriter.appendRejection(
          record,
          RejectionType.NOT_FOUND,
          "Expected to fail resolution for secret reference '%s' in store '%s', but no such secret reference is pending."
              .formatted(secretReference, storeId));
      return;
    }

    stateWriter.appendFollowUpEvent(
        record.getKey(), SecretReferenceIntent.RESOLUTION_FAILED, value);

    final List<Long> nextBatch = buildBatch(storeId, secretReference);

    if (!nextBatch.isEmpty()) {
      final var nextRecord =
          new SecretReferenceRecord().setStoreId(storeId).setSecretReference(secretReference);
      for (final long jobKey : nextBatch) {
        nextRecord.addJobKey(jobKey);
      }
      commandWriter.appendFollowUpCommand(
          keyGenerator.nextKey(), SecretReferenceIntent.BATCH_CREATE_INCIDENTS, nextRecord);
    }
  }

  private List<Long> buildBatch(final String storeId, final String secretReference) {
    final List<Long> batch = new ArrayList<>();
    // no filtering on other pending references: a failed secret never resolves, so every waiting
    // job is doomed and must get its incident now instead of waiting for its other references
    // every batch is rebuilt from the live waiting index, so a job that starts waiting on this
    // reference after the failure is swept into a later batch of the same chain. It is incidented
    // before the scheduler retries the reference that its own RESOLUTION_REQUESTED left pending.
    secretReferenceState.visitJobsBySecretReference(
        storeId,
        secretReference,
        jobKey -> {
          batch.add(jobKey);
          return batch.size() < MAX_BATCH_SIZE;
        });
    return batch;
  }
}
