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
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;
import io.camunda.zeebe.protocol.impl.record.value.clusterversion.ClusterVersionRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVersionIntent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies the {@link Capability#JOB_BATCH_RESERVATION_STATE} gate end-to-end through {@code
 * JobBatchActivateProcessor} and {@code EventAppliers.selectVersionFor}.
 *
 * <p><b>Below the gate</b> batch activation must emit only the legacy {@link
 * JobBatchIntent#ACTIVATED} (record version {@code 1}) and the v=1 of {@code
 * JobBatchActivatedApplier} moves jobs straight from {@code ACTIVATABLE → ACTIVATED}. Record stream
 * is byte-identical to a pre-feature broker.
 *
 * <p><b>Above the gate</b> the leader emits {@link JobBatchIntent#RESERVED} (whose applier moves
 * jobs to {@code RESERVED}) immediately followed by {@link JobBatchIntent#ACTIVATED} (whose v=2
 * applier moves them to {@code ACTIVATED}). The ACTIVATED record carries version {@code 2} — {@code
 * selectVersionFor} stamps it automatically.
 */
public final class JobBatchReservationGateTest {

  private static final String PROCESS_ID = "process";
  private static final String JOB_TYPE = "test";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldEmitOnlyActivatedBelowGate() {
    // given — fresh engine at BASELINE, gate at ordinal 16 is closed
    deployProcessAndCreateJob();

    // when
    engine.jobs().withType(JOB_TYPE).activate();

    // then — collecting up to and including the ACTIVATED record gives us a short-circuit on
    // a known terminal event; within that window no RESERVED record may exist
    final var batchRecords =
        RecordingExporter.jobBatchRecords()
            .withType(JOB_TYPE)
            .limit(r -> r.getIntent() == JobBatchIntent.ACTIVATED)
            .toList();

    assertThat(batchRecords)
        .extracting(Record::getIntent)
        .as("RESERVED must not appear below the JOB_BATCH_RESERVATION_STATE gate")
        .doesNotContain(JobBatchIntent.RESERVED)
        .contains(JobBatchIntent.ACTIVATED);

    // and — the ACTIVATED record carries the legacy v=1 stamp, so a pre-feature follower's
    // applier registry can still dispatch it
    final var activated =
        batchRecords.stream()
            .filter(r -> r.getIntent() == JobBatchIntent.ACTIVATED)
            .findFirst()
            .orElseThrow();
    assertThat(activated.getRecordVersion())
        .as("Below the gate, ACTIVATED records must be stamped v=1")
        .isEqualTo(1);
  }

  @Test
  public void shouldEmitReservedBeforeActivatedAboveGate() {
    // given — raise ECV to the reservation gate
    raiseToReservationOrdinal();
    deployProcessAndCreateJob();

    // when
    engine.jobs().withType(JOB_TYPE).activate();

    // then — RESERVED appears in the stream and precedes ACTIVATED on the same batch key
    final var reserved =
        RecordingExporter.jobBatchRecords(JobBatchIntent.RESERVED).withType(JOB_TYPE).getFirst();
    final var activated =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED).withType(JOB_TYPE).getFirst();

    assertThat(reserved.getKey())
        .as("RESERVED and ACTIVATED share the same batch key")
        .isEqualTo(activated.getKey());
    assertThat(reserved.getPosition())
        .as("RESERVED must precede ACTIVATED in the log")
        .isLessThan(activated.getPosition());

    // and — selectVersionFor stamps ACTIVATED at v=2 once the gate is open, so the new
    // RESERVED → ACTIVATED applier path runs in place of the legacy v=1
    assertThat(activated.getRecordVersion())
        .as("Above the gate, ACTIVATED records must be stamped v=2")
        .isEqualTo(2);
  }

  private void deployProcessAndCreateJob() {
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
  }

  private void raiseToReservationOrdinal() {
    final int target = Capability.JOB_BATCH_RESERVATION_STATE.at();
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
