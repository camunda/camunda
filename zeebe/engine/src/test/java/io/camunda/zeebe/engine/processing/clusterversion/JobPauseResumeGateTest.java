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
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.GatedCommandId;
import io.camunda.zeebe.protocol.impl.record.value.clusterversion.ClusterVersionRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVersionIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies {@link Capability#JOB_PAUSE_RESUME} (catalog ordinal 19) end-to-end. The capability
 * lands four new intent enum values and one new state value, gated on two axes:
 *
 * <ul>
 *   <li><b>Command admission</b> — the catalog lists {@code GatedCommandId(JOB, PAUSE)} and {@code
 *       GatedCommandId(JOB, RESUME)}. {@code ClusterVersionGate.admits} is consulted by {@code
 *       CommandApiRequestHandler} on the broker's external command API path; that path isn't
 *       exercised by the engine-level test framework (which writes records directly to the log via
 *       {@code EngineRule.writeRecords}), so the admission rejection itself is covered by the
 *       catalog-content assertions here and by broker-layer tests separately. The defense-in-depth
 *       at the engine writer side is in {@code
 *       ResultBuilderBackedTypedCommandWriter.enforceClusterVersionGate}.
 *   <li><b>Applier-version selection</b> — the catalog lists {@code ApplierVersionId(PAUSED, 1)}
 *       and {@code ApplierVersionId(RESUMED, 1)}; below the gate {@code selectVersionFor} returns
 *       {@code -1} for those intents.
 * </ul>
 *
 * The behavior-toggle tests focus on what the engine-level test framework can observe: the full
 * PAUSE/RESUME cycle running above the gate (a worker can pause an activated job, resume it back to
 * ACTIVATED), and the precondition validator's state-machine guardrails (an already-paused job
 * rejects a second PAUSE).
 */
public final class JobPauseResumeGateTest {

  private static final String PROCESS_ID = "process";
  private static final String JOB_TYPE = "test";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldDrivePauseResumeCycleAboveGate() {
    // given — raise ECV so PAUSE/RESUME admission opens and PAUSED/RESUMED appliers are selectable
    raiseToPauseResumeOrdinal();
    final long jobKey = activateAJob();

    // when — PAUSE the activated job
    engine.writeRecords(
        RecordToWrite.command().key(jobKey).job(JobIntent.PAUSE, jobRecordForKey()));

    // then — the PAUSED event reaches the log
    final var pausedEvent =
        RecordingExporter.jobRecords(JobIntent.PAUSED).withRecordKey(jobKey).getFirst();

    // when — RESUME the paused job
    engine.writeRecords(
        RecordToWrite.command().key(jobKey).job(JobIntent.RESUME, jobRecordForKey()));

    // then — the RESUMED event reaches the log and follows PAUSED in log order
    final var resumedEvent =
        RecordingExporter.jobRecords(JobIntent.RESUMED).withRecordKey(jobKey).getFirst();
    assertThat(pausedEvent.getPosition()).isLessThan(resumedEvent.getPosition());

    // and — both follow-up events are stamped with their gated version 1
    assertThat(pausedEvent.getRecordVersion()).isEqualTo(1);
    assertThat(resumedEvent.getRecordVersion()).isEqualTo(1);
  }

  @Test
  public void shouldRejectPauseOnAlreadyPausedJob() {
    // given — raise, activate, pause
    raiseToPauseResumeOrdinal();
    final long jobKey = activateAJob();
    engine.writeRecords(
        RecordToWrite.command().key(jobKey).job(JobIntent.PAUSE, jobRecordForKey()));
    RecordingExporter.jobRecords(JobIntent.PAUSED).withRecordKey(jobKey).getFirst();

    // when — try to PAUSE again
    engine.writeRecords(
        RecordToWrite.command().key(jobKey).job(JobIntent.PAUSE, jobRecordForKey()));

    // then — precondition validator rejects (state is PAUSED, not ACTIVATED)
    final var rejection =
        RecordingExporter.records()
            .filter(r -> r.getRecordType() == RecordType.COMMAND_REJECTION)
            .filter(r -> r.getValueType() == ValueType.JOB)
            .filter(r -> r.getIntent() == JobIntent.PAUSE)
            .filter(r -> r.getKey() == jobKey)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldRejectResumeOnActivatedJob() {
    // given — raise, activate; the job is ACTIVATED, never paused
    raiseToPauseResumeOrdinal();
    final long jobKey = activateAJob();

    // when — try to RESUME a job that was never paused
    engine.writeRecords(
        RecordToWrite.command().key(jobKey).job(JobIntent.RESUME, jobRecordForKey()));

    // then — precondition validator rejects (state is ACTIVATED, not PAUSED)
    final var rejection =
        RecordingExporter.records()
            .filter(r -> r.getRecordType() == RecordType.COMMAND_REJECTION)
            .filter(r -> r.getValueType() == ValueType.JOB)
            .filter(r -> r.getIntent() == JobIntent.RESUME)
            .filter(r -> r.getKey() == jobKey)
            .getFirst();
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldListAdmissionAndApplierGatesInTheCatalog() {
    // The broker-level admission path (CommandApiRequestHandler.admits → ClusterVersionGate)
    // is the only place a JOB:PAUSE / JOB:RESUME from an external client gets rejected below
    // the gate; the engine-level test framework writes records directly to the log and so
    // bypasses that check. Pin the catalog content here so a reviewer who copies the JobPause
    // PoC for the next feature sees what the catalog must declare for admission rejection to
    // fire in production.
    final var resolved = ClusterVersionCatalog.resolveByName("JOB_PAUSE_RESUME");
    assertThat(resolved).isPresent();
    assertThat(resolved.get().commands())
        .as("Both PAUSE and RESUME must be admission-gated at the broker layer")
        .containsExactlyInAnyOrder(
            new GatedCommandId(ValueType.JOB, JobIntent.PAUSE),
            new GatedCommandId(ValueType.JOB, JobIntent.RESUME));
    assertThat(resolved.get().appliers())
        .as("Both PAUSED and RESUMED appliers must be version-gated for defense-in-depth")
        .extracting(a -> a.intent().name())
        .containsExactlyInAnyOrder("PAUSED", "RESUMED");

    // The required ordinal lookup must agree with the capability for both commands and both
    // appliers — this is what selectVersionFor (engine) and ClusterVersionGate.admits (broker)
    // consult when deciding whether to admit.
    assertThat(
            ClusterVersionCatalog.requiredOrdinalForCommand(ValueType.JOB, JobIntent.PAUSE)
                .orElse(-1))
        .isEqualTo(Capability.JOB_PAUSE_RESUME.at());
    assertThat(
            ClusterVersionCatalog.requiredOrdinalForCommand(ValueType.JOB, JobIntent.RESUME)
                .orElse(-1))
        .isEqualTo(Capability.JOB_PAUSE_RESUME.at());
    assertThat(ClusterVersionCatalog.requiredOrdinalForApplier(JobIntent.PAUSED, 1).orElse(-1))
        .isEqualTo(Capability.JOB_PAUSE_RESUME.at());
    assertThat(ClusterVersionCatalog.requiredOrdinalForApplier(JobIntent.RESUMED, 1).orElse(-1))
        .isEqualTo(Capability.JOB_PAUSE_RESUME.at());
  }

  private long activateAJob() {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .done())
        .deploy();
    final long pi = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(pi)
        .withType(JOB_TYPE)
        .getFirst();
    final var batch = engine.jobs().withType(JOB_TYPE).activate();
    return batch.getValue().getJobKeys().get(0);
  }

  private JobRecord jobRecordForKey() {
    return new JobRecord().setType(JOB_TYPE).setRetries(3).setDeadline(1_000L);
  }

  private void raiseToPauseResumeOrdinal() {
    final int target = Capability.JOB_PAUSE_RESUME.at();
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.RAISE,
                new ClusterVersionRecord().setLine(810).setOrdinal(target)));
    RecordingExporter.records()
        .filter(r -> r.getValueType() == ValueType.CLUSTER_VERSION)
        .filter(r -> r.getIntent() == ClusterVersionIntent.APPLIED)
        .filter(r -> ((ClusterVersionRecord) r.getValue()).getOrdinal() == target)
        .getFirst();
  }
}
