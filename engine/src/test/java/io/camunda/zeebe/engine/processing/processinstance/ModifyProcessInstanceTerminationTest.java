/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.EventSubProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationTerminateInstruction;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
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

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(1))
        .isNotEmpty();

    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    // then
    assertThat(
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
    assertThat(
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
    assertThat(
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
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit("C", ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getElementId(),
            Record::getIntent)
        .describedAs("Ensure the precondition of a pending activation")
        .containsSubsequence(
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
    assertThat(
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

  @Test
  public void shouldTerminateEmbeddedSubProcess() {
    // given
    final var correlationKey = CLASS_RULE_HELPER.getCorrelationValue();

    final Consumer<EventSubProcessBuilder> eventSubProcess =
        eventSP ->
            eventSP
                .startEvent()
                .message(m -> m.name("start").zeebeCorrelationKeyExpression("key"))
                .interrupting(false)
                .userTask("B")
                .endEvent();

    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess(
                "subprocess",
                sp ->
                    sp.embeddedSubProcess()
                        .eventSubProcess("event-subprocess", eventSubProcess)
                        .startEvent()
                        .userTask("A")
                        .endEvent())
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", correlationKey)
            .create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    ENGINE.message().withName("start").withCorrelationKey(correlationKey).publish();

    final var subProcessKey = getElementInstanceKeyOfElement(processInstanceKey, "subprocess");

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("B")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(subProcessKey)
        .modify();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getElementId(),
            Record::getIntent)
        .describedAs("Expect to terminate the subprocess and all containing elements")
        .containsSequence(
            tuple(
                BpmnElementType.SUB_PROCESS,
                "subprocess",
                ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, "A", ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, "A", ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                BpmnElementType.EVENT_SUB_PROCESS,
                "event-subprocess",
                ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, "B", ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, "B", ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                BpmnElementType.EVENT_SUB_PROCESS,
                "event-subprocess",
                ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                BpmnElementType.SUB_PROCESS,
                "subprocess",
                ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, PROCESS_ID, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.PROCESS, PROCESS_ID, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldTerminateCallActivity() {
    // given
    final String callActivityProcessId = "callActivityProcess";
    final var callActivityProcess =
        Bpmn.createExecutableProcess(callActivityProcessId)
            .startEvent()
            .userTask("A")
            .endEvent()
            .done();

    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .callActivity(
                "callActivity", callActivity -> callActivity.zeebeProcessId(callActivityProcessId))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(callActivityProcess).withXmlResource(process).deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var callActivityKey = getElementInstanceKeyOfElement(processInstanceKey, "callActivity");

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withParentProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(callActivityKey)
        .modify();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKeyOrParentProcessInstanceKey(processInstanceKey)
                .limit(PROCESS_ID, ProcessInstanceIntent.ELEMENT_TERMINATED))
        .extracting(
            r -> r.getValue().getBpmnElementType(),
            r -> r.getValue().getElementId(),
            Record::getIntent)
        .describedAs("Expect to terminate the callActivity and all containing elements")
        .containsSequence(
            tuple(
                BpmnElementType.CALL_ACTIVITY,
                "callActivity",
                ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(
                BpmnElementType.PROCESS,
                callActivityProcessId,
                ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, "A", ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, "A", ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                BpmnElementType.PROCESS,
                callActivityProcessId,
                ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                BpmnElementType.CALL_ACTIVITY,
                "callActivity",
                ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, PROCESS_ID, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.PROCESS, PROCESS_ID, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldTerminateParallelMultiInstanceElement() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "A",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .userTask("B")
                            .endEvent()
                            .subProcessDone()
                            .multiInstance()
                            .parallel()
                            .zeebeInputCollectionExpression("[1,2,3]")
                            .multiInstanceDone())
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var multiInstanceElement =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
            .getFirst();
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("B")
                .withElementType(BpmnElementType.USER_TASK)
                .limit(3))
        .describedAs("Await until all 3 user tasks are activated as pre-condition")
        .hasSize(3);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(multiInstanceElement.getKey())
        .modify();

    // then
    assertThatElementIsTerminated(processInstanceKey, "A");

    assertThat(
            RecordingExporter.processInstanceRecords()
                .onlyEvents()
                .withProcessInstanceKey(processInstanceKey)
                .skipUntil(r -> r.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATING)
                .limitToProcessInstanceTerminated())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .describedAs("Expect that all active instances of the multi-instance have been terminated")
        .containsSequence(
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldTerminateSequentialMultiInstanceElement() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "A",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .userTask("B")
                            .endEvent()
                            .subProcessDone()
                            .multiInstance()
                            .sequential()
                            .zeebeInputCollectionExpression("[1,2,3]")
                            .multiInstanceDone())
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var multiInstanceElement =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
            .getFirst();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("B")
        .withElementType(BpmnElementType.USER_TASK)
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(multiInstanceElement.getKey())
        .modify();

    // then
    assertThatElementIsTerminated(processInstanceKey, "A");

    assertThat(
            RecordingExporter.processInstanceRecords()
                .onlyEvents()
                .withProcessInstanceKey(processInstanceKey)
                .skipUntil(r -> r.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATING)
                .limitToProcessInstanceTerminated())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .describedAs("Expect that all active instances of the multi-instance have been terminated")
        .containsSequence(
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldTerminateMultiInstanceBodyAndNestedElements() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "A",
                    s ->
                        s.embeddedSubProcess()
                            .startEvent()
                            .userTask("B")
                            .endEvent()
                            .subProcessDone()
                            .multiInstance()
                            .parallel()
                            .zeebeInputCollectionExpression("[1,2,3]")
                            .multiInstanceDone())
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var multiInstanceElement =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
            .getFirst();
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("B")
                .withElementType(BpmnElementType.USER_TASK)
                .limit(3))
        .describedAs("Expect that all 3 user tasks are activated")
        .hasSize(3);
    final var elements =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SUB_PROCESS)
            .limit(3)
            .map(Record::getKey)
            .toList();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(multiInstanceElement.getKey())
        .terminateElement(elements.get(0))
        .terminateElement(elements.get(1))
        .terminateElement(elements.get(2))
        .modify();

    // then
    assertThatElementIsTerminated(processInstanceKey, "A");

    assertThat(
            RecordingExporter.processInstanceRecords()
                .onlyEvents()
                .withProcessInstanceKey(processInstanceKey)
                .skipUntil(r -> r.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATING)
                .limitToProcessInstanceTerminated())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .describedAs("Expect that all active instances of the multi-instance have been terminated")
        .containsSequence(
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldTerminateFlowScopeWhenElementInsideIsActivateAndFlowScopeIsTerminated() {
    // given
    final var correlationKey = CLASS_RULE_HELPER.getCorrelationValue();
    final Consumer<EventSubProcessBuilder> eventSubProcess =
        esp ->
            esp.startEvent("startEvent")
                .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
                .interrupting(false)
                .userTask("B")
                .endEvent("endEvent");
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .eventSubProcess("eventSubProcess", eventSubProcess)
                .startEvent()
                .userTask("A")
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", correlationKey)
            .create();

    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withMessageName("message")
                .findFirst())
        .isPresent();

    ENGINE.message().withName("message").withCorrelationKey(correlationKey).publish();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .hasSize(2);

    final long eventSubprocessKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("eventSubProcess")
            .getFirst()
            .getKey();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(eventSubprocessKey)
        .activateElement("endEvent")
        .modify();

    // then
    final var rejectedActivateCommand =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ACTIVATE_ELEMENT)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("endEvent")
            .onlyCommandRejections()
            .getFirst();

    assertThat(rejectedActivateCommand.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejectedActivateCommand.getRejectionReason())
        .isEqualTo(
            "Expected flow scope instance with key '%s' to be present in state but not found.",
            eventSubprocessKey);
    assertThatElementIsTerminated(processInstanceKey, "eventSubProcess");
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
    assertThat(
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
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(elementId)
                .exists())
        .isTrue();
  }

  private void assertThatIncidentIsResolved(final long processInstanceKey, final String elementId) {
    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(elementId)
                .exists())
        .isTrue();
  }

  private void assertThatTimerEventSubscriptionIsDeleted(
      final long processInstanceKey, final long elementInstanceKey) {
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementInstanceKey(elementInstanceKey)
                .exists())
        .isTrue();
  }

  private void assertThatMessageEventSubscriptionIsDeleted(
      final long processInstanceKey, final long elementInstanceKey) {
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementInstanceKey(elementInstanceKey)
                .exists())
        .isTrue();
  }
}
