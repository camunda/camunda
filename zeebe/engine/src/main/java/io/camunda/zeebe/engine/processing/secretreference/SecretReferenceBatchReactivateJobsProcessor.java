/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secretreference;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SecretReferenceState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
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
  private final JobState jobState;
  private final IncidentState incidentState;
  private final BpmnJobActivationBehavior jobActivationBehavior;

  public SecretReferenceBatchReactivateJobsProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final BpmnJobActivationBehavior jobActivationBehavior) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    this.keyGenerator = keyGenerator;
    secretReferenceState = processingState.getSecretReferenceState();
    jobState = processingState.getJobState();
    incidentState = processingState.getIncidentState();
    this.jobActivationBehavior = jobActivationBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<SecretReferenceRecord> record) {
    final var value = record.getValue();
    final var storeId = value.getStoreId();
    final var secretReference = value.getSecretReference();

    // only the jobs that fit the record batch go into the batch event; the ones cut off keep their
    // waiting entries and are re-collected into the follow-up command below
    final var processedBatch = selectJobsToReactivate(value);

    stateWriter.appendFollowUpEvent(
        record.getKey(), SecretReferenceIntent.BATCH_JOBS_REACTIVATED, processedBatch);

    // the jobs still waiting are collected before the jobs of this batch are handed out: a job the
    // push parks again (its value is already gone from the cache) waits on a reference that is
    // pending again, and reactivating it in the same chain would drop the waiting entry it just
    // got, leaving it parked with nothing left to reactivate it
    final List<Long> nextBatch = buildNextBatch(storeId, secretReference);

    // the event above reactivated the eligible jobs, so they can be handed to a worker again. A
    // pushing worker only ever receives jobs that are pushed to it, so the reactivation must push
    // them; publishWork checks their references again and parks a job whose value is gone already
    publishReactivatedJobs(processedBatch, followUpRecordsReserve(value));

    // a hand-out that found the value gone from the cache requested the resolution again, which
    // makes the reference pending again. This chain stops there: the jobs it has not drained yet
    // keep their waiting entries and are drained by the chain of that new resolution, whereas
    // continuing would strip the waiting entry of every job it reactivates without being able to
    // reactivate it, leaving them parked with nothing left to hand them out
    if (!nextBatch.isEmpty() && !secretReferenceState.isPending(storeId, secretReference)) {
      final var nextRecord = newBatch(storeId, secretReference);
      nextBatch.forEach(nextRecord::addJobKey);
      commandWriter.appendFollowUpCommand(
          keyGenerator.nextKey(), SecretReferenceIntent.BATCH_REACTIVATE_JOBS, nextRecord);
    }
  }

  /**
   * Each cycle of the chain starts its own record batch. Without this, the follow-up command would
   * be processed in the current batch on the same, already filled result builder, so the budget
   * check would reject pushes based on what earlier cycles wrote. Isolating the cycles keeps the
   * batch bounded by one command's jobs instead of letting the whole chain accumulate into a single
   * batch until it exceeds the log's maximum fragment size.
   */
  @Override
  public boolean shouldProcessResultsInSeparateBatches() {
    return true;
  }

  /**
   * Returns the jobs of the command this cycle reactivates: as many as the record batch is expected
   * to take, since handing a job out writes into the same batch. What that costs is {@link
   * BpmnJobActivationBehavior#maxHandOutLength(JobRecord)}, so the estimate stays with the hand-out
   * that spends it rather than being restated here. The first job is always taken, so a cycle
   * always drains at least one waiting entry and the chain cannot spin on a job whose record alone
   * exceeds the budget.
   *
   * <p>Only an expectation: a hand-out can cost more than the stored job record predicts, because
   * the worker name of the stream that takes the job is not on that record. Selecting a job the
   * batch turns out not to fit is not a failure, it just leaves the job activatable for a long poll
   * (see {@link #publishReactivatedJobs}); what keeps the batch intact is the hand-out's own check.
   */
  private SecretReferenceRecord selectJobsToReactivate(final SecretReferenceRecord value) {
    final var storeId = value.getStoreId();
    final var secretReference = value.getSecretReference();
    final var selected = newBatch(storeId, secretReference);
    final int followUpRecordsReserve = followUpRecordsReserve(value);
    // the hand-outs of the selected jobs share one record batch, so their lengths accumulate
    var selectedHandOutsLength = 0;
    var jobSelected = false;
    for (final long jobKey : value.getJobKeys()) {
      final JobRecord job = jobState.getJob(jobKey);
      if (job == null) {
        // the job is gone (e.g. its process instance was cancelled); the batch event still cleans
        // up its waiting entry, and a job that does not exist is not pushed either
        selected.addJobKey(jobKey);
        continue;
      }
      final int handOutLength = BpmnJobActivationBehavior.maxHandOutLength(job);
      if (jobSelected
          && !stateWriter.canWriteEventOfLength(
              selectedHandOutsLength + handOutLength + followUpRecordsReserve)) {
        break;
      }
      selected.addJobKey(jobKey);
      selectedHandOutsLength += handOutLength;
      jobSelected = true;
    }
    return selected;
  }

  /**
   * Hands every job the batch event just reactivated to a worker. A job that is not activatable is
   * skipped: it was left parked because another of its references is still pending, and its
   * resolution puts the job back on this path. A job that carries an incident is skipped too,
   * whichever state it is in: an incident means the job waits for someone to resolve it, and
   * resolving it hands the job out again.
   *
   * <p>Stops as soon as the batch runs out of room, either because a hand-out reports it or because
   * what is left would not cover the records written after the hand-outs. The jobs it does not
   * reach were reactivated all the same, so they stay activatable and a long poll collects them.
   * That is the lesser of the two outcomes: pressing on means an append that does not fit, which
   * fails the whole cycle, and since this command is written by the engine its rejection reaches
   * nobody, leaving every job of the cycle parked on a reference that is resolved already.
   *
   * <p>The jobs are read again here rather than kept from the selection that sized them. The job
   * state hands out one record instance it reuses on every read, and the batch event applied in
   * between reads jobs itself, so keeping them would mean copying every selected job's record onto
   * the heap for the whole batch. Reading a job twice costs a point lookup, which is the cheaper of
   * the two.
   */
  private void publishReactivatedJobs(
      final SecretReferenceRecord processedBatch, final int followUpRecordsReserve) {
    // shared across the batch so the workers of a job type are notified once, not once per job
    final Set<String> notifiedJobTypes = new HashSet<>();
    for (final long jobKey : processedBatch.getJobKeys()) {
      if (jobState.getState(jobKey) != State.ACTIVATABLE || hasIncident(jobKey)) {
        continue;
      }
      if (!stateWriter.canWriteEventOfLength(followUpRecordsReserve)) {
        // keeping this much free is what lets the follow-up command below be appended unchecked,
        // so the chain reaches the jobs this cycle leaves waiting
        break;
      }
      // the reused record instance is why each job is published before the next one is read
      final JobRecord job = jobState.getJob(jobKey);
      if (job != null && !jobActivationBehavior.publishWork(jobKey, job, notifiedJobTypes)) {
        break;
      }
    }
  }

  private boolean hasIncident(final long jobKey) {
    return incidentState.getJobIncidentKey(jobKey) != IncidentState.MISSING_INCIDENT;
  }

  /**
   * Capacity to keep free for the two records written after the hand-outs: the batch result event
   * (its keys are a subset of the command's, so the command value bounds its size) and a potential
   * follow-up {@code BATCH_REACTIVATE_JOBS} command. The buffer absorbs the record metadata that
   * the value lengths do not include.
   */
  private static int followUpRecordsReserve(final SecretReferenceRecord value) {
    return value.getLength()
        + maxNextCommandLength(value.getStoreId(), value.getSecretReference())
        + EngineConfiguration.BATCH_SIZE_CALCULATION_BUFFER;
  }

  private static SecretReferenceRecord newBatch(
      final String storeId, final String secretReference) {
    return new SecretReferenceRecord().setStoreId(storeId).setSecretReference(secretReference);
  }

  /** Length of the largest possible follow-up command: a full batch of widest-encoded job keys. */
  private static int maxNextCommandLength(final String storeId, final String secretReference) {
    final var maxRecord = newBatch(storeId, secretReference);
    for (int i = 0; i < MAX_BATCH_SIZE; i++) {
      maxRecord.addJobKey(Long.MAX_VALUE);
    }
    return maxRecord.getLength();
  }

  private List<Long> buildNextBatch(final String storeId, final String secretReference) {
    final List<Long> nextBatch = new ArrayList<>();
    secretReferenceState.visitJobsBySecretReference(
        storeId,
        secretReference,
        jobKey -> {
          nextBatch.add(jobKey);
          return nextBatch.size() < MAX_BATCH_SIZE;
        });
    return nextBatch;
  }
}
