/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationTerminateInstruction;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ModifyProcessInstanceTerminationTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @ClassRule
  public static final BrokerClassRuleHelper CLASS_RULE_HELPER = new BrokerClassRuleHelper();

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
    final long elementInstanceKey = getElementInstanceKeyOfElement(processInstanceKey, "A");

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
    final long elementInstanceKey = getElementInstanceKeyOfElement(processInstanceKey, "A");

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

  @Test
  public void shouldTerminateAllElementsInRootScope() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask("A").endEvent().done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var elementInstanceKey = getElementInstanceKeyOfElement(processInstanceKey, "A");

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(elementInstanceKey)
        .modify();

    // then
    assertThatElementIsTerminated(processInstanceKey, "A");
    assertThatElementIsTerminated(processInstanceKey, PROCESS_ID);
  }

  @Test
  public void shouldTerminateAllElementsOfFlowScope() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "subprocess",
                    subprocess ->
                        subprocess
                            .embeddedSubProcess()
                            .startEvent()
                            .parallelGateway("fork")
                            .userTask("A")
                            .moveToNode("fork")
                            .userTask("B"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var elementInstanceKeyOfA = getElementInstanceKeyOfElement(processInstanceKey, "A");
    final var elementInstanceKeyOfB = getElementInstanceKeyOfElement(processInstanceKey, "B");

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(elementInstanceKeyOfA)
        .terminateElement(elementInstanceKeyOfB)
        .modify();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getElementId(),
            Record::getIntent)
        .describedAs("Expect to terminate the elements and propagate to their flow scopes")
        .containsSubsequence(
            tuple(BpmnElementType.USER_TASK, "A", ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.USER_TASK, "B", ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                BpmnElementType.SUB_PROCESS,
                "subprocess",
                ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, PROCESS_ID, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldTerminateAllElementsOfNestedFlowScope() {
    // given
    final Consumer<SubProcessBuilder> subprocessLvl2Builder =
        subprocessLvl2 -> subprocessLvl2.embeddedSubProcess().startEvent().userTask("A").endEvent();

    final Consumer<SubProcessBuilder> subprocessLvl1Builder =
        subprocessLvl1 ->
            subprocessLvl1
                .embeddedSubProcess()
                .startEvent()
                .subProcess("subprocess-lvl-2", subprocessLvl2Builder)
                .endEvent();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess("subprocess-lvl-1", subprocessLvl1Builder)
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var elementInstanceKeyOfA = getElementInstanceKeyOfElement(processInstanceKey, "A");

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(elementInstanceKeyOfA)
        .modify();

    // then
    assertThatElementIsTerminated(processInstanceKey, "A");
    assertThatElementIsTerminated(processInstanceKey, "subprocess-lvl-2");
    assertThatElementIsTerminated(processInstanceKey, "subprocess-lvl-1");
    assertThatElementIsTerminated(processInstanceKey, PROCESS_ID);
  }

  @Test
  public void shouldDeleteEventSubscriptionsOfTerminatedFlowScope() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "subprocess",
                    subprocess -> subprocess.embeddedSubProcess().startEvent().userTask("A"))
                .boundaryEvent(
                    "boundary-event",
                    b -> b.message(m -> m.name("message").zeebeCorrelationKeyExpression("\"key\"")))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var elementInstanceKeyOfA = getElementInstanceKeyOfElement(processInstanceKey, "A");
    final var elementInstanceKeyOfSubprocess =
        getElementInstanceKeyOfElement(processInstanceKey, "subprocess");

    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementInstanceKey(elementInstanceKeyOfSubprocess)
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(elementInstanceKeyOfA)
        .modify();

    // then
    assertThatElementIsTerminated(processInstanceKey, "A");
    assertThatElementIsTerminated(processInstanceKey, "subprocess");
    assertThatMessageEventSubscriptionIsDeleted(processInstanceKey, elementInstanceKeyOfSubprocess);
  }

  @Test
  public void shouldNotCompleteFlowScopeIfElementsAreTerminated() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "subprocess",
                    subprocess -> subprocess.embeddedSubProcess().startEvent().userTask("A"))
                .sequenceFlowId("to-end")
                .endEvent("end")
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var elementInstanceKey = getElementInstanceKeyOfElement(processInstanceKey, "A");

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(elementInstanceKey)
        .modify();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getElementId(),
            Record::getIntent)
        .describedAs("Expect to terminate the element and its flow scope")
        .containsSequence(
            tuple(BpmnElementType.USER_TASK, "A", ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, "A", ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                BpmnElementType.SUB_PROCESS,
                "subprocess",
                ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(
                BpmnElementType.SUB_PROCESS,
                "subprocess",
                ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, PROCESS_ID, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.PROCESS, PROCESS_ID, ProcessInstanceIntent.ELEMENT_TERMINATED))
        .describedAs("Expect the flow scope not to be completed")
        .doesNotContain(
            tuple(
                BpmnElementType.SUB_PROCESS,
                "subprocess",
                ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(
                BpmnElementType.SUB_PROCESS, "subprocess", ProcessInstanceIntent.ELEMENT_COMPLETED))
        .describedAs("Expect to elements after the flow scope not to be activated")
        .doesNotContain(
            tuple(
                BpmnElementType.SEQUENCE_FLOW, "to-end", ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.END_EVENT, "end", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldNotTerminateFlowScopeIfPendingActivation() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .parallelGateway("fork")
                .userTask("A")
                .moveToNode("fork")
                .userTask("B")
                .sequenceFlowId("b-to-c")
                .userTask("C")
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var elementInstanceKeyOfA = getElementInstanceKeyOfElement(processInstanceKey, "A");

    final var elementRecordOfB =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("B")
            .getFirst();

    // when
    final var modificationCommand =
        new ProcessInstanceModificationRecord()
            .setProcessInstanceKey(processInstanceKey)
            .addTerminateInstruction(
                new ProcessInstanceModificationTerminateInstruction()
                    .setElementInstanceKey(elementInstanceKeyOfA));

    // write the commands in a specific order to ensure that we took the outgoing sequence flow of
    // B, and we wrote a command to activate the element C (i.e. the pending activation), before
    // we process the modification command
    ENGINE.writeRecords(
        RecordToWrite.command()
            .processInstance(ProcessInstanceIntent.COMPLETE_ELEMENT, elementRecordOfB.getValue())
            .key(elementRecordOfB.getKey()),
        RecordToWrite.command().modification(modificationCommand).key(processInstanceKey));

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit("C", ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getElementId(),
            Record::getIntent)
        .describedAs("Ensure the precondition of a pending activation")
        .containsSequence(
            tuple(BpmnElementType.USER_TASK, "B", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.SEQUENCE_FLOW, "b-to-c", ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple(BpmnElementType.USER_TASK, "C", ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.USER_TASK, "A", ProcessInstanceIntent.ELEMENT_TERMINATING))
        .describedAs("Expect the flow scope not to be terminated")
        .doesNotContain(
            tuple(
                BpmnElementType.SUB_PROCESS,
                "subprocess",
                ProcessInstanceIntent.ELEMENT_TERMINATED))
        .describedAs("Expect the pending element to be activated")
        .contains(tuple(BpmnElementType.USER_TASK, "C", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldTerminateEventSubprocess() {
    // given
    final var correlationKey = CLASS_RULE_HELPER.getCorrelationValue();

    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .eventSubProcess(
                "event-subprocess",
                eventSubprocess ->
                    eventSubprocess
                        .startEvent()
                        .message(m -> m.name("start").zeebeCorrelationKeyExpression("key"))
                        .subProcess(
                            "subprocess",
                            subprocess ->
                                subprocess
                                    .embeddedSubProcess()
                                    .startEvent()
                                    .userTask("B")
                                    .endEvent())
                        .endEvent())
            .startEvent()
            .userTask("A")
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", correlationKey)
            .create();

    ENGINE.message().withName("start").withCorrelationKey(correlationKey).publish();

    final var eventSubprocessKey =
        getElementInstanceKeyOfElement(processInstanceKey, "event-subprocess");

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("B")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(eventSubprocessKey)
        .modify();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getElementId(),
            Record::getIntent)
        .describedAs("Expect to terminate the event subprocess and all containing elements")
        .containsSequence(
            tuple(
                BpmnElementType.EVENT_SUB_PROCESS,
                "event-subprocess",
                ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(
                BpmnElementType.SUB_PROCESS,
                "subprocess",
                ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, "B", ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, "B", ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                BpmnElementType.SUB_PROCESS,
                "subprocess",
                ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                BpmnElementType.EVENT_SUB_PROCESS,
                "event-subprocess",
                ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, PROCESS_ID, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.PROCESS, PROCESS_ID, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  private static long getElementInstanceKeyOfElement(
      final long processInstanceKey, final String elementId) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(elementId)
        .getFirst()
        .getKey();
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
