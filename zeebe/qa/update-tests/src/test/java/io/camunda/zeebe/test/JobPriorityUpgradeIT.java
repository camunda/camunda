/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.VersionUtil;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck;
import io.camunda.zeebe.util.migration.VersionCompatibilityCheck.CheckResult.Compatible;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.Timeout;

/**
 * Upgrade integration test for job priority activation.
 *
 * <p>Verifies that after upgrading from the previous Zeebe version to the current one:
 *
 * <ul>
 *   <li>Legacy jobs (in {@code JOB_ACTIVATABLE}) are activated in job-key-ascending order (Phase 2)
 *   <li>New jobs (in {@code JOB_ACTIVATABLE_BY_PRIORITY}) are activated in priority-descending
 *       order
 *   <li>When both exist the three-phase order holds: Phase 1 (priority&gt;0) → Phase 2 (legacy) →
 *       Phase 3 (priority≤0)
 *   <li>No job is activated twice (no stale CF entry after activation)
 *   <li>When a batch is filled by Phase-1 (priority&gt;0) jobs, the legacy Phase-2 jobs are not
 *       dropped by the phase short-circuit and remain activatable on the next call
 * </ul>
 *
 * <p>All scenarios share a single Docker container start for the old broker and a single
 * Spring-broker start for the new version. Isolation is via distinct job types per scenario.
 *
 * <p>Priority assertions use {@link RecordingExporter} on the {@code JOB_BATCH ACTIVATED} record
 * because the Camunda Java client's {@code ActivatedJob} response does not currently expose
 * priority.
 */
@TestInstance(Lifecycle.PER_CLASS)
final class JobPriorityUpgradeIT {

  private static final String LEGACY_ONLY_TYPE = "upgrade-priority-legacy-only";
  private static final int NUM_OF_LEGACY_ONLY_TYPE_JOBS = 3;

  private static final String MIXED_TYPE = "upgrade-priority-mixed";
  private static final int NUM_OF_MIXED_TYPE_LEGACY_JOBS = 2;
  private static final int NUM_OF_MIXED_TYPE_NEW_JOBS = 3;

  private static final String NEW_ONLY_TYPE = "upgrade-priority-new-only";
  private static final int NUM_OF_NEW_ONLY_TYPE_JOBS = 3;

  private static final String BOUNDARY_TYPE = "upgrade-priority-boundary";
  private static final int NUM_OF_BOUNDARY_LEGACY_JOBS = 2;
  private static final int NUM_OF_BOUNDARY_NEW_JOBS = 1;

  private final ContainerState state = new ContainerState();

  // Minimum job key among the 3 new (post-upgrade) MIXED_TYPE jobs.
  // Distinguishes Phase-2 legacy jobs (key < this) from the Phase-3 new zero-priority job
  // (key >= this) in the mixed assertion.
  private long minNewMixedJobKey;

  @BeforeAll
  @Timeout(value = 5, unit = TimeUnit.MINUTES)
  void setUp() {
    final String previous = VersionUtil.getPreviousVersion();
    final String current = VersionUtil.getVersion().replace("-SNAPSHOT", "");
    Assumptions.assumeTrue(
        VersionCompatibilityCheck.check(previous, current) instanceof Compatible,
        "Skipping JobPriorityUpgradeIT: unsupported upgrade path " + previous + " → " + current);

    // ── OLD BROKER ─────────────────────────────────────────────────────────────────────────────
    // Jobs created here go to JOB_ACTIVATABLE (legacy CF). No zeebeJobPriority is set — the old
    // broker stores jobs in the legacy CF with an implicit priority of 0.
    state.withPartitionCount(1).withOldBroker().start(true);

    deployAndCreateInstances(
        LEGACY_ONLY_TYPE + "-legacy", LEGACY_ONLY_TYPE, null, NUM_OF_LEGACY_ONLY_TYPE_JOBS);
    awaitJobsCreated(LEGACY_ONLY_TYPE, NUM_OF_LEGACY_ONLY_TYPE_JOBS);

    deployAndCreateInstances(
        MIXED_TYPE + "-legacy", MIXED_TYPE, null, NUM_OF_MIXED_TYPE_LEGACY_JOBS);
    awaitJobsCreated(MIXED_TYPE, NUM_OF_MIXED_TYPE_LEGACY_JOBS);

    deployAndCreateInstances(
        BOUNDARY_TYPE + "-legacy", BOUNDARY_TYPE, null, NUM_OF_BOUNDARY_LEGACY_JOBS);
    awaitJobsCreated(BOUNDARY_TYPE, NUM_OF_BOUNDARY_LEGACY_JOBS);

    // ── UPGRADE ────────────────────────────────────────────────────────────────────────────────
    // RecordingExporter is reset inside ContainerState when the Spring broker starts.
    state.close();
    state.withNewBroker().start(true);

    // ── NEW BROKER ─────────────────────────────────────────────────────────────────────────────
    // Jobs created here go to JOB_ACTIVATABLE_BY_PRIORITY (8.10+ CF).

    // Mixed scenario: 3 new jobs with explicit priorities
    deployAndCreateInstances(MIXED_TYPE + "-pos50", MIXED_TYPE, 50, 1);
    deployAndCreateInstances(MIXED_TYPE + "-zero", MIXED_TYPE, 0, 1);
    deployAndCreateInstances(MIXED_TYPE + "-neg10", MIXED_TYPE, -10, 1);
    // The Spring broker replays the old broker's log on startup, so RecordingExporter already
    // contains 2 replayed legacy MIXED_TYPE JOB CREATED records. Wait for all 5 (2 legacy + 3 new)
    // before computing minNewMixedJobKey, so we can reliably skip the replayed legacy records.
    awaitJobsCreated(MIXED_TYPE, NUM_OF_MIXED_TYPE_LEGACY_JOBS + NUM_OF_MIXED_TYPE_NEW_JOBS);

    // Record the minimum key of the new mixed jobs to distinguish Phase-2 legacy jobs (lower keys)
    // from the Phase-3 new zero-priority job (higher key) during the mixed assertion.
    minNewMixedJobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withType(MIXED_TYPE)
            .limit(5) // 2 legacy (replayed from old broker) + 3 new
            .toList()
            .stream()
            .skip(2) // skip the 2 replayed legacy records; new keys are always higher
            .mapToLong(Record::getKey)
            .min()
            .orElseThrow();

    // New-only scenario: 3 new jobs with explicit priorities
    deployAndCreateInstances(NEW_ONLY_TYPE + "-pos50", NEW_ONLY_TYPE, 50, 1);
    deployAndCreateInstances(NEW_ONLY_TYPE + "-zero", NEW_ONLY_TYPE, 0, 1);
    deployAndCreateInstances(NEW_ONLY_TYPE + "-neg10", NEW_ONLY_TYPE, -10, 1);
    awaitJobsCreated(NEW_ONLY_TYPE, NUM_OF_NEW_ONLY_TYPE_JOBS);

    // Boundary scenario: 1 new priority>0 job on top of the legacy jobs created pre-upgrade.
    // Used to verify the phase short-circuit: a batch filled by Phase-1 (priority>0) jobs must not
    // drop the legacy Phase-2 jobs — they must remain activatable on the next call.
    deployAndCreateInstances(BOUNDARY_TYPE + "-pos50", BOUNDARY_TYPE, 50, NUM_OF_BOUNDARY_NEW_JOBS);
    awaitJobsCreated(BOUNDARY_TYPE, NUM_OF_BOUNDARY_LEGACY_JOBS + NUM_OF_BOUNDARY_NEW_JOBS);
  }

  @AfterAll
  void tearDown() {
    state.close();
  }

  // ── Test cases ───────────────────────────────────────────────────────────────────────────────

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldActivateMultipleLegacyJobsInKeyOrder() {
    // given/when
    activateJobs(LEGACY_ONLY_TYPE, 10);

    // then
    final var batch = activatedBatch(LEGACY_ONLY_TYPE);
    assertThat(batch.getJobs().size()).isEqualTo(NUM_OF_LEGACY_ONLY_TYPE_JOBS);
    assertThat(batch.getJobs()).extracting(JobRecordValue::getPriority).containsOnly(0);
    assertThat(batch.getJobKeys()).isSorted();

    // and a second activation returns nothing (no stale entry)
    assertThat(activateJobs(LEGACY_ONLY_TYPE, 10)).isEmpty();
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldActivateNewJobsByPriorityAfterUpgrade() {
    // given/when
    activateJobs(NEW_ONLY_TYPE, 10);

    // then
    final var batch = activatedBatch(NEW_ONLY_TYPE);
    assertThat(batch.getJobs().size()).isEqualTo(NUM_OF_NEW_ONLY_TYPE_JOBS);
    assertThat(batch.getJobs()).extracting(JobRecordValue::getPriority).containsExactly(50, 0, -10);

    // and a second activation returns nothing (no stale entry)
    assertThat(activateJobs(NEW_ONLY_TYPE, 10)).isEmpty();
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldActivateMixedJobsInThreePhaseOrder() {
    // when
    activateJobs(MIXED_TYPE, 10);

    // then
    final var batch = activatedBatch(MIXED_TYPE);
    final var jobs = batch.getJobs();
    final var keys = batch.getJobKeys();
    assertThat(jobs).hasSize(NUM_OF_MIXED_TYPE_LEGACY_JOBS + NUM_OF_MIXED_TYPE_NEW_JOBS);

    // Phase 1: new high-priority job first
    assertThat(jobs.get(0).getPriority()).isEqualTo(50);

    // Phase 2: two legacy jobs, priority=0, keys below any post-upgrade key, ascending key order
    assertThat(jobs.get(1).getPriority()).isEqualTo(0);
    assertThat(jobs.get(2).getPriority()).isEqualTo(0);
    assertThat(keys.get(1)).isLessThan(minNewMixedJobKey);
    assertThat(keys.get(2)).isLessThan(minNewMixedJobKey);
    assertThat(keys.get(1)).isLessThan(keys.get(2));

    // Phase 3: new zero-priority job (post-upgrade key) then negative-priority job
    assertThat(jobs.get(3).getPriority()).isEqualTo(0);
    assertThat(keys.get(3)).isGreaterThanOrEqualTo(minNewMixedJobKey);
    assertThat(jobs.get(4).getPriority()).isEqualTo(-10);

    // and a second activation returns nothing (no stale entry)
    assertThat(activateJobs(MIXED_TYPE, 10)).isEmpty();
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  void shouldServeLegacyJobsInNextBatchWhenHighPriorityFillsBatch() {
    // given a batch filled by exactly the priority>0 (Phase 1) jobs
    final var firstBatch = activateJobs(BOUNDARY_TYPE, NUM_OF_BOUNDARY_NEW_JOBS);

    // then only the new priority-50 job is returned
    assertThat(firstBatch).hasSize(NUM_OF_BOUNDARY_NEW_JOBS);
    assertThat(activatedBatch(BOUNDARY_TYPE).getJobs())
        .extracting(JobRecordValue::getPriority)
        .containsExactly(50);

    // when activating again
    final var secondBatch = activateJobs(BOUNDARY_TYPE, 10);

    // then the legacy (Phase 2) jobs were not dropped by the short-circuit; they are served next,
    // in job-key-ascending order
    assertThat(secondBatch).hasSize(NUM_OF_BOUNDARY_LEGACY_JOBS);
    final var legacyKeys = secondBatch.stream().map(ActivatedJob::getKey).toList();
    assertThat(legacyKeys).isSorted();
    // legacy jobs predate the upgrade, so their keys are below the new priority-50 job's key
    assertThat(legacyKeys.getLast()).isLessThan(firstBatch.getFirst().getKey());

    // and a third activation returns nothing (no stale entry)
    assertThat(activateJobs(BOUNDARY_TYPE, 10)).isEmpty();
  }

  // ── Helpers ──────────────────────────────────────────────────────────────────────────────────

  private void deployAndCreateInstances(
      final String processId, final String jobType, final Integer priority, final int count) {
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(
                "task",
                t -> {
                  t.zeebeJobType(jobType);
                  if (priority != null) {
                    t.zeebeJobPriority(String.valueOf(priority));
                  }
                })
            .endEvent()
            .done();

    state
        .client()
        .newDeployResourceCommand()
        .addProcessModel(model, processId + ".bpmn")
        .send()
        .join();

    for (int i = 0; i < count; i++) {
      state
          .client()
          .newCreateInstanceCommand()
          .bpmnProcessId(processId)
          .latestVersion()
          .send()
          .join();
    }
  }

  /**
   * Blocks until {@code expectedCount} JOB CREATED records for the given job type appear in the
   * broker output — Docker stdout on the old broker, RecordingExporter on the new Spring broker.
   */
  private void awaitJobsCreated(final String jobType, final int expectedCount) {
    Awaitility.await("until " + expectedCount + " JOB CREATED records for type " + jobType)
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(200))
        .until(
            () ->
                state.countLogOccurrences("JOB", "CREATED", "\"" + jobType + "\"")
                    >= expectedCount);
  }

  private List<ActivatedJob> activateJobs(final String jobType, final int maxJobs) {
    return state
        .client()
        .newActivateJobsCommand()
        .jobType(jobType)
        .maxJobsToActivate(maxJobs)
        .requestTimeout(Duration.ofSeconds(2))
        .send()
        .join()
        .getJobs();
  }

  /**
   * Blocks until a {@code JOB_BATCH ACTIVATED} record for the given type is captured by
   * RecordingExporter (enabled on the Spring new broker), then returns its value. The value's
   * {@link JobBatchRecordValue#getJobs()} and {@link JobBatchRecordValue#getJobKeys()} lists are in
   * activation order and correspond index-for-index.
   */
  private JobBatchRecordValue activatedBatch(final String jobType) {
    return RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED)
        .withType(jobType)
        .getFirst()
        .getValue();
  }
}
