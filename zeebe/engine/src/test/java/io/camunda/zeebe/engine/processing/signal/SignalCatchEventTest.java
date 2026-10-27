/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.SignalClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.ExecuteCommandResponseDecoder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class SignalCatchEventTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "wf";
  private static final String ELEMENT_ID = "catch";
  private static final String SIGNAL_NAME = "signal";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final SignalClient signalClient = ENGINE.signal().withSignalName(SIGNAL_NAME);

  @Test
  public void shouldTriggerIntermediateCatchEvent() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .intermediateCatchEvent(ELEMENT_ID)
            .signal(SIGNAL_NAME)
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
                .withSignalName(SIGNAL_NAME)
                .exists())
        .isTrue();

    // when
    signalClient.broadcast();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldTriggerInterruptingBoundaryEvent() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", b -> b.zeebeJobType("type"))
            .boundaryEvent(ELEMENT_ID)
            .signal(SIGNAL_NAME)
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
                .withSignalName(SIGNAL_NAME)
                .exists())
        .isTrue();

    // when
    signalClient.broadcast();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("task", ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple("task", ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldTriggerNonInterruptingBoundaryEvent() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", b -> b.zeebeJobType("type"))
            .boundaryEvent(ELEMENT_ID)
            .signal(SIGNAL_NAME)
            .cancelActivity(false)
            .endEvent("end")
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
                .withSignalName(SIGNAL_NAME)
                .exists())
        .isTrue();

    signalClient.broadcast();

    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType("type").activate();

    // when
    ENGINE.job().withKey(batchRecord.getValue().getJobKeys().get(0)).complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("task", ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple("task", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("end", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("end", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("task", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("task", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCloseSignalSubscription() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", b -> b.zeebeJobType("type"))
            .boundaryEvent(ELEMENT_ID)
            .signal(SIGNAL_NAME)
            .endEvent("end")
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
                .withSignalName(SIGNAL_NAME)
                .exists())
        .isTrue();

    final Record<JobBatchRecordValue> batchRecord = ENGINE.jobs().withType("type").activate();

    // when
    ENGINE.job().withKey(batchRecord.getValue().getJobKeys().get(0)).complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("task", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("task", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.DELETED)
                .withSignalName(SIGNAL_NAME)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldTriggerSignalCatchEventAttachedToEventBasedGateway() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .eventBasedGateway("event_based_gateway")
            .intermediateCatchEvent(ELEMENT_ID)
            .signal(SIGNAL_NAME)
            .endEvent()
            .moveToLastGateway()
            .intermediateCatchEvent()
            .timerWithDuration(Duration.ofMinutes(10))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
                .withSignalName(SIGNAL_NAME)
                .exists())
        .isTrue();

    // when
    signalClient.broadcast();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void
      shouldRejectDeploymentSignalCatchEventWithSameSignalNameAttachedToEventBasedGateway() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .eventBasedGateway("event_based_gateway")
            .intermediateCatchEvent(ELEMENT_ID)
            .signal(SIGNAL_NAME)
            .endEvent()
            .moveToLastGateway()
            .intermediateCatchEvent()
            .signal(SIGNAL_NAME)
            .endEvent()
            .moveToLastGateway()
            .intermediateCatchEvent()
            .timerWithDuration(Duration.ofMinutes(10))
            .endEvent()
            .done();

    // when
    final Record<DeploymentRecordValue> rejectedDeployment =
        ENGINE.deployment().withXmlResource(process).expectRejection().deploy();

    // then
    Assertions.assertThat(rejectedDeployment)
        .hasKey(ExecuteCommandResponseDecoder.keyNullValue())
        .hasRecordType(RecordType.COMMAND_REJECTION)
        .hasIntent(DeploymentIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejectedDeployment.getRejectionReason())
        .contains("Element: event_based_gateway")
        .contains(
            "ERROR: Multiple signal event definitions with the same name 'signal' are not allowed.");
  }
}
