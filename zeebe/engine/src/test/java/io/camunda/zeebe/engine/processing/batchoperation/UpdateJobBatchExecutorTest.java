/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationJobUpdatePlan;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public final class UpdateJobBatchExecutorTest extends AbstractBatchOperationTest {

  private static final String PROCESS_ID = "process";

  @Test
  public void shouldUpdatePriorityForMatchedJobs() {
    // given
    final Map<String, Object> claims = Map.of(AUTHORIZED_USERNAME, DEFAULT_USER.getUsername());
    final long jobKey = createActivatableJob(DEFAULT_JOB_TYPE);
    final var plan = new BatchOperationJobUpdatePlan().setPriority(80);

    // when
    final var batchOperationKey = createNewUpdateJobBatchOperation(Set.of(jobKey), plan, claims);

    // then the batch operation completes
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationIntent.COMPLETED))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.COMPLETED);

    // and the job priority is updated
    assertThat(
            RecordingExporter.jobRecords()
                .withIntent(JobIntent.PRIORITY_UPDATED)
                .withRecordKey(jobKey)
                .getFirst()
                .getValue()
                .getPriority())
        .isEqualTo(80);
  }

  @Test
  public void shouldApplyMixedChangeset() {
    // given
    final Map<String, Object> claims = Map.of(AUTHORIZED_USERNAME, DEFAULT_USER.getUsername());
    final long jobKey = createActivatableJob(DEFAULT_JOB_TYPE);
    final var plan = new BatchOperationJobUpdatePlan().setPriority(50).setRetries(7);

    // when
    final var batchOperationKey = createNewUpdateJobBatchOperation(Set.of(jobKey), plan, claims);

    // then the batch operation completes
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationIntent.COMPLETED))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.COMPLETED);

    // and both priority and retries are applied to the job
    assertThat(
            RecordingExporter.jobRecords()
                .withIntent(JobIntent.UPDATED)
                .withRecordKey(jobKey)
                .getFirst()
                .getValue())
        .satisfies(
            v -> {
              assertThat(v.getPriority()).isEqualTo(50);
              assertThat(v.getRetries()).isEqualTo(7);
            });
  }

  @Test
  public void shouldEmitUpdateAndContinueWhenJobNotFound() {
    // given - a job key that does not correspond to any live job (a terminal/completed job is
    // likewise removed from state, so it yields the same NOT_FOUND rejection)
    final Map<String, Object> claims = Map.of(AUTHORIZED_USERNAME, DEFAULT_USER.getUsername());
    final long jobKey = 42L; // non-existing job key
    final var plan = new BatchOperationJobUpdatePlan().setPriority(80);

    // when
    final var batchOperationKey = createNewUpdateJobBatchOperation(Set.of(jobKey), plan, claims);

    // then the batch operation still completes without failure
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationIntent.COMPLETED))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.COMPLETED);

    // and the JOB:UPDATE command was written and rejected
    Assertions.assertThat(
            RecordingExporter.jobRecords().withRecordKey(jobKey).onlyCommandRejections().getFirst())
        .hasKey(jobKey)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasIntent(JobIntent.UPDATE);
  }

  @Test
  public void shouldCompleteWhenNoJobsMatch() {
    // given
    final Map<String, Object> claims = Map.of(AUTHORIZED_USERNAME, DEFAULT_USER.getUsername());
    final var plan = new BatchOperationJobUpdatePlan().setPriority(80);

    // when
    final var batchOperationKey = createNewUpdateJobBatchOperation(Set.of(), plan, claims);

    // then the batch operation completes with no items processed
    final var completed =
        RecordingExporter.batchOperationLifecycleRecords()
            .withBatchOperationKey(batchOperationKey)
            .withIntent(BatchOperationIntent.COMPLETED)
            .getFirst();
    assertThat(completed.getIntent()).isEqualTo(BatchOperationIntent.COMPLETED);

    // and no chunk records were ever created because no jobs matched the filter. All records for
    // this batch operation are present by the time COMPLETED is recorded, so we bound the search
    // on that record's position to avoid blocking on an empty stream.
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getPosition() >= completed.getPosition())
                .withValueType(ValueType.BATCH_OPERATION_CHUNK)
                .toList())
        .isEmpty();
  }

  @Test
  public void shouldRestoreFifoOrderingWhenPriorityResetToZero() {
    // given - three activatable jobs of the SAME type whose priority order differs from their
    // creation (jobKey) order: created A, B, C with priorities 10, 30, 20. By priority the
    // activation order would be B, C, A; FIFO (creation) order is A, B, C.
    final Map<String, Object> claims = Map.of(AUTHORIZED_USERNAME, DEFAULT_USER.getUsername());
    final long jobKeyA = createPrioritizedJob("priority-a", 10);
    final long jobKeyB = createPrioritizedJob("priority-b", 30);
    final long jobKeyC = createPrioritizedJob("priority-c", 20);

    final var plan = new BatchOperationJobUpdatePlan().setPriority(0);

    // when - reset all three jobs to priority 0 via a batch operation
    final var batchOperationKey =
        createNewUpdateJobBatchOperation(Set.of(jobKeyA, jobKeyB, jobKeyC), plan, claims);

    // then the batch operation completes
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationIntent.COMPLETED))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.COMPLETED);

    // and all three jobs reach priority 0 (await before activating to avoid racing the re-index)
    assertThat(
            RecordingExporter.jobRecords(JobIntent.PRIORITY_UPDATED)
                .withType(DEFAULT_JOB_TYPE)
                .limit(3))
        .extracting(r -> r.getValue().getPriority())
        .containsExactly(0, 0, 0);

    // then activating the jobs returns them in jobKey-ASC (creation/FIFO) order, not the original
    // priority order (B, C, A) - i.e. FIFO ordering is restored
    final var batch =
        engine
            .jobs()
            .withType(DEFAULT_JOB_TYPE)
            .withMaxJobsToActivate(3)
            .activate(DEFAULT_USER.getUsername());
    assertThat(batch.getValue().getJobKeys()).isSorted();
    assertThat(batch.getValue().getJobKeys()).containsExactly(jobKeyA, jobKeyB, jobKeyC);
    assertThat(batch.getValue().getJobs()).extracting(JobRecordValue::getPriority).containsOnly(0);
  }

  @Test
  public void shouldEmitUpdateAndContinueWhenJobBecameTerminal() {
    // given - a real job that is driven to a terminal state (completed) before the batch runs, so
    // it is removed from state by execution time
    final Map<String, Object> claims = Map.of(AUTHORIZED_USERNAME, DEFAULT_USER.getUsername());
    final long jobKey = createActivatableJob(DEFAULT_JOB_TYPE);
    final var batchRecord =
        engine.jobs().withType(DEFAULT_JOB_TYPE).activate(DEFAULT_USER.getUsername());
    final long activatedJobKey = batchRecord.getValue().getJobKeys().get(0);
    engine.job().withKey(activatedJobKey).complete();
    RecordingExporter.jobRecords(JobIntent.COMPLETED).withRecordKey(activatedJobKey).await();

    final var plan = new BatchOperationJobUpdatePlan().setPriority(80);

    // when - the batch targets the now-terminal job
    final var batchOperationKey = createNewUpdateJobBatchOperation(Set.of(jobKey), plan, claims);

    // then the batch operation still completes without failure
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationIntent.COMPLETED))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.COMPLETED);

    // and the JOB:UPDATE command was written and rejected because the job is no longer in state
    Assertions.assertThat(
            RecordingExporter.jobRecords().withRecordKey(jobKey).onlyCommandRejections().getFirst())
        .hasKey(jobKey)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasIntent(JobIntent.UPDATE);
  }

  // Test for legacy-CF lazy migration (shouldMigrateLegacyActivatableJobOnPriorityUpdate) is
  // intentionally omitted: there is no clean engine-test-level mechanism to seed a job into the
  // pre-8.10 legacy JOB_ACTIVATABLE column family (no such helper exists in the job tests, and
  // jobs created via the engine are always written to JOB_ACTIVATABLE_BY_PRIORITY). The legacy-CF
  // lazy migration on priority update is covered directly by M2-6's DbJobState.updateJobPriority
  // unit tests; the batch path reuses that exact code path via the JOB:UPDATE command, so no
  // additional engine-level coverage is added here rather than fabricating internal state.

  private long createActivatableJob(final String type) {
    return engine.createJob(type, PROCESS_ID).getKey();
  }

  private long createPrioritizedJob(final String processId, final int priority) {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    b ->
                        b.zeebeJobType(DEFAULT_JOB_TYPE).zeebeJobPriority(String.valueOf(priority)))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withType(DEFAULT_JOB_TYPE)
        .filter(r -> r.getValue().getProcessInstanceKey() == processInstanceKey)
        .getFirst()
        .getKey();
  }
}
