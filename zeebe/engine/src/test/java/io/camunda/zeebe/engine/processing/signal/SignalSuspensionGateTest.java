/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.signal;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.SuspensionState.State;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class SignalSuspensionGateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String ELEMENT_ID = "catch";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldSkipSignalActivationForSuspendedTarget() {
    // given - an instance waiting on a signal catch event, then suspended
    final String processId = Strings.newRandomValidBpmnId();
    final String signalName = Strings.newRandomValidBpmnId();
    final long processInstanceKey =
        deployAndStartProcessWithSignalCatchEvent(processId, signalName);
    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withSignalName(signalName)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    final Record<?> broadcast = ENGINE.signal().withSignalName(signalName).broadcast();

    // then - the broadcast still succeeds
    Assertions.assertThat(broadcast).hasIntent(SignalIntent.BROADCASTED);

    // and - the catch event, already ACTIVATED on start while entering the wait state, never
    // completes: completion is what a signal trigger would have caused
    assertThat(catchEventCompleted(processInstanceKey)).isFalse();
  }

  @Test
  public void shouldActivateSignalForActiveTargetWhenAnotherTargetSuspended() {
    // given - two instances of different processes waiting on the same signal, one suspended
    final String signalName = Strings.newRandomValidBpmnId();
    final String suspendedProcessId = Strings.newRandomValidBpmnId();
    final String activeProcessId = Strings.newRandomValidBpmnId();
    final long suspendedInstanceKey =
        deployAndStartProcessWithSignalCatchEvent(suspendedProcessId, signalName);
    final long activeInstanceKey =
        deployAndStartProcessWithSignalCatchEvent(activeProcessId, signalName);
    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withBpmnProcessId(suspendedProcessId)
        .await();
    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withBpmnProcessId(activeProcessId)
        .await();
    ENGINE.processInstance().withInstanceKey(suspendedInstanceKey).suspend();

    // when
    ENGINE.signal().withSignalName(signalName).broadcast();

    // then - the active instance's catch event fires and the process completes
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(activeInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // and - the suspended instance's catch event is skipped
    assertThat(catchEventCompleted(suspendedInstanceKey)).isFalse();
  }

  @Test
  public void shouldSkipSignalActivationForResumingTarget() {
    // given - the marker is seeded directly at RESUMING to catch the drain window deterministically
    // (a real drain that starts and finishes immediately never exposes it since there is nothing
    // buffered here to drain)
    final String processId = Strings.newRandomValidBpmnId();
    final String signalName = Strings.newRandomValidBpmnId();
    final long processInstanceKey =
        deployAndStartProcessWithSignalCatchEvent(processId, signalName);
    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withSignalName(signalName)
        .await();
    ((MutableProcessingState) ENGINE.getProcessingState())
        .getSuspensionState()
        .setSuspensionState(processInstanceKey, State.RESUMING);

    // when
    final Record<?> broadcast = ENGINE.signal().withSignalName(signalName).broadcast();

    // then - the broadcast still succeeds, but activating inline would race the buffered-command
    // drain, so the catch event is skipped just like while SUSPENDED
    Assertions.assertThat(broadcast).hasIntent(SignalIntent.BROADCASTED);
    assertThat(catchEventCompleted(processInstanceKey)).isFalse();
  }

  @Test
  public void shouldReceiveSignalAfterSuspendAndResume() {
    // given - an instance suspended and then resumed while waiting on a signal catch event
    final String processId = Strings.newRandomValidBpmnId();
    final String signalName = Strings.newRandomValidBpmnId();
    final long processInstanceKey =
        deployAndStartProcessWithSignalCatchEvent(processId, signalName);
    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withSignalName(signalName)
        .await();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // when - a signal is broadcast after resume
    ENGINE.signal().withSignalName(signalName).broadcast();

    // then - the catch event fires and the process completes normally
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
  }

  private static boolean catchEventCompleted(final long processInstanceKey) {
    return RecordingExporter.<Boolean>expectNoMatchingRecords(
        records ->
            records
                .processInstanceRecords()
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETING)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(ELEMENT_ID)
                .exists());
  }

  private long deployAndStartProcessWithSignalCatchEvent(
      final String processId, final String signalName) {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(ELEMENT_ID)
                .signal(signalName)
                .endEvent()
                .done())
        .deploy();
    return ENGINE.processInstance().ofBpmnProcessId(processId).create();
  }
}
