/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BufferedCommandRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * Each test compares a never-suspended {@code controlKey} instance against a {@code testKey}
 * instance suspended mid-flight and resumed.
 */
public final class SuspendResumeEquivalenceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final Set<ProcessInstanceIntent> ELEMENT_LIFECYCLE_INTENTS =
      EnumSet.of(
          ProcessInstanceIntent.ELEMENT_ACTIVATING,
          ProcessInstanceIntent.ELEMENT_ACTIVATED,
          ProcessInstanceIntent.ELEMENT_COMPLETING,
          ProcessInstanceIntent.ELEMENT_COMPLETED,
          ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN);

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldReachSameStateWithTimerCatchEvent() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var jobType = Strings.newRandomValidBpmnId();
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT1S"))
            .serviceTask("task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(model).deploy();

    final long controlKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.timerRecords(TimerIntent.CREATED).withProcessInstanceKey(controlKey).await();
    ENGINE.increaseTime(Duration.ofSeconds(2));
    ENGINE.job().withType(jobType).ofInstance(controlKey).complete();

    final long testKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.timerRecords(TimerIntent.CREATED).withProcessInstanceKey(testKey).await();
    ENGINE.processInstance().withInstanceKey(testKey).suspend();

    // when - await the checker's rejected TRIGGER so resume deterministically exercises the
    // stranded-timer rescan, not a race with the checker's own regular polling
    ENGINE.increaseTime(Duration.ofSeconds(2));
    RecordingExporter.timerRecords(TimerIntent.TRIGGER)
        .onlyCommandRejections()
        .withProcessInstanceKey(testKey)
        .await();
    ENGINE.processInstance().withInstanceKey(testKey).resume();
    ENGINE.job().withType(jobType).ofInstance(testKey).complete();

    // then
    assertThat(elementLifecycle(testKey))
        .containsExactlyInAnyOrderElementsOf(elementLifecycle(controlKey));
  }

  @Test
  public void shouldReachSameStateWithMessageCatchEvent() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var jobType = Strings.newRandomValidBpmnId();
    final var messageName = Strings.newRandomValidBpmnId();
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .intermediateCatchEvent(
                "msgCatch",
                c ->
                    c.message(
                        m -> m.name(messageName).zeebeCorrelationKeyExpression("correlationKey")))
            .serviceTask("task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(model).deploy();

    final long controlKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("correlationKey", "control")
            .create();
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(controlKey)
        .await();
    ENGINE.message().withName(messageName).withCorrelationKey("control").publish();
    ENGINE.job().withType(jobType).ofInstance(controlKey).complete();

    final long testKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("correlationKey", "test")
            .create();
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(testKey)
        .await();
    ENGINE.processInstance().withInstanceKey(testKey).suspend();
    // suspending tears down the message-side subscription; await it so publishing below reliably
    // buffers instead of racing the teardown
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
        .withProcessInstanceKey(testKey)
        .await();

    // when - the TTL must outlast suspension so resume can re-create the subscription and
    // correlate it
    ENGINE
        .message()
        .withName(messageName)
        .withCorrelationKey("test")
        .withTimeToLive(Duration.ofMinutes(1))
        .publish();
    ENGINE.processInstance().withInstanceKey(testKey).resume();
    ENGINE.job().withType(jobType).ofInstance(testKey).complete();

    // then
    assertThat(elementLifecycle(testKey))
        .containsExactlyInAnyOrderElementsOf(elementLifecycle(controlKey));
  }

  @Test
  public void shouldReachSameStateWithParallelGateway() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var jobTypeA = Strings.newRandomValidBpmnId();
    final var jobTypeB = Strings.newRandomValidBpmnId();
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .parallelGateway("fork")
            .serviceTask("taskA", t -> t.zeebeJobType(jobTypeA))
            .parallelGateway("join")
            .endEvent()
            .moveToNode("fork")
            .serviceTask("taskB", t -> t.zeebeJobType(jobTypeB))
            .connectTo("join")
            .done();
    ENGINE.deployment().withXmlResource(model).deploy();

    final long controlKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    ENGINE.job().withType(jobTypeA).ofInstance(controlKey).complete();
    ENGINE.job().withType(jobTypeB).ofInstance(controlKey).complete();

    final long testKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(testKey)
        .limit(2)
        .await();

    // when
    ENGINE.processInstance().withInstanceKey(testKey).suspend();
    ENGINE.processInstance().withInstanceKey(testKey).resume();
    ENGINE.job().withType(jobTypeA).ofInstance(testKey).complete();
    ENGINE.job().withType(jobTypeB).ofInstance(testKey).complete();

    // then
    assertThat(elementLifecycle(testKey))
        .containsExactlyInAnyOrderElementsOf(elementLifecycle(controlKey));
  }

  @Test
  public void shouldReachSameStateWithCallActivity() {
    // given
    final var childProcessId = Strings.newRandomValidBpmnId();
    final var childJobType = Strings.newRandomValidBpmnId();
    final BpmnModelInstance childModel =
        Bpmn.createExecutableProcess(childProcessId)
            .startEvent()
            .serviceTask("childTask", t -> t.zeebeJobType(childJobType))
            .endEvent()
            .done();

    final var parentProcessId = Strings.newRandomValidBpmnId();
    final var parentJobType = Strings.newRandomValidBpmnId();
    final BpmnModelInstance parentModel =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity("call", c -> c.zeebeProcessId(childProcessId))
            .serviceTask("parentTask", t -> t.zeebeJobType(parentJobType))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(childModel).withXmlResource(parentModel).deploy();

    final long controlKey = ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();
    final long controlChildKey = awaitChildInstanceKey(controlKey);
    ENGINE.job().withType(childJobType).ofInstance(controlChildKey).complete();
    ENGINE.job().withType(parentJobType).ofInstance(controlKey).complete();

    final long testKey = ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();
    final long testChildKey = awaitChildInstanceKey(testKey);

    // when - the child's completion signal buffers in the suspended parent scope; await that
    // before resuming so the drain path is deterministically exercised
    ENGINE.processInstance().withInstanceKey(testKey).suspend();
    ENGINE.job().withType(childJobType).ofInstance(testChildKey).complete();
    RecordingExporter.records()
        .withValueType(ValueType.BUFFERED_COMMAND)
        .withIntent(BufferedCommandIntent.BUFFERED)
        .filter(r -> ((BufferedCommandRecordValue) r.getValue()).getProcessInstanceKey() == testKey)
        .await();
    ENGINE.processInstance().withInstanceKey(testKey).resume();
    ENGINE.job().withType(parentJobType).ofInstance(testKey).complete();

    // then - the child runs as its own instance, so compare only the parent's lifecycle
    assertThat(elementLifecycle(testKey))
        .containsExactlyInAnyOrderElementsOf(elementLifecycle(controlKey));
  }

  private static long awaitChildInstanceKey(final long parentInstanceKey) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withParentProcessInstanceKey(parentInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst()
        .getValue()
        .getProcessInstanceKey();
  }

  private static List<String> elementLifecycle(final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(processInstanceKey)
        .limitToProcessInstanceCompleted()
        .filter(r -> ELEMENT_LIFECYCLE_INTENTS.contains(r.getIntent()))
        .map(r -> r.getIntent() + ":" + r.getValue().getElementId())
        .toList();
  }
}
