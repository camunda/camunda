/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.VariableOperationType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import org.assertj.core.groups.Tuple;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Checks the source of the variables that an event payload writes. A job result is a payload, and
 * so is the payload of a message. The source names the operation, and the element that caught the
 * payload.
 */
public final class EventPayloadVariableSourceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  /** The source of a write that did not come from an event payload. */
  private static final Tuple NO_SOURCE = tuple(VariableOperationType.UNKNOWN, -1L, "");

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final String processId = Strings.newRandomValidBpmnId();
  private final String jobType = Strings.newRandomValidBpmnId();
  private final String messageName = Strings.newRandomValidBpmnId();

  @Test
  public void shouldSourceCreatedVariableOfJobCompletionFromJobCompletion() {
    // given
    final long processInstanceKey =
        createInstance(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done());
    final long taskKey = elementInstanceKey(processInstanceKey, "task");

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(jobType).withVariable("x", 1).complete();

    // then
    assertThat(variableRecords(processInstanceKey, "x"))
        .extracting(Record::getIntent, EventPayloadVariableSourceTest::sourceOf)
        .containsExactly(
            tuple(
                VariableIntent.CREATED,
                tuple(VariableOperationType.JOB_COMPLETION, taskKey, "task")));
  }

  @Test
  public void shouldSourceUpdatedVariableOfJobCompletionFromJobCompletion() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("x", 1).create();
    final long taskKey = elementInstanceKey(processInstanceKey, "task");

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(jobType).withVariable("x", 2).complete();

    // then
    assertThat(variableRecords(processInstanceKey, "x"))
        .filteredOn(record -> record.getIntent() == VariableIntent.UPDATED)
        .extracting(EventPayloadVariableSourceTest::sourceOf)
        .containsExactly(tuple(VariableOperationType.JOB_COMPLETION, taskKey, "task"));
  }

  @Test
  public void shouldSourceLocalCopyOfJobCompletionFromJobCompletionWithOutputMapping() {
    // given
    final long processInstanceKey =
        createInstance(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task", t -> t.zeebeJobType(jobType).zeebeOutputExpression("x", "total"))
                .endEvent()
                .done());
    final long taskKey = elementInstanceKey(processInstanceKey, "task");

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(jobType).withVariable("x", 1).complete();

    // then
    assertThat(variableRecords(processInstanceKey, "x"))
        .extracting(
            record -> record.getValue().getScopeKey(), EventPayloadVariableSourceTest::sourceOf)
        .containsExactly(
            tuple(taskKey, tuple(VariableOperationType.JOB_COMPLETION, taskKey, "task")));
  }

  @Test
  public void shouldSourceVariableOfSendTaskJobFromJobCompletion() {
    // given
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .sendTask("task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();

    // when
    final long processInstanceKey = completeJobWithVariable(process, jobType);

    // then
    assertJobCompletionSource(processInstanceKey);
  }

  @Test
  public void shouldSourceVariableOfScriptTaskJobFromJobCompletion() {
    // given
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .scriptTask("task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();

    // when
    final long processInstanceKey = completeJobWithVariable(process, jobType);

    // then
    assertJobCompletionSource(processInstanceKey);
  }

  @Test
  public void shouldSourceVariableOfBusinessRuleTaskJobFromJobCompletion() {
    // given
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .businessRuleTask("task", t -> t.zeebeJobType(jobType))
            .endEvent()
            .done();

    // when
    final long processInstanceKey = completeJobWithVariable(process, jobType);

    // then
    assertJobCompletionSource(processInstanceKey);
  }

  @Test
  public void shouldSourceVariableOfJobBasedUserTaskJobFromJobCompletion() {
    // given
    final var process =
        Bpmn.createExecutableProcess(processId).startEvent().userTask("task").endEvent().done();

    // when
    final long processInstanceKey = completeJobWithVariable(process, Protocol.USER_TASK_JOB_TYPE);

    // then
    assertJobCompletionSource(processInstanceKey);
  }

  @Test
  public void shouldSourceVariableOfMessageThrowEventJobFromJobCompletion() {
    // given
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .intermediateThrowEvent("task")
            .message(messageName)
            .zeebeJobType(jobType)
            .endEvent()
            .done();

    // when
    final long processInstanceKey = completeJobWithVariable(process, jobType);

    // then
    assertJobCompletionSource(processInstanceKey);
  }

  @Test
  public void shouldSourceVariableOfMessageEndEventJobFromJobCompletion() {
    // given
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .endEvent("task", e -> e.message(messageName).zeebeJobType(jobType))
            .done();

    // when
    final long processInstanceKey = completeJobWithVariable(process, jobType);

    // then
    assertJobCompletionSource(processInstanceKey);
  }

  @Test
  public void shouldNotSourceVariablesOfMultiInstanceFromPreviousJobCompletion() {
    // given
    final long processInstanceKey =
        createInstance(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType(jobType)
                            .multiInstance(
                                m ->
                                    m.sequential()
                                        .zeebeInputCollectionExpression("[1,2]")
                                        .zeebeInputElement("item")
                                        .zeebeOutputElementExpression("result")
                                        .zeebeOutputCollection("results")))
                .endEvent()
                .done());

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(jobType)
        .withVariable("result", 10)
        .complete();
    final var secondJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(jobType)
            .skip(1)
            .getFirst();
    ENGINE.job().withKey(secondJob.getKey()).withVariable("result", 20).complete();

    // then
    final var innerInstanceKeys =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .limit(2)
            .map(Record::getKey)
            .toList();
    final var records = variableRecords(processInstanceKey);
    assertThat(records)
        .filteredOn(
            record -> record.getValue().getSource().getType() != VariableOperationType.UNKNOWN)
        .extracting(
            record -> record.getValue().getName(),
            record -> record.getValue().getValue(),
            EventPayloadVariableSourceTest::sourceOf)
        .containsExactly(
            tuple(
                "result",
                "10",
                tuple(VariableOperationType.JOB_COMPLETION, innerInstanceKeys.get(0), "task")),
            tuple(
                "result",
                "20",
                tuple(VariableOperationType.JOB_COMPLETION, innerInstanceKeys.get(1), "task")));
    assertThat(records)
        .filteredOn(
            record -> record.getValue().getSource().getType() == VariableOperationType.UNKNOWN)
        .extracting(EventPayloadVariableSourceTest::sourceOf)
        .containsOnly(NO_SOURCE);
  }

  @Test
  public void shouldSourceVariableOfMessageCatchEventFromCatchEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(
                    "catch", e -> e.message(m -> m.name(messageName).zeebeCorrelationKey("=key")))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("key", "k1").create();
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    final long catchKey = elementInstanceKey(processInstanceKey, "catch");

    // when
    ENGINE
        .message()
        .withName(messageName)
        .withCorrelationKey("k1")
        .withVariables(Map.of("x", 1))
        .publish();

    // then
    assertThat(variableRecords(processInstanceKey, "x"))
        .extracting(EventPayloadVariableSourceTest::sourceOf)
        .containsExactly(tuple(VariableOperationType.UNKNOWN, catchKey, "catch"));
  }

  @Test
  public void shouldSourceVariableOfNonInterruptingBoundaryEventFromBoundaryEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .boundaryEvent("boundary")
                .cancelActivity(false)
                .message(m -> m.name(messageName).zeebeCorrelationKey("=key"))
                .endEvent("boundary-end")
                .moveToActivity("task")
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("key", "k1").create();
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE
        .message()
        .withName(messageName)
        .withCorrelationKey("k1")
        .withVariables(Map.of("y", 1))
        .publish();
    final long boundaryKey = elementInstanceKey(processInstanceKey, "boundary");
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("boundary-end")
        .await();
    ENGINE.job().ofInstance(processInstanceKey).withType(jobType).complete();

    // then
    final var source = tuple(VariableOperationType.UNKNOWN, boundaryKey, "boundary");
    assertThat(variableRecords(processInstanceKey, "y"))
        .extracting(
            record -> record.getValue().getScopeKey(), EventPayloadVariableSourceTest::sourceOf)
        .containsExactlyInAnyOrder(tuple(boundaryKey, source), tuple(processInstanceKey, source));
  }

  @Test
  public void shouldSourceResultVariableOfScriptTaskFromScriptTask() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .scriptTask("task", t -> t.zeebeExpression("= 1 + 1").zeebeResultVariable("result"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final long taskKey = elementInstanceKey(processInstanceKey, "task");
    assertThat(variableRecords(processInstanceKey, "result"))
        .extracting(EventPayloadVariableSourceTest::sourceOf)
        .containsExactly(tuple(VariableOperationType.UNKNOWN, taskKey, "task"));
  }

  @Test
  public void shouldSourceLocalCopyOfCallActivityWithoutPropagationFromCallActivity() {
    // given
    final var childProcessId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .callActivity(
                    "call",
                    c -> c.zeebeProcessId(childProcessId).zeebePropagateAllChildVariables(false))
                .endEvent()
                .done())
        .withXmlResource(
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .serviceTask("childTask", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final long callKey = elementInstanceKey(processInstanceKey, "call");
    final var childJob =
        RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).getFirst();

    // when
    ENGINE.job().withKey(childJob.getKey()).withVariable("z", 1).complete();

    // then
    assertThat(variableRecords(processInstanceKey, "z"))
        .filteredOn(record -> record.getValue().getScopeKey() == callKey)
        .extracting(EventPayloadVariableSourceTest::sourceOf)
        .containsExactly(tuple(VariableOperationType.UNKNOWN, callKey, "call"));
  }

  @Test
  public void shouldNotSourceResultVariableOfScriptTaskWithTaskHeaderFromJobCompletion() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .scriptTask(
                    "task",
                    t ->
                        t.zeebeExpression("= 1 + 1")
                            .zeebeResultVariable("result")
                            .zeebeTaskHeader("key", "value"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final long taskKey = elementInstanceKey(processInstanceKey, "task");
    assertThat(variableRecords(processInstanceKey, "result"))
        .extracting(EventPayloadVariableSourceTest::sourceOf)
        .containsExactly(tuple(VariableOperationType.UNKNOWN, taskKey, "task"));
  }

  @Test
  public void shouldSourceResultVariableOfBusinessRuleTaskWithDecisionFromBusinessRuleTask() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource("/dmn/drg-force-user.dmn")
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .businessRuleTask(
                    "task",
                    t ->
                        t.zeebeCalledDecisionId("jedi_or_sith")
                            .zeebeResultVariable("result")
                            .zeebeTaskHeader("key", "value"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("lightsaberColor", "blue")
            .create();

    // then
    final long taskKey = elementInstanceKey(processInstanceKey, "task");
    assertThat(variableRecords(processInstanceKey, "result"))
        .extracting(EventPayloadVariableSourceTest::sourceOf)
        .containsExactly(tuple(VariableOperationType.UNKNOWN, taskKey, "task"));
  }

  @Test
  public void shouldSourceVariableOfEventSubProcessStartEventFromStartEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .eventSubProcess(
                    "event-sub-process",
                    sub ->
                        sub.startEvent("start")
                            .message(m -> m.name(messageName).zeebeCorrelationKey("=key"))
                            .endEvent())
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("key", "k1").create();
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE
        .message()
        .withName(messageName)
        .withCorrelationKey("k1")
        .withVariables(Map.of("m", 1))
        .publish();
    final long startKey = elementInstanceKey(processInstanceKey, "start");
    ENGINE.job().ofInstance(processInstanceKey).withType(jobType).complete();

    // then
    assertThat(variableRecords(processInstanceKey, "m"))
        .extracting(EventPayloadVariableSourceTest::sourceOf)
        .containsOnly(tuple(VariableOperationType.UNKNOWN, startKey, "start"));
  }

  @Test
  public void shouldSourceVariableOfMessageStartEventFromStartEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent("start")
                .message(messageName)
                .endEvent()
                .done())
        .deploy();

    // when
    ENGINE
        .message()
        .withName(messageName)
        .withCorrelationKey("k1")
        .withVariables(Map.of("m", 1))
        .publish();

    // then
    final long processInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withBpmnProcessId(processId)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();
    final long startKey = elementInstanceKey(processInstanceKey, "start");
    assertThat(variableRecords(processInstanceKey, "m"))
        .extracting(EventPayloadVariableSourceTest::sourceOf)
        .containsOnly(tuple(VariableOperationType.UNKNOWN, startKey, "start"));
  }

  @Test
  public void shouldSourceVariableOfEventBasedGatewayCatchEventFromCatchEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .eventBasedGateway()
                .intermediateCatchEvent(
                    "catch", e -> e.message(m -> m.name(messageName).zeebeCorrelationKey("=key")))
                .endEvent()
                .moveToLastGateway()
                .intermediateCatchEvent("timer", e -> e.timerWithDuration("PT1H"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("key", "k1").create();
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE
        .message()
        .withName(messageName)
        .withCorrelationKey("k1")
        .withVariables(Map.of("y", 1))
        .publish();

    // then
    final long catchKey = elementInstanceKey(processInstanceKey, "catch");
    final var source = tuple(VariableOperationType.UNKNOWN, catchKey, "catch");
    assertThat(variableRecords(processInstanceKey, "y"))
        .extracting(
            record -> record.getValue().getScopeKey(), EventPayloadVariableSourceTest::sourceOf)
        .containsExactlyInAnyOrder(tuple(catchKey, source), tuple(processInstanceKey, source));
  }

  @Test
  public void shouldSourcePropagatedVariableOfCallActivityFromCallActivity() {
    // given
    final var childProcessId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .callActivity("call", c -> c.zeebeProcessId(childProcessId))
                .endEvent()
                .done())
        .withXmlResource(
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .serviceTask("childTask", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final long callKey = elementInstanceKey(processInstanceKey, "call");
    final var childJob =
        RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).getFirst();

    // when
    ENGINE.job().withKey(childJob.getKey()).withVariable("z", 1).complete();

    // then
    assertThat(variableRecords(processInstanceKey, "z"))
        .filteredOn(record -> record.getValue().getScopeKey() == processInstanceKey)
        .extracting(EventPayloadVariableSourceTest::sourceOf)
        .containsExactly(tuple(VariableOperationType.UNKNOWN, callKey, "call"));
  }

  /** Completes the job of the element "task" with the variable x, and returns the instance key. */
  private long completeJobWithVariable(final BpmnModelInstance process, final String type) {
    final long processInstanceKey = createInstance(process);
    ENGINE.job().ofInstance(processInstanceKey).withType(type).withVariable("x", 1).complete();
    return processInstanceKey;
  }

  private static void assertJobCompletionSource(final long processInstanceKey) {
    final long taskKey = elementInstanceKey(processInstanceKey, "task");
    assertThat(variableRecords(processInstanceKey, "x"))
        .extracting(EventPayloadVariableSourceTest::sourceOf)
        .containsExactly(tuple(VariableOperationType.JOB_COMPLETION, taskKey, "task"));
  }

  private long createInstance(final BpmnModelInstance process) {
    ENGINE.deployment().withXmlResource(process).deploy();
    return ENGINE.processInstance().ofBpmnProcessId(processId).create();
  }

  private static long elementInstanceKey(final long processInstanceKey, final String elementId) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(elementId)
        .getFirst()
        .getKey();
  }

  /** Returns the variable records until the process instance is completed. */
  private static List<Record<VariableRecordValue>> variableRecords(final long processInstanceKey) {
    return RecordingExporter.records()
        .betweenProcessInstance(processInstanceKey)
        .variableRecords()
        .toList();
  }

  private static List<Record<VariableRecordValue>> variableRecords(
      final long processInstanceKey, final String name) {
    return variableRecords(processInstanceKey).stream()
        .filter(record -> record.getValue().getName().equals(name))
        .toList();
  }

  private static Tuple sourceOf(final Record<VariableRecordValue> record) {
    final var source = record.getValue().getSource();
    return tuple(source.getType(), source.getElementInstanceKey(), source.getElementId());
  }
}
