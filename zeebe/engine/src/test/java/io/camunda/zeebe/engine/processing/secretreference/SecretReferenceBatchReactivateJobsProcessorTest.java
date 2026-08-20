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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

@ExtendWith(ProcessingStateExtension.class)
public final class SecretReferenceBatchReactivateJobsProcessorTest {

  private static final String STORE_ID = "storeA";
  private static final String SECRET_REF = "secret1";
  private static final String JOB_TYPE = "type";

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private MutableSecretReferenceState secretReferenceState;
  private MutableJobState jobState;
  private StateWriter stateWriter;
  private TypedCommandWriter commandWriter;
  private KeyGenerator keyGenerator;
  private BpmnJobActivationBehavior jobActivationBehavior;
  private SecretReferenceBatchReactivateJobsProcessor processor;

  @BeforeEach
  void setUp() {
    secretReferenceState = processingState.getSecretReferenceState();
    jobState = processingState.getJobState();

    stateWriter = mock(StateWriter.class);
    when(stateWriter.canWriteEventOfLength(org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
    commandWriter = mock(TypedCommandWriter.class);
    keyGenerator = mock(KeyGenerator.class);
    when(keyGenerator.nextKey()).thenReturn(999L);
    jobActivationBehavior = mock(BpmnJobActivationBehavior.class);
    // a hand-out reports whether the batch had room for it; the default mock answer is false, which
    // would stop the drain after the first job in every test that does not care about the budget
    when(jobActivationBehavior.publishWork(org.mockito.ArgumentMatchers.anyLong(), any(), any()))
        .thenReturn(true);

    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    when(writers.command()).thenReturn(commandWriter);

    processor =
        new SecretReferenceBatchReactivateJobsProcessor(
            writers, keyGenerator, processingState, jobActivationBehavior);
  }

  private MockTypedRecord<SecretReferenceRecord> command(final SecretReferenceRecord value) {
    return new MockTypedRecord<>(500L, new RecordMetadata(), value);
  }

  @Test
  void shouldWriteBatchJobsReactivatedEventForCurrentBatch() {
    // given - two jobs on the command; not seeded in state (event application removes them first)
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
    // given - job 3 is still in state (jobs 1 and 2 were removed by event application already);
    //         command carries jobs 1 and 2 as the current batch
    waitingJob(3L);
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
    // given - no jobs remain in state (all were removed by event application); command carries all
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
    // given - job 1 is on the current batch (already removed from state by event application);
    //         jobs 3 and 4 remain; job 3 also waits on secret2 (still pending), making it
    //         ineligible for reactivation, but it should still appear in the next batch command
    //         (the applier decides eligibility, not the processor)
    waitingJob(3L);
    waitingJob(4L);
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

  @Test
  void shouldPublishEveryReactivatedJob() {
    // given - two activatable jobs, as the batch event leaves them once it reactivated them
    activatableJob(1L, "type-of-job-1");
    activatableJob(2L, "type-of-job-2");
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L)
            .addJobKey(2L);

    // the job state reuses one record instance across reads, so the record is read at call time
    final Map<Long, String> publishedJobTypes = new LinkedHashMap<>();
    doAnswer(
            invocation -> {
              publishedJobTypes.put(
                  invocation.getArgument(0), ((JobRecord) invocation.getArgument(1)).getType());
              return true;
            })
        .when(jobActivationBehavior)
        .publishWork(org.mockito.ArgumentMatchers.anyLong(), any(), any());

    // when
    processor.processRecord(command(value));

    // then - both jobs are handed to a worker again, each with its own record, which a pushing
    // worker depends on
    Assertions.assertThat(publishedJobTypes)
        .containsExactly(
            org.assertj.core.api.Assertions.entry(1L, "type-of-job-1"),
            org.assertj.core.api.Assertions.entry(2L, "type-of-job-2"));
  }

  @Test
  void shouldNotPublishJobThatStayedParked() {
    // given - a job the batch event left parked, e.g. because another reference is still pending
    activatableJob(1L);
    jobState.parkForSecretResolution(1L, jobState.getJob(1L));
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L);

    // when
    processor.processRecord(command(value));

    // then - the overload the processor hands its jobs to is the one that shares the notified job
    // types across the batch; verifying the single-job one would pass without publishing anything
    verify(jobActivationBehavior, never())
        .publishWork(org.mockito.ArgumentMatchers.anyLong(), any(), any());
  }

  @Test
  void shouldReactivateOnlyTheJobsThatFitTheRecordBatch() {
    // given - two activatable jobs, and a record batch with room for one activation
    activatableJob(1L);
    activatableJob(2L);
    waitingJob(2L);
    // the first check is the selection sizing the second job, which does not fit; the checks after
    // it guard the hand-outs of what was selected, and those still have room
    when(stateWriter.canWriteEventOfLength(org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(false, true);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L)
            .addJobKey(2L);

    // when
    processor.processRecord(command(value));

    // then - only the first job is reactivated and published
    final var eventCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(500L), eq(SecretReferenceIntent.BATCH_JOBS_REACTIVATED), eventCaptor.capture());
    Assertions.assertThat(eventCaptor.getValue().getJobKeys()).containsExactly(1L);
    verify(jobActivationBehavior).publishWork(eq(1L), any(), any());
    verify(jobActivationBehavior, never()).publishWork(eq(2L), any(), any());

    // and - the job left out keeps its waiting entry and is re-collected into the follow-up command
    final var commandCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(999L), eq(SecretReferenceIntent.BATCH_REACTIVATE_JOBS), commandCaptor.capture());
    Assertions.assertThat(commandCaptor.getValue().getJobKeys()).containsExactly(2L);
  }

  @Test
  void shouldStopHandingOutJobsWhenTheFollowUpRecordsNoLongerFit() {
    // given - two jobs the selection took, and a batch that runs out of room once the first of
    // them has been handed out
    activatableJob(1L);
    activatableJob(2L);
    // the selection sizes the second job (fits), then each hand-out checks that the records
    // written after it still fit: they do for the first job and no longer for the second
    when(stateWriter.canWriteEventOfLength(org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(true, true, false);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L)
            .addJobKey(2L);

    // when
    processor.processRecord(command(value));

    // then - the drain stops rather than letting a hand-out take the room the follow-up records
    // need, which would fail the whole cycle
    verify(jobActivationBehavior).publishWork(eq(1L), any(), any());
    verify(jobActivationBehavior, never()).publishWork(eq(2L), any(), any());

    // and - both jobs were still reactivated, so the one left un-pushed is activatable rather than
    // parked on a reference that is resolved already
    final var eventCaptor = ArgumentCaptor.forClass(SecretReferenceRecord.class);
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(500L), eq(SecretReferenceIntent.BATCH_JOBS_REACTIVATED), eventCaptor.capture());
    Assertions.assertThat(eventCaptor.getValue().getJobKeys()).containsExactly(1L, 2L);
  }

  @Test
  void shouldStopHandingOutJobsWhenAHandOutReportsTheBatchIsFull() {
    // given - two jobs the selection took, and a first hand-out that finds no room for its
    // activation once the stream's worker name is on it
    activatableJob(1L);
    activatableJob(2L);
    when(jobActivationBehavior.publishWork(eq(1L), any(), any())).thenReturn(false);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L)
            .addJobKey(2L);

    // when
    processor.processRecord(command(value));

    // then - the jobs behind it are not handed out either: the batch is out of room, so their
    // appends would fail the whole cycle instead of just turning one job away
    verify(jobActivationBehavior).publishWork(eq(1L), any(), any());
    verify(jobActivationBehavior, never()).publishWork(eq(2L), any(), any());
  }

  @Test
  void shouldNotPublishJobThatCarriesAnIncident() {
    // given - an activatable job whose incident still waits to be resolved
    activatableJob(1L);
    final var incident =
        new io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord().setJobKey(1L);
    processingState.getIncidentState().createIncident(77L, incident);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L);

    // when
    processor.processRecord(command(value));

    // then - resolving the incident is what hands the job out, not this chain
    verify(jobActivationBehavior, never())
        .publishWork(org.mockito.ArgumentMatchers.anyLong(), any(), any());
  }

  @Test
  void shouldCountTheJobsAlreadySelectedTowardsTheRecordBatch() {
    // given - three activatable jobs whose activations share one record batch
    activatableJob(1L);
    activatableJob(2L);
    activatableJob(3L);
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L)
            .addJobKey(2L)
            .addJobKey(3L);

    // when
    processor.processRecord(command(value));

    // then - each further job is checked against the batch including the jobs selected before it,
    // not against the empty batch, so a batch cannot be filled beyond its capacity
    final var requestedLengths = ArgumentCaptor.forClass(Integer.class);
    verify(stateWriter, org.mockito.Mockito.atLeast(2))
        .canWriteEventOfLength(requestedLengths.capture());
    final var lengths = requestedLengths.getAllValues();
    Assertions.assertThat(lengths.get(1) - lengths.get(0))
        .isGreaterThanOrEqualTo(jobState.getJob(2L).getLength());
  }

  @Test
  void shouldNotRecollectAJobTheHandOutParkedAgain() {
    // given - a job whose value is gone from the cache again, so publishing it parks it once more,
    // and a second job still waiting for the same reference
    activatableJob(1L);
    waitingJob(2L);
    doAnswer(
            invocation -> {
              final long publishedJobKey = invocation.getArgument(0);
              secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
              secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, publishedJobKey);
              return true;
            })
        .when(jobActivationBehavior)
        .publishWork(org.mockito.ArgumentMatchers.anyLong(), any(), any());
    final var value =
        new SecretReferenceRecord()
            .setStoreId(STORE_ID)
            .setSecretReference(SECRET_REF)
            .addJobKey(1L);

    // when
    processor.processRecord(command(value));

    // then - the job was handed out, which parked it again on a reference that is pending once
    // more, so this chain stops: continuing would strip the waiting entries of the jobs it cannot
    // reactivate, and the chain of the new resolution drains them instead
    verify(jobActivationBehavior).publishWork(eq(1L), any(), any());
    Assertions.assertThat(secretReferenceState.isWaiting(STORE_ID, SECRET_REF, 1L)).isTrue();
    Assertions.assertThat(secretReferenceState.isWaiting(STORE_ID, SECRET_REF, 2L)).isTrue();
    verify(commandWriter, never())
        .appendFollowUpCommand(org.mockito.ArgumentMatchers.anyLong(), any(), any());
  }

  /**
   * Records the job as waiting for the reference, as it is once the resolution completed: the
   * pending marker the waiting entry needs is gone again by the time the chain drains it.
   */
  private void waitingJob(final long jobKey) {
    secretReferenceState.addPendingSecretReference(STORE_ID, SECRET_REF);
    secretReferenceState.addWaitingJob(STORE_ID, SECRET_REF, jobKey);
    secretReferenceState.removePendingSecretReference(STORE_ID, SECRET_REF);
  }

  private JobRecord activatableJob(final long jobKey) {
    return activatableJob(jobKey, JOB_TYPE);
  }

  private JobRecord activatableJob(final long jobKey, final String jobType) {
    final var job =
        new JobRecord()
            .setType(jobType)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setRetries(3);
    jobState.insertJobRecordActivatable(jobKey, job);
    jobState.makeJobActivatableByPriority(
        job.getTypeBuffer(), jobKey, job.getTenantId(), job.getPriority());
    return job;
  }
}
