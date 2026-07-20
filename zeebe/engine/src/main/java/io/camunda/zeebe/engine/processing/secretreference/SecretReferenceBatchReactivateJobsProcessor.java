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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.SecretReferenceState;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ExcludeAuthorizationCheck
public final class SecretReferenceBatchReactivateJobsProcessor
    implements TypedRecordProcessor<SecretReferenceRecord> {

  private static final int MAX_BATCH_SIZE = 100;

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final KeyGenerator keyGenerator;
  private final SecretReferenceState secretReferenceState;

  public SecretReferenceBatchReactivateJobsProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final SecretReferenceState secretReferenceState) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    this.keyGenerator = keyGenerator;
    this.secretReferenceState = secretReferenceState;
  }

  @Override
  public void processRecord(final TypedRecord<SecretReferenceRecord> record) {
    final var value = record.getValue();
    final var storeId = value.getStoreId();
    final var secretReference = value.getSecretReference();

    // Write the current batch as an event first. Applying this event removes these job keys from
    // the waiting-jobs index, so they will not be re-selected by the next-batch query below.
    stateWriter.appendFollowUpEvent(
        record.getKey(), SecretReferenceIntent.BATCH_JOBS_REACTIVATED, value);

    final Set<Long> currentBatch = new HashSet<>(value.getJobKeys());
    final List<Long> nextBatch = buildNextBatch(storeId, secretReference, currentBatch);

    if (!nextBatch.isEmpty()) {
      final var nextRecord =
          new SecretReferenceRecord().setStoreId(storeId).setSecretReference(secretReference);
      for (final long jobKey : nextBatch) {
        nextRecord.addJobKey(jobKey);
      }
      commandWriter.appendFollowUpCommand(
          keyGenerator.nextKey(), SecretReferenceIntent.BATCH_REACTIVATE_JOBS, nextRecord);
    }
  }

  private List<Long> buildNextBatch(
      final String storeId, final String secretReference, final Set<Long> currentBatch) {
    final List<Long> nextBatch = new ArrayList<>();
    secretReferenceState.visitJobsBySecretReference(
        storeId,
        secretReference,
        jobKey -> {
          if (!currentBatch.contains(jobKey)) {
            nextBatch.add(jobKey);
          }
          return nextBatch.size() < MAX_BATCH_SIZE;
        });
    return nextBatch;
  }
}
