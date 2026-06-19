/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clusterversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;
import io.camunda.zeebe.protocol.impl.record.value.clusterversion.ClusterVersionRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVersionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

/**
 * Defense-in-depth for the cancel-execution-listener feature (PR #46880, gated by {@link
 * Capability#CANCEL_EXECUTION_LISTENER}).
 *
 * <p>Two layers must work independently:
 *
 * <ol>
 *   <li><b>Deployment-admission gate.</b> Deploying a BPMN that declares cancel listeners is
 *       rejected outright when the capability is inactive. The operator sees a clear "raise first"
 *       message instead of having listeners silently no-op at runtime.
 *   <li><b>Runtime gate.</b> Even when a deployment slipped through (e.g. capability was raised at
 *       deploy time, then suppressed before termination), the {@code ProcessProcessor} still takes
 *       the legacy termination path — no {@code CONTINUE_TERMINATING_ELEMENT}, no listener jobs —
 *       so a follower running pre-PR code can still replay the stream.
 * </ol>
 */
public final class CancelExecutionListenerGateTest {

  private static final String PROCESS_ID = "process";
  private static final String CANCEL_EL_TYPE = "cancel-listener";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectDeploymentWhenCapabilityInactive() {
    // given — a fresh engine at BASELINE ordinal (gate closed). Build a process whose
    // <process> element declares a cancel execution listener.
    final var bpmn =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .zeebeCancelExecutionListener(CANCEL_EL_TYPE)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("svc"))
            .endEvent()
            .done();

    // when
    final var rejection = engine.deployment().expectRejection().withXmlResource(bpmn).deploy();

    // then — the rejection points at the capability and tells the operator to raise
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .contains(Capability.CANCEL_EXECUTION_LISTENER.name())
        .contains("cluster-version/raise");
  }

  @Test
  public void shouldAcceptDeploymentAfterRaisingClusterVersion() {
    // given — raise to the cancel-listener ordinal so the deployment gate opens
    raiseToCancelListenerOrdinal();

    // when
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeCancelExecutionListener(CANCEL_EL_TYPE)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("svc"))
                .endEvent()
                .done())
        .deploy();

    // then — the deployment is CREATED (the engine accepted it)
    RecordingExporter.deploymentRecords(DeploymentIntent.CREATED).getFirst();
  }

  @Test
  public void shouldFallBackToLegacyTerminationWhenCapabilitySuppressed() {
    // given — raise so the deployment is accepted...
    raiseToCancelListenerOrdinal();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeCancelExecutionListener(CANCEL_EL_TYPE)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("svc"))
                .endEvent()
                .done())
        .deploy();

    // ...then SUPPRESS the capability so the runtime gate closes again. This is the
    // rollback-lite path: the operator decides at runtime that the feature isn't safe and toggles
    // it off without lowering the ECV.
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.SUPPRESS_FLAG,
                new ClusterVersionRecord()
                    .setFlagName(Capability.CANCEL_EXECUTION_LISTENER.name())));
    RecordingExporter.records()
        .filter(r -> r.getValueType() == ValueType.CLUSTER_VERSION)
        .filter(r -> r.getIntent() == ClusterVersionIntent.FLAG_SUPPRESSED)
        .filter(
            r ->
                Capability.CANCEL_EXECUTION_LISTENER
                    .name()
                    .equals(((ClusterVersionRecord) r.getValue()).getFlagName()))
        .getFirst();

    final long pi = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(pi)
        .withType("svc")
        .getFirst();

    // when — cancel the instance with the runtime gate closed via suppress
    engine.processInstance().withInstanceKey(pi).expectTerminating().cancel();

    // then — the instance terminates via the legacy path: no CONTINUE_TERMINATING_ELEMENT
    // command, no execution-listener job. Record stream matches a pre-PR broker.
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(pi)
                .limitToProcessInstanceTerminated())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED))
        .doesNotContain(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.CONTINUE_TERMINATING_ELEMENT));

    final boolean cancelListenerJobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(pi)
            .limit(1)
            .findFirst()
            .filter(j -> j.getValue().getJobKind() == JobKind.EXECUTION_LISTENER)
            .filter(j -> CANCEL_EL_TYPE.equals(j.getValue().getType()))
            .isPresent();
    assertThat(cancelListenerJobCreated)
        .as("suppress wins — no cancel listener job is created at termination time")
        .isFalse();
  }

  @Test
  public void shouldEmitCancelListenerJobsWhenCapabilityFullyActive() {
    // given — raise (deployment passes, runtime gate open)
    raiseToCancelListenerOrdinal();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .zeebeCancelExecutionListener(CANCEL_EL_TYPE)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("svc"))
                .endEvent()
                .done())
        .deploy();
    final long pi = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(pi)
        .withType("svc")
        .getFirst();

    // when
    engine.processInstance().withInstanceKey(pi).expectTerminating().cancel();

    // then — the new termination path runs: CONTINUE_TERMINATING_ELEMENT command and a cancel
    // listener job appear
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.CONTINUE_TERMINATING_ELEMENT)
        .withProcessInstanceKey(pi)
        .getFirst();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(pi)
        .filter(r -> r.getValue().getJobKind() == JobKind.EXECUTION_LISTENER)
        .filter(r -> CANCEL_EL_TYPE.equals(r.getValue().getType()))
        .getFirst();
  }

  private void raiseToCancelListenerOrdinal() {
    final int target = Capability.CANCEL_EXECUTION_LISTENER.at();
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
