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
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVersionIntent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ReservationOrigin;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies the {@link Capability#JOB_BATCH_RESERVATION_ORIGIN} gate end-to-end. Above the gate
 * {@code JobBatchActivateProcessor} stamps {@code reservationOrigin = WORKER_REQUEST} on the {@code
 * JobBatchRecord}; below the gate the field is left at its default {@code UNSPECIFIED}.
 *
 * <p>The new field is forward-compatible at the wire level — old binaries skip the unknown property
 * name during MsgPack decode and the accessor returns the enum's default {@code UNSPECIFIED}, so an
 * old-binary follower replaying a new-leader's record never crashes on this field. The hazard that
 * the gate guards against is the future extension of {@link ReservationOrigin} with a third value,
 * which would re-introduce the {@code Enum.valueOf} crash mode documented by the {@code
 * JobKind.MAINTENANCE} demo at ordinal 17.
 */
public final class JobBatchReservationOriginGateTest {

  private static final String PROCESS_ID = "process";
  private static final String JOB_TYPE = "test";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldLeaveReservationOriginUnspecifiedBelowGate() {
    // given — fresh engine at BASELINE, gate at ordinal 18 is closed
    deployProcessAndCreateJob();

    // when
    engine.jobs().withType(JOB_TYPE).activate();

    // then — the ACTIVATED batch record's reservationOrigin field defaults to UNSPECIFIED
    // (the field is absent from the on-wire record because the producer skipped the setter)
    final var activated =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED).withType(JOB_TYPE).getFirst();

    assertThat(activated.getValue().getReservationOrigin())
        .as("Below the gate, the new field must remain at its UNSPECIFIED default")
        .isEqualTo(ReservationOrigin.UNSPECIFIED);
  }

  @Test
  public void shouldStampWorkerRequestAboveGate() {
    // given — raise ECV to the reservation-origin gate
    raiseToReservationOriginOrdinal();
    deployProcessAndCreateJob();

    // when
    engine.jobs().withType(JOB_TYPE).activate();

    // then — the producer stamped reservationOrigin = WORKER_REQUEST
    final var activated =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED).withType(JOB_TYPE).getFirst();

    assertThat(activated.getValue().getReservationOrigin())
        .as("Above the gate, the new field carries the meaningful WORKER_REQUEST value")
        .isEqualTo(ReservationOrigin.WORKER_REQUEST);
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

  private void raiseToReservationOriginOrdinal() {
    final int target = Capability.JOB_BATCH_RESERVATION_ORIGIN.at();
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
