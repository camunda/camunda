/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clusterversion;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies that {@link Capability#JOB_PRIORITIZATION} gates the priority-update API (issue #50567 /
 * PR #55317). When the capability is inactive, sending a {@code JobIntent.UPDATE} command whose
 * changeset includes {@code PRIORITY} is rejected outright — the new {@code PRIORITY_UPDATED} event
 * intent must never reach the log under low ECV, otherwise older replicas without the intent would
 * fail replay.
 */
public final class JobPrioritizationGateTest {

  private static final String PROCESS_ID = "process";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  // intentionally NOT calling withInitialClusterVersionAtMax() — the gate stays closed

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectPriorityUpdateWhenCapabilityInactive() {
    // given — a fresh engine at BASELINE ordinal (JOB_PRIORITIZATION inactive). Create a job.
    final String jobType = Strings.newRandomValidBpmnId();
    final long jobKey = engine.createJob(jobType, PROCESS_ID).getKey();

    // when — request a priority update via the JobIntent.UPDATE command
    final var rejection =
        engine
            .job()
            .withKey(jobKey)
            .withPriority(10)
            .withChangeset(Set.of("priority"))
            .expectRejection()
            .update();

    // then — rejection names the capability and points at /v2/cluster-version/raise. The
    // rejection itself proves PRIORITY_UPDATED was never emitted — the processor's behavioural
    // gate fires before any state write, and the applier-version gate
    // (selectVersionFor(PRIORITY_UPDATED) returns -1 below ordinal 13) would block any
    // accidentally-attempted emission as the second line of defense.
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .contains(Capability.JOB_PRIORITIZATION.name())
        .contains("cluster-version/raise");
  }

  @Test
  public void shouldRejectMixedChangesetIfPriorityIsIncluded() {
    // given — a job exists; we'll try to update retries + priority in one command
    final String jobType = Strings.newRandomValidBpmnId();
    final long jobKey = engine.createJob(jobType, PROCESS_ID).getKey();

    // when — UPDATE command requests both retries (legacy, ungated) AND priority (gated)
    final var rejection =
        engine
            .job()
            .withKey(jobKey)
            .withRetries(5)
            .withPriority(10)
            .withChangeset(Set.of("retries", "priority"))
            .expectRejection()
            .update();

    // then — the whole command is rejected atomically. Because validation runs before any state
    // write (pass 1 in JobUpdateProcessor), the retries change is not applied — the rejection
    // itself proves no RETRIES_UPDATED event was emitted for this command.
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains(Capability.JOB_PRIORITIZATION.name());
  }
}
