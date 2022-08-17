/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ModifyProcessInstanceTerminationTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldTerminateElementInRootScope() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var elementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst()
            .getKey();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(elementInstanceKey)
        .modify();

    // then
    assertThatElementIsTerminated(processInstanceKey, "A");
    assertThatJobIsCancelled(processInstanceKey, "A");
  }

  @Test
  public void shouldTerminateMultipleElementsInRootScope() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .parallelGateway()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .endEvent()
                .moveToLastGateway()
                .serviceTask("B", b -> b.zeebeJobType("B"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var elementsInstanceKeys =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .limit(2)
            .collect(Collectors.toMap(r -> r.getValue().getElementId(), Record::getKey));

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(elementsInstanceKeys.get("A"))
        .terminateElement(elementsInstanceKeys.get("B"))
        .modify();

    // then
    assertThatElementIsTerminated(processInstanceKey, "A");
    assertThatElementIsTerminated(processInstanceKey, "B");
    assertThatJobIsCancelled(processInstanceKey, "A");
    assertThatJobIsCancelled(processInstanceKey, "B");
  }

  @Test
  public void shouldTerminateElementWithIncidentOnJob() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    ENGINE.job().withType("A").ofInstance(processInstanceKey).fail();
    final var incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(incidentRecord.getValue().getElementInstanceKey())
        .modify();

    // then
    assertThatElementIsTerminated(processInstanceKey, "A");
    assertThatJobIsCancelled(processInstanceKey, "A");
    assertThatIncidentIsResolved(processInstanceKey, "A");
  }

  @Test
  public void shouldTerminateElementWithIncidentInRootScope() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobTypeExpression("A")) // invalid expression
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(incidentRecord.getValue().getElementInstanceKey())
        .modify();

    // then
    assertThatElementIsTerminated(processInstanceKey, "A");
    assertThatIncidentIsResolved(processInstanceKey, "A");
  }

  @Test
  public void shouldTerminateElementWithEventSubscriptions() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .boundaryEvent("timer", t -> t.timerWithDuration("PT1H").endEvent())
                .moveToActivity("A")
                .boundaryEvent(
                    "message",
                    b ->
                        b.message(
                            m ->
                                m.name("message")
                                    .zeebeCorrelationKeyExpression("= \"correlationKey\"")))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var elementInstanceKey =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withMessageName("message")
            .getFirst()
            .getValue()
            .getElementInstanceKey();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(elementInstanceKey)
        .modify();

    // then
    assertThatElementIsTerminated(processInstanceKey, "A");
    assertThatJobIsCancelled(processInstanceKey, "A");
    assertThatTimerEventSubscriptionIsDeleted(processInstanceKey, elementInstanceKey);
    assertThatMessageEventSubscriptionIsDeleted(processInstanceKey, elementInstanceKey);
  }

  @Test
  public void shouldBeAbleToCompleteProcessInstanceAfterElementIsTerminated() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .parallelGateway("gateway")
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .endEvent()
                .moveToNode("gateway")
                .serviceTask("B", b -> b.zeebeJobType("B"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var elementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst()
            .getKey();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(elementInstanceKey)
        .modify();

    assertThatElementIsTerminated(processInstanceKey, "A");
    assertThatJobIsCancelled(processInstanceKey, "A");

    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(1))
        .isNotEmpty();

    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .limitToProcessInstanceCompleted()
                .findAny())
        .describedAs("Expect the process instance to have been completed")
        .isPresent();
  }

  private void assertThatElementIsTerminated(
      final long processInstanceKey, final String elementId) {
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .onlyEvents()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(elementId)
                .limit(elementId, ProcessInstanceIntent.ELEMENT_TERMINATED)
                .toList())
        .extracting(Record::getIntent)
        .containsSequence(
            ProcessInstanceIntent.ELEMENT_TERMINATING, ProcessInstanceIntent.ELEMENT_TERMINATED);
  }

  private void assertThatJobIsCancelled(final long processInstanceKey, final String elementId) {
    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(elementId)
                .exists())
        .isTrue();
  }

  private void assertThatIncidentIsResolved(final long processInstanceKey, final String elementId) {
    Assertions.assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(elementId)
                .exists())
        .isTrue();
  }

  private void assertThatTimerEventSubscriptionIsDeleted(
      final long processInstanceKey, final long elementInstanceKey) {
    Assertions.assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementInstanceKey(elementInstanceKey)
                .exists())
        .isTrue();
  }

  private void assertThatMessageEventSubscriptionIsDeleted(
      final long processInstanceKey, final long elementInstanceKey) {
    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementInstanceKey(elementInstanceKey)
                .exists())
        .isTrue();
  }
}
