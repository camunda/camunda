/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secretreference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

@ExtendWith(ProcessingStateExtension.class)
public final class SecretReferenceResolutionCompleteProcessorTest {

  private static final String STORE_ID = "storeA";
  private static final String SECRET_REF = "secret1";

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableSecretReferenceState secretReferenceState;
  private StateWriter stateWriter;
  private TypedCommandWriter commandWriter;
  private TypedRejectionWriter rejectionWriter;
  private KeyGenerator keyGenerator;
  private SecretReferenceResolutionCompleteProcessor processor;

  @BeforeEach
  void setUp() {
    secretReferenceState = processingState.getSecretReferenceState();

    stateWriter = mock(StateWriter.class);
    commandWriter = mock(TypedCommandWriter.class);
    rejectionWriter = mock(TypedRejectionWriter.class);
    keyGenerator = mock(KeyGenerator.class);
    when(keyGenerator.nextKey()).thenReturn(999L);

    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    when(writers.command()).thenReturn(commandWriter);
    when(writers.rejection()).thenReturn(rejectionWriter);

    processor =
        new SecretReferenceResolutionCompleteProcessor(writers, keyGenerator, secretReferenceState);
  }

  private MockTypedRecord<SecretReferenceRecord> command(final SecretReferenceRecord value) {
    return new MockTypedRecord<>(500L, new RecordMetadata(), value);
  }

  @Test
  void shouldWriteResolutionCompletedEvent() {
    // given
    secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
    final var value =
        new SecretReferenceRecord().setStoreId(STORE_ID).setSecretReference(SECRET_REF);

    // when
    processor.processRecord(command(value));

    // then
    final var eventCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(500L), eq(SecretReferenceIntent.RESOLUTION_COMPLETED), eventCaptor.capture());
    Assertions.assertThat(eventCaptor.getValue().getStoreId()).isEqualTo(STORE_ID);
    Assertions.assertThat(eventCaptor.getValue().getSecretReference()).isEqualTo(SECRET_REF);
  }

  @Test
  void shouldWriteBatchReactivateJobsCommandForWaitingJobs() {
    // given
    secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, 1L);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, 2L);
    final var value =
        new SecretReferenceRecord().setStoreId(STORE_ID).setSecretReference(SECRET_REF);

    // when
    processor.processRecord(command(value));

    // then
    final var commandCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(999L), eq(SecretReferenceIntent.BATCH_REACTIVATE_JOBS), commandCaptor.capture());
    final var next = commandCaptor.getValue();
    Assertions.assertThat(next.getStoreId()).isEqualTo(STORE_ID);
    Assertions.assertThat(next.getSecretReference()).isEqualTo(SECRET_REF);
    Assertions.assertThat(next.getJobKeys()).containsExactlyInAnyOrder(1L, 2L);
  }

  @Test
  void shouldCapBatchAtHundred() {
    // given - 105 jobs waiting on the resolved secret reference
    secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
    for (long jobKey = 1; jobKey <= 105; jobKey++) {
      secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, jobKey);
    }
    final var value =
        new SecretReferenceRecord().setStoreId(STORE_ID).setSecretReference(SECRET_REF);

    // when
    processor.processRecord(command(value));

    // then - the batch is capped at 100 jobs
    final var commandCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(999L), eq(SecretReferenceIntent.BATCH_REACTIVATE_JOBS), commandCaptor.capture());
    Assertions.assertThat(commandCaptor.getValue().getJobKeys()).hasSize(100);
  }

  @Test
  void shouldNotWriteBatchCommandWhenNoJobsWaiting() {
    // given - no jobs waiting on the resolved secret reference
    secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
    final var value =
        new SecretReferenceRecord().setStoreId(STORE_ID).setSecretReference(SECRET_REF);

    // when
    processor.processRecord(command(value));

    // then
    verify(commandWriter, never()).appendFollowUpCommand(anyLong(), any(), any());
  }

  @Test
  void shouldRejectWhenSecretReferenceNotPending() {
    // given - secret reference is NOT seeded as pending (never requested, or already completed)
    final var value =
        new SecretReferenceRecord().setStoreId(STORE_ID).setSecretReference(SECRET_REF);

    // when
    processor.processRecord(command(value));

    // then - rejected, no event or follow-up command written
    verify(rejectionWriter).appendRejection(any(), eq(RejectionType.NOT_FOUND), any());
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(commandWriter, never()).appendFollowUpCommand(anyLong(), any(), any());
  }

  @Test
  void shouldIncludeJobsStillWaitingOnOtherSecretsInBatch() {
    // given - job 1 waits on the resolved secret reference AND on a second, still-pending secret;
    //         the processor must NOT filter it out. Eligibility is decided by the
    //         BATCH_JOBS_REACTIVATED applier, not the processor, so that removeWaitingJob is
    //         always called for jobs waiting on the resolved secret — otherwise their
    //         (secretRef, jobKey) index entry would leak forever.
    secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, 1L);
    secretReferenceState.addPendingSecretReference(STORE_ID, "secret2");
    secretReferenceState.addWaitingJob(STORE_ID, "secret2", 1L);
    final var value =
        new SecretReferenceRecord().setStoreId(STORE_ID).setSecretReference(SECRET_REF);

    // when
    processor.processRecord(command(value));

    // then - job 1 still appears in the emitted batch
    final var commandCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(999L), eq(SecretReferenceIntent.BATCH_REACTIVATE_JOBS), commandCaptor.capture());
    Assertions.assertThat(commandCaptor.getValue().getJobKeys()).containsExactly(1L);
  }
}
