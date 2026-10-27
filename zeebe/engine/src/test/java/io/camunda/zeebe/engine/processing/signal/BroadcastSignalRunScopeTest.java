/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.signal;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.TagUtil;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/** Phase-1 acceptance tests for tag-scoped signal broadcast (ADR 0014). */
public class BroadcastSignalRunScopeTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String SIGNAL_NAME = "s";
  private static final String CATCH_ID = "catch";
  private static final String RUN_A = "runA";
  private static final String RUN_B = "runB";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static String catchProcessId(final String suffix) {
    return "catch-process-" + suffix;
  }

  private long deployCatcherAndCreate(final String processId, final String runScope) {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(CATCH_ID)
                .signal(SIGNAL_NAME)
                .endEvent()
                .done())
        .deploy();
    final var key =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withTags(TagUtil.runScopeTag(runScope))
            .create();
    // wait until the catch event is active (its subscription is open) before broadcasting
    RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(key)
        .withElementId(CATCH_ID)
        .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .getFirst();
    return key;
  }

  @Test
  public void shouldWakeOnlyMatchingRunCatchEvent() {
    // given two instances of the same definition waiting on the same signal, tagged for two runs
    final var processId = catchProcessId("wake");
    final long piA = deployCatcherAndCreate(processId, RUN_A);
    final long piB = deployCatcherAndCreate(processId, RUN_B);

    // when a scoped broadcast is sent for run A
    ENGINE.signal().withSignalName(SIGNAL_NAME).withRunScope(RUN_A).broadcast();

    // then run A's catch event completes
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(piA)
                .limitToProcessInstanceCompleted()
                .withElementId(CATCH_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isTrue();

    // and run B is untouched: it only completes after its own scoped broadcast, proving the first
    // broadcast did not wake it
    final var runBBroadcast =
        ENGINE.signal().withSignalName(SIGNAL_NAME).withRunScope(RUN_B).broadcast();
    final var piBCatchCompleted =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(piB)
            .withElementId(CATCH_ID)
            .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();
    assertThat(piBCatchCompleted.getPosition()).isGreaterThan(runBBroadcast.getPosition());
  }

  @Test
  public void shouldWakeBothRunsWhenScopeUnset() {
    // given two instances tagged for two runs
    final var processId = catchProcessId("regression");
    final long piA = deployCatcherAndCreate(processId, RUN_A);
    final long piB = deployCatcherAndCreate(processId, RUN_B);

    // when an unscoped (global) broadcast is sent
    ENGINE.signal().withSignalName(SIGNAL_NAME).broadcast();

    // then both instances complete, identical to today's behaviour
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(piA)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.PROCESS)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(piB)
                .limitToProcessInstanceCompleted()
                .withElementType(BpmnElementType.PROCESS)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldStampRunScopeTagOnSignalStartInstance() {
    // given a signal-start process
    final var processId = catchProcessId("start");
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .signal(SIGNAL_NAME)
                .endEvent()
                .done())
        .deploy();
    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withSignalName(SIGNAL_NAME)
        .getFirst();

    // when a scoped broadcast starts an instance
    ENGINE.signal().withSignalName(SIGNAL_NAME).withRunScope(RUN_A).broadcast();

    // then the newly started instance is stamped with the reserved run-scope tag
    final Record<ProcessInstanceRecordValue> created =
        RecordingExporter.processInstanceRecords()
            .withBpmnProcessId(processId)
            .withElementType(BpmnElementType.PROCESS)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .getFirst();
    assertThat(created.getValue().getTags()).containsExactly(TagUtil.runScopeTag(RUN_A));
  }

  @Test
  public void shouldScopeInternalThrowToOwnRun() {
    // given a thrower that broadcasts its own signal, and two catchers tagged for two runs
    final var throwerId = "thrower";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(throwerId)
                .startEvent()
                .intermediateThrowEvent("throw", t -> t.signal(SIGNAL_NAME))
                .endEvent()
                .done())
        .deploy();
    final var catcherId = catchProcessId("throw");
    final long piA = deployCatcherAndCreate(catcherId, RUN_A);
    final long piB = deployCatcherAndCreate(catcherId, RUN_B);

    // when a thrower tagged for run A runs
    ENGINE
        .processInstance()
        .ofBpmnProcessId(throwerId)
        .withTags(TagUtil.runScopeTag(RUN_A))
        .create();

    // then only run A's catcher completes; run B only completes after its own scoped broadcast
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(piA)
                .limitToProcessInstanceCompleted()
                .withElementId(CATCH_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isTrue();

    final var runBBroadcast =
        ENGINE.signal().withSignalName(SIGNAL_NAME).withRunScope(RUN_B).broadcast();
    final var piBCatchCompleted =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(piB)
            .withElementId(CATCH_ID)
            .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();
    assertThat(piBCatchCompleted.getPosition()).isGreaterThan(runBBroadcast.getPosition());
  }

  @Test
  public void shouldResolveCatchInsideCallActivityViaRootTag() {
    // given a parent that calls a child whose catch event waits on the signal
    final var childId = "child-process";
    final var parentId = "parent-process";
    ENGINE
        .deployment()
        .withXmlResource(
            "child.bpmn",
            Bpmn.createExecutableProcess(childId)
                .startEvent()
                .intermediateCatchEvent(CATCH_ID)
                .signal(SIGNAL_NAME)
                .endEvent()
                .done())
        .withXmlResource(
            "parent.bpmn",
            Bpmn.createExecutableProcess(parentId)
                .startEvent()
                .callActivity("call", c -> c.zeebeProcessId(childId))
                .endEvent()
                .done())
        .deploy();

    // when the parent (tagged for run A) is created and its child subscribes
    final long parentKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(parentId)
            .withTags(TagUtil.runScopeTag(RUN_A))
            .create();
    final var childSubscription =
        RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
            .withSignalName(SIGNAL_NAME)
            .getFirst();

    // then the child subscription carries the run scope propagated from the root
    assertThat(childSubscription.getValue().getRunScope()).isEqualTo(RUN_A);

    // and a scoped broadcast for run A wakes the child's catch event
    ENGINE.signal().withSignalName(SIGNAL_NAME).withRunScope(RUN_A).broadcast();
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withParentProcessInstanceKey(parentKey)
                .limitToProcessInstanceCompleted()
                .withElementId(CATCH_ID)
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .exists())
        .isTrue();
  }
}
