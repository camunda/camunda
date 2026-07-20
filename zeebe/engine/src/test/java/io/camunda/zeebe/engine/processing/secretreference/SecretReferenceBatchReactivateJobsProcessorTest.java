/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secretreference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

@ExtendWith(ProcessingStateExtension.class)
public final class SecretReferenceBatchReactivateJobsProcessorTest {

  private static final String STORE_ID = "storeA";
  private static final String SECRET_REF = "secret1";

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableSecretReferenceState secretReferenceState;
  private StateWriter stateWriter;
  private TypedCommandWriter commandWriter;
  private KeyGenerator keyGenerator;
  private SecretReferenceBatchReactivateJobsProcessor processor;

  @BeforeEach
  void setUp() {
    secretReferenceState = processingState.getSecretReferenceState();

    stateWriter = mock(StateWriter.class);
    commandWriter = mock(TypedCommandWriter.class);
    keyGenerator = mock(KeyGenerator.class);
    when(keyGenerator.nextKey()).thenReturn(999L);

    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    when(writers.command()).thenReturn(commandWriter);

    processor =
        new SecretReferenceBatchReactivateJobsProcessor(
            writers, keyGenerator, secretReferenceState);
  }

  private MockTypedRecord<SecretReferenceRecord> command(final SecretReferenceRecord value) {
    return new MockTypedRecord<>(500L, new RecordMetadata(), value);
  }

  @Test
  void shouldWriteBatchJobsReactivatedEventForCurrentBatch() {
    // given - two eligible jobs on the command; both are also present in state
    secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, 1L);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, 2L);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L)
            .addJobKey(2L);

    // when
    processor.processRecord(command(value));

    // then - the current batch is written back as a BATCH_JOBS_REACTIVATED event on the same key
    final var eventCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(500L), eq(SecretReferenceIntent.BATCH_JOBS_REACTIVATED), eventCaptor.capture());
    Assertions.assertThat(eventCaptor.getValue().getJobKeys()).containsExactly(1L, 2L);

    // and - no follow-up command, since state holds only the current batch
    verify(commandWriter, never())
        .appendFollowUpCommand(org.mockito.ArgumentMatchers.anyLong(), any(), any());
  }

  @Test
  void shouldWriteFollowUpBatchReactivateJobsCommandWhenMoreJobsExist() {
    // given - three jobs waiting in state; command carries only the first two (current batch)
    secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, 1L);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, 2L);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, 3L);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L)
            .addJobKey(2L);

    // when
    processor.processRecord(command(value));

    // then - a follow-up BATCH_REACTIVATE_JOBS command is written for the remaining job
    final var commandCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(999L), eq(SecretReferenceIntent.BATCH_REACTIVATE_JOBS), commandCaptor.capture());
    final var next = commandCaptor.getValue();
    Assertions.assertThat(next.getStoreId()).isEqualTo(STORE_ID);
    Assertions.assertThat(next.getSecretReference()).isEqualTo(SECRET_REF);
    Assertions.assertThat(next.getJobKeys()).containsExactly(3L);
  }

  @Test
  void shouldNotWriteFollowUpCommandWhenNoBatchJobsRemain() {
    // given - all waiting jobs are on the command (current batch)
    secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, 1L);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, 2L);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L)
            .addJobKey(2L);

    // when
    processor.processRecord(command(value));

    // then
    verify(commandWriter, never())
        .appendFollowUpCommand(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void shouldIncludeIneligibleJobsInNextBatch() {
    // given - jobs 3 and 4 are waiting; job 3 also has another pending ref (secret2), making it
    //         ineligible for reactivation, but it should still appear in the next batch command
    //         (the applier decides eligibility, not the processor)
    secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, 1L);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, 3L);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, 4L);
    secretReferenceState.addPendingSecretReference(STORE_ID, "secret2");
    secretReferenceState.addWaitingJob(STORE_ID, "secret2", 3L);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L);

    // when
    processor.processRecord(command(value));

    // then - both job 3 (ineligible) and job 4 (eligible) appear in the next batch
    final var commandCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(999L), eq(SecretReferenceIntent.BATCH_REACTIVATE_JOBS), commandCaptor.capture());
    Assertions.assertThat(commandCaptor.getValue().getJobKeys()).containsExactlyInAnyOrder(3L, 4L);
  }
}
