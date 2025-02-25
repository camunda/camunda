/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.EventSubProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.assertj.core.groups.Tuple;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ModifyProcessInstanceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldWriteModifiedEventForProcessInstance() {
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

    // when
    final var event =
        ENGINE.processInstance().withInstanceKey(processInstanceKey).modification().modify();

    // then
    assertThat(event)
        .hasKey(processInstanceKey)
        .hasRecordType(RecordType.EVENT)
        .hasIntent(ProcessInstanceModificationIntent.MODIFIED);

    assertThat(event.getValue())
        .hasProcessInstanceKey(processInstanceKey)
        .hasNoActivateInstructions()
        .hasNoTerminateInstructions();
  }

  @Test
  public void shouldActivateRootElement() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .serviceTask("B", b -> b.zeebeJobType("B"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .modify();

    // then
    verifyThatRootElementIsActivated(processInstanceKey, "B", BpmnElementType.SERVICE_TASK);
  }

  @Test
  public void shouldActivateMultipleRootElement() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .parallelGateway()
                .serviceTask("B", b -> b.zeebeJobType("B"))
                .moveToLastGateway()
                .userTask("C")
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst()
        .getValue();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .activateElement("C")
        .modify();

    // then
    verifyThatRootElementIsActivated(processInstanceKey, "B", BpmnElementType.SERVICE_TASK);
    verifyThatRootElementIsActivated(processInstanceKey, "C", BpmnElementType.USER_TASK);
  }

  @Test
  public void shouldCompleteModifiedProcessInstanceWithActivatedRootElements() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .serviceTask("B", b -> b.zeebeJobType("B"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .modify();

    // when
    completeJobs(processInstanceKey, 3);

    // then
    verifyThatElementIsCompleted(processInstanceKey, "B");
    verifyThatProcessInstanceIsCompleted(processInstanceKey);
  }

  @Test
  public void shouldActivateInsideExistingFlowScope() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "subprocess",
                    sp ->
                        sp.embeddedSubProcess()
                            .startEvent()
                            .serviceTask("A", a -> a.zeebeJobType("A"))
                            .serviceTask("B", b -> b.zeebeJobType("B"))
                            .endEvent())
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var subProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SUB_PROCESS)
            .getFirst();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .modify();

    // then
    verifyThatElementIsActivated(
        processInstanceKey, "B", BpmnElementType.SERVICE_TASK, subProcessInstance.getKey());
  }

  @Test
  public void shouldActivateMultipleElementInsideExistingFlowScope() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "subprocess",
                    sp ->
                        sp.embeddedSubProcess()
                            .startEvent()
                            .serviceTask("A", a -> a.zeebeJobType("A"))
                            .parallelGateway()
                            .serviceTask("B", b -> b.zeebeJobType("B"))
                            .moveToLastGateway()
                            .userTask("C")
                            .endEvent())
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var subprocessScopeKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SUB_PROCESS)
            .getFirst()
            .getKey();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .activateElement("C")
        .modify();

    // then
    verifyThatElementIsActivated(
        processInstanceKey, "B", BpmnElementType.SERVICE_TASK, subprocessScopeKey);
    verifyThatElementIsActivated(
        processInstanceKey, "C", BpmnElementType.USER_TASK, subprocessScopeKey);
  }

  @Test
  public void shouldCompleteModifiedProcessInstanceWithActivatedElementsInExistingFlowScope() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "subprocess",
                    sp ->
                        sp.embeddedSubProcess()
                            .startEvent()
                            .serviceTask("A", a -> a.zeebeJobType("A"))
                            .serviceTask("B", b -> b.zeebeJobType("B"))
                            .endEvent())
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SUB_PROCESS)
        .await();

    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .modify();

    // when
    // 3 jobs as A creates another B
    completeJobs(processInstanceKey, 3);

    // then
    verifyThatElementIsCompleted(processInstanceKey, "B");
    verifyThatProcessInstanceIsCompleted(processInstanceKey);
  }

  @Test
  public void shouldActivateElementsInsideSameNonExistingFlowScope() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .eventSubProcess(
                    "event-subprocess",
                    s ->
                        s.startEvent()
                            .message(m -> m.name("start").zeebeCorrelationKeyExpression("key"))
                            .parallelGateway("fork")
                            .serviceTask("B", t -> t.zeebeJobType("B"))
                            .moveToNode("fork")
                            .userTask("C"))
                .startEvent()
                .userTask("A")
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "1").create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .activateElement("C")
        .modify();

    // then
    verifyThatElementIsActivated(
        processInstanceKey,
        "event-subprocess",
        BpmnElementType.EVENT_SUB_PROCESS,
        processInstanceKey);

    final var eventSubprocessKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
            .getFirst()
            .getKey();

    verifyThatElementIsActivated(
        processInstanceKey, "B", BpmnElementType.SERVICE_TASK, eventSubprocessKey);
    verifyThatElementIsActivated(
        processInstanceKey, "C", BpmnElementType.USER_TASK, eventSubprocessKey);

    // and
    completeJobs(processInstanceKey, 3);

    verifyThatElementIsCompleted(processInstanceKey, "B");
    verifyThatElementIsCompleted(processInstanceKey, "C");
    verifyThatElementIsCompleted(processInstanceKey, "event-subprocess");
    verifyThatProcessInstanceIsCompleted(processInstanceKey);
  }

  @Test
  public void shouldActivateElementsInsideDifferentNonExistingFlowScopes() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .eventSubProcess(
                    "event-subprocess-1",
                    s ->
                        s.startEvent()
                            .message(m -> m.name("start-1").zeebeCorrelationKeyExpression("key"))
                            .serviceTask("B", t -> t.zeebeJobType("B")))
                .eventSubProcess(
                    "event-subprocess-2",
                    s ->
                        s.startEvent()
                            .message(m -> m.name("start-2").zeebeCorrelationKeyExpression("key"))
                            .userTask("C"))
                .startEvent()
                .userTask("A")
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "1").create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .activateElement("C")
        .modify();

    // then
    verifyThatElementIsActivated(
        processInstanceKey,
        "event-subprocess-1",
        BpmnElementType.EVENT_SUB_PROCESS,
        processInstanceKey);
    verifyThatElementIsActivated(
        processInstanceKey,
        "event-subprocess-2",
        BpmnElementType.EVENT_SUB_PROCESS,
        processInstanceKey);

    final var eventSubprocessKeysByElementId =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
            .limit(2)
            .collect(Collectors.toMap(r -> r.getValue().getElementId(), Record::getKey));

    verifyThatElementIsActivated(
        processInstanceKey,
        "B",
        BpmnElementType.SERVICE_TASK,
        eventSubprocessKeysByElementId.get("event-subprocess-1"));
    verifyThatElementIsActivated(
        processInstanceKey,
        "C",
        BpmnElementType.USER_TASK,
        eventSubprocessKeysByElementId.get("event-subprocess-2"));

    // and
    completeJobs(processInstanceKey, 3);

    verifyThatElementIsCompleted(processInstanceKey, "B");
    verifyThatElementIsCompleted(processInstanceKey, "C");
    verifyThatElementIsCompleted(processInstanceKey, "event-subprocess-1");
    verifyThatElementIsCompleted(processInstanceKey, "event-subprocess-2");
    verifyThatProcessInstanceIsCompleted(processInstanceKey);
  }

  @Test
  public void shouldActivateElementsInsideNestedNonExistingFlowScopes() {
    // given
    final Consumer<SubProcessBuilder> subprocessBuilder =
        subprocess ->
            subprocess
                .embeddedSubProcess()
                .eventSubProcess(
                    "event-subprocess",
                    eventSubprocess ->
                        eventSubprocess
                            .startEvent()
                            .message(m -> m.name("start").zeebeCorrelationKeyExpression("key"))
                            .serviceTask("B", t -> t.zeebeJobType("B")))
                .startEvent()
                .userTask("C")
                .endEvent();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask("A")
                .subProcess("subprocess", subprocessBuilder)
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "1").create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .activateElement("C")
        .modify();

    // then
    verifyThatElementIsActivated(
        processInstanceKey, "subprocess", BpmnElementType.SUB_PROCESS, processInstanceKey);

    final var subprocessKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SUB_PROCESS)
            .getFirst()
            .getKey();

    verifyThatElementIsActivated(
        processInstanceKey, "event-subprocess", BpmnElementType.EVENT_SUB_PROCESS, subprocessKey);

    final var eventSubprocessKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
            .getFirst()
            .getKey();

    verifyThatElementIsActivated(
        processInstanceKey, "B", BpmnElementType.SERVICE_TASK, eventSubprocessKey);
    verifyThatElementIsActivated(processInstanceKey, "C", BpmnElementType.USER_TASK, subprocessKey);

    // and
    completeJobs(processInstanceKey, 4);

    verifyThatElementIsCompleted(processInstanceKey, "B");
    verifyThatElementIsCompleted(processInstanceKey, "C");
    verifyThatElementIsCompleted(processInstanceKey, "subprocess");
    verifyThatElementIsCompleted(processInstanceKey, "event-subprocess");
    verifyThatProcessInstanceIsCompleted(processInstanceKey);
  }

  @Test
  public void shouldCreateEventSubscriptionsWhenActivatingElementsInsideNonExistingFlowScopes() {
    // given
    final Consumer<SubProcessBuilder> subprocessBuilder1 =
        subprocess ->
            subprocess
                .embeddedSubProcess()
                .eventSubProcess(
                    "event-subprocess-1",
                    eventSubprocess ->
                        eventSubprocess
                            .startEvent()
                            .message(m -> m.name("start-1").zeebeCorrelationKeyExpression("key"))
                            .endEvent())
                .startEvent()
                .serviceTask("B", t -> t.zeebeJobType("B"));

    final Consumer<SubProcessBuilder> subprocessBuilder2 =
        subprocess ->
            subprocess
                .embeddedSubProcess()
                .eventSubProcess(
                    "event-subprocess-2",
                    eventSubprocess ->
                        eventSubprocess
                            .startEvent()
                            .message(m -> m.name("start-2").zeebeCorrelationKeyExpression("key"))
                            .endEvent())
                .startEvent()
                .userTask("C");

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask("A")
                .subProcess("subprocess-1", subprocessBuilder1)
                .subProcess("subprocess-2", subprocessBuilder2)
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "1").create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .activateElement("C")
        .modify();

    // then
    final var subprocessKeysByElementId =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SUB_PROCESS)
            .limit(2)
            .collect(Collectors.toMap(r -> r.getValue().getElementId(), Record::getKey));

    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            ProcessMessageSubscriptionRecordValue::getMessageName,
            ProcessMessageSubscriptionRecordValue::getElementInstanceKey)
        .describedAs("Expect one message subscription for each subprocess to be created")
        .contains(
            tuple("start-1", subprocessKeysByElementId.get("subprocess-1")),
            tuple("start-2", subprocessKeysByElementId.get("subprocess-2")));

    // and
    ENGINE.message().withName("start-1").withCorrelationKey("1").publish();
    ENGINE.message().withName("start-2").withCorrelationKey("1").publish();

    verifyThatElementIsCompleted(processInstanceKey, "event-subprocess-1");
    verifyThatElementIsCompleted(processInstanceKey, "event-subprocess-2");
  }

  @Test
  public void shouldSetVariablesFromMultipleInstructions() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .parallelGateway()
                .userTask("A")
                .moveToLastGateway()
                .userTask("B")
                .moveToLastGateway()
                .userTask("C")
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("A")
        .withGlobalVariables(Map.of("foo", 1))
        .withGlobalVariables(Map.of("bar", 2))
        .activateElement("B")
        .withGlobalVariables(Map.of("baz", 3))
        .withGlobalVariables(Map.of("fizz", 4))
        .withGlobalVariables(Map.of("buzz", 5))
        .activateElement("C")
        .withGlobalVariables(Map.of("foo", "updated"))
        .withGlobalVariables(Map.of("bar", true))
        .modify();

    // then
    Assertions.assertThat(RecordingExporter.variableRecords().onlyEvents().limit(7))
        .extracting(Record::getValue)
        .extracting(VariableRecordValue::getName, VariableRecordValue::getValue)
        .containsExactlyInAnyOrder(
            tuple("foo", "1"),
            tuple("bar", "2"),
            tuple("baz", "3"),
            tuple("fizz", "4"),
            tuple("buzz", "5"),
            tuple("foo", "\"updated\""),
            tuple("bar", "true"));
  }

  @Test
  public void shouldTerminateAndActivateElementInTheSameScope() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "sp",
                    sp ->
                        sp.embeddedSubProcess().startEvent().userTask("A").userTask("B").endEvent())
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var terminateElement =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst();
    final var terminatedElementScope = terminateElement.getValue().getFlowScopeKey();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(terminateElement.getKey())
        .activateElement("B")
        .modify();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .onlyEvents()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("A")
                .limit("A", ProcessInstanceIntent.ELEMENT_TERMINATED))
        .extracting(Record::getIntent)
        .containsSequence(
            ProcessInstanceIntent.ELEMENT_TERMINATING, ProcessInstanceIntent.ELEMENT_TERMINATED);
    // Verifies that the element is activated in the same scope the other element was terminated in.
    verifyThatElementIsActivated(
        processInstanceKey, "B", BpmnElementType.USER_TASK, terminatedElementScope);

    // and
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("B")
        .limit(1)
        .map(Record::getKey)
        .forEach(jobKey -> ENGINE.job().withKey(jobKey).complete());
    verifyThatElementIsCompleted(processInstanceKey, "B");
    verifyThatElementIsCompleted(processInstanceKey, "sp");
    verifyThatProcessInstanceIsCompleted(processInstanceKey);
  }

  @Test
  public void shouldActivateElementInInterruptedFlowScope() {
    // given
    final Consumer<EventSubProcessBuilder> eventSubProcess =
        eventSubprocess ->
            eventSubprocess
                .startEvent()
                .interrupting(true)
                .message(message -> message.name("interrupt").zeebeCorrelationKeyExpression("key"))
                .userTask("A")
                .endEvent();

    final Consumer<SubProcessBuilder> subProcess =
        subprocess -> subprocess.embeddedSubProcess().startEvent().userTask("C").endEvent();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .eventSubProcess("event-subprocess", eventSubProcess)
                .startEvent()
                .userTask("B")
                .subProcess("subprocess", subProcess)
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", helper.getCorrelationValue())
            .create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("B")
        .await();

    ENGINE
        .message()
        .withName("interrupt")
        .withCorrelationKey(helper.getCorrelationValue())
        .withTimeToLive(Duration.ofMinutes(1))
        .publish();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("C")
        .activateElement("subprocess")
        .modify();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("C")
                .exists())
        .isTrue();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("subprocess")
                .limit(2)
                .count())
        .isEqualTo(2);
  }

  @Test
  public void shouldActivateInsideSpecificFlowScopeUsingAncestorSelection() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .eventSubProcess(
                    "event-sub",
                    sub ->
                        sub.startEvent()
                            .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
                            .interrupting(false)
                            .userTask("A")
                            .userTask("B")
                            .endEvent())
                .startEvent()
                .userTask("C")
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", helper.getCorrelationValue())
            .create();

    ENGINE.message().withName("msg").withCorrelationKey(helper.getCorrelationValue()).publish();
    ENGINE.message().withName("msg").withCorrelationKey(helper.getCorrelationValue()).publish();

    final List<Long> eventSubProcessKeys =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
            .limit(2)
            .map(Record::getKey)
            .toList();
    assertThat(eventSubProcessKeys)
        .describedAs("Expect that there are 2 active instances of the event sub process")
        .hasSize(2);

    // when
    final var ancestorScopeKey = eventSubProcessKeys.get(1);
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B", ancestorScopeKey)
        .modify();

    // then
    final var activatedTaskB =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("B")
            .findAny();
    assertThat(activatedTaskB).describedAs("Expect that task B is activated").isPresent();
    assertThat(activatedTaskB.get().getValue())
        .describedAs("Expect that task B exists inside of flow scope " + ancestorScopeKey)
        .hasFlowScopeKey(ancestorScopeKey);
  }

  @Test
  public void shouldActivateInsideNestedSpecificFlowScopeUsingAncestorSelection() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess("sub")
                .embeddedSubProcess()
                .startEvent()
                .exclusiveGateway("split")
                .defaultFlow()
                .userTask("A")
                .userTask("C")
                .exclusiveGateway("join")
                .moveToLastExclusiveGateway()
                .conditionExpression("false")
                .userTask("B")
                .connectTo("join")
                .endEvent()
                .subProcessDone()
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var activatedTaskA =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .findAny();
    assertThat(activatedTaskA).isPresent();

    // when
    final var ancestorScopeKey = processInstanceKey;
    final var modifiedRecord =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("B", ancestorScopeKey)
            .modify();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .skipUntil(r -> r.getPosition() >= modifiedRecord.getSourceRecordPosition())
                .limit(2))
        .extracting(Record::getValue)
        .extracting(ProcessInstanceRecordValue::getElementId)
        .describedAs("Expect that a new instance of the sub process and task B have been activated")
        .containsExactly("sub", "B");
  }

  @Test
  public void shouldActivateInsideNestedSpecificFlowScopeUsingAncestorSelectionWhenMultipleExist() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .eventSubProcess(
                    "event-sub",
                    sub ->
                        sub.startEvent()
                            .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
                            .interrupting(false)
                            .userTask("A")
                            .userTask("B")
                            .endEvent())
                .startEvent()
                .userTask("C")
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", helper.getCorrelationValue())
            .create();

    ENGINE.message().withName("msg").withCorrelationKey(helper.getCorrelationValue()).publish();
    ENGINE.message().withName("msg").withCorrelationKey(helper.getCorrelationValue()).publish();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("A")
                .limit(2))
        .describedAs(
            "Expect that task A activated twice and there are 2 active instances of the event sub process")
        .hasSize(2);

    // when
    final var ancestorScopeKey = processInstanceKey;
    final var modifiedRecord =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("B", ancestorScopeKey)
            .modify();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .skipUntil(r -> r.getPosition() >= modifiedRecord.getSourceRecordPosition())
                .limit(2))
        .extracting(Record::getValue)
        .extracting(ProcessInstanceRecordValue::getElementId)
        .describedAs("Expect that a new instance of the sub process and task B have been activated")
        .containsExactly("event-sub", "B");
  }

  @Test
  public void shouldUseAncestorSelectionWithMultiInstancesOfNestedInstances() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .eventSubProcess(
                    "event-sub",
                    sub ->
                        sub.zeebeInputExpression("null", "key2")
                            .startEvent()
                            .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
                            .interrupting(false)
                            .userTask("A")
                            .boundaryEvent(
                                "boundary",
                                b ->
                                    b.cancelActivity(false)
                                        .message(
                                            m ->
                                                m.name("msg2")
                                                    .zeebeCorrelationKeyExpression("key2"))
                                        .subProcess("sub")
                                        .embeddedSubProcess()
                                        .startEvent()
                                        .userTask("B")
                                        .userTask("C")
                                        .endEvent()
                                        .subProcessDone()
                                        .endEvent()))
                .startEvent()
                .userTask("D")
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", helper.getCorrelationValue())
            .create();

    ENGINE
        .message()
        .withName("msg")
        .withCorrelationKey(helper.getCorrelationValue())
        .withVariables(Map.of("key2", helper.getCorrelationValue() + "1"))
        .publish();
    ENGINE
        .message()
        .withName("msg")
        .withCorrelationKey(helper.getCorrelationValue())
        .withVariables(Map.of("key2", helper.getCorrelationValue() + "2"))
        .publish();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("A")
                .limit(2))
        .describedAs("Expect that task A activated 2 times")
        .hasSize(2);

    ENGINE
        .message()
        .withName("msg2")
        .withCorrelationKey(helper.getCorrelationValue() + "1")
        .publish();
    ENGINE
        .message()
        .withName("msg2")
        .withCorrelationKey(helper.getCorrelationValue() + "1")
        .publish();
    ENGINE
        .message()
        .withName("msg2")
        .withCorrelationKey(helper.getCorrelationValue() + "2")
        .publish();
    ENGINE
        .message()
        .withName("msg2")
        .withCorrelationKey(helper.getCorrelationValue() + "2")
        .publish();

    final var flowscopekeys =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("B")
            .limit(4)
            .map(Record::getValue)
            .map(ProcessInstanceRecordValue::getFlowScopeKey)
            .toList();
    assertThat(flowscopekeys)
        .describedAs("Expect that task B activated 4 times, twice per event-subprocess instance")
        .hasSize(4);

    // when
    final var ancestorScopeKey = flowscopekeys.getFirst();
    final var modifiedRecord =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("C", ancestorScopeKey)
            .modify();

    // then
    final var activatedTask =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .skipUntil(r -> r.getPosition() >= modifiedRecord.getSourceRecordPosition())
            .limit(1)
            .getFirst();
    assertThat(activatedTask.getValue())
        .describedAs("Expect that task C has been activated")
        .hasElementId("C")
        .hasFlowScopeKey(ancestorScopeKey);
  }

  @Test
  public void shouldActivateParallelGateway() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .parallelGateway("fork")
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .endEvent()
                .moveToNode("fork")
                .serviceTask("B", b -> b.zeebeJobType("B"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .hasSize(2);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("fork")
        .modify();

    // then
    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(4))
        .hasSize(4);
  }

  @Test
  public void shouldActivateInclusiveGateway() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .inclusiveGateway("fork")
                .conditionExpression("true")
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .endEvent()
                .moveToNode("fork")
                .conditionExpression("true")
                .serviceTask("B", b -> b.zeebeJobType("B"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .hasSize(2);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("fork")
        .modify();

    // then
    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(4))
        .hasSize(4);
  }

  @Test
  public void shouldActivateInclusiveGatewayRetainingAlreadyTakenFlows() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .inclusiveGateway("fork")
                .conditionExpression("true")
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .inclusiveGateway("join")
                .endEvent()
                .moveToNode("fork")
                .conditionExpression("true")
                .serviceTask("B", b -> b.zeebeJobType("B"))
                .connectTo("join")
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .hasSize(2);

    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ACTIVATE_ELEMENT)
        .onlyCommandRejections()
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("join")
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("join")
        .modify();

    // then
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("join")
        .await();

    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    Assertions.assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .processInstanceRecords()
                .withElementId("join")
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .describedAs("Expect that the join gateway activated again after completing task B")
        .hasSize(2);
  }

  @Test
  public void verifyCallActivityWithIncidentInOutputMappingCanBeTerminated() {
    final var child = Bpmn.createExecutableProcess("child").startEvent().endEvent().done();
    final var parent =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .callActivity("callActivity", c -> c.zeebeProcessId("child"))
            .zeebeOutputExpression("assert(x, x != null)", "y")
            .manualTask("task")
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(child).withXmlResource(parent).deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var callActivityElement =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("callActivity")
            .withElementType(BpmnElementType.CALL_ACTIVITY)
            .getFirst();

    Assertions.assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst())
        .extracting(r -> r.getValue().getElementId())
        .isEqualTo("callActivity");

    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("task")
        .terminateElement(callActivityElement.getKey())
        .modify();

    verifyThatRootElementIsActivated(processInstanceKey, "task", BpmnElementType.MANUAL_TASK);
    verifyThatProcessInstanceIsCompleted(processInstanceKey);
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(callActivityElement.getValue().getElementId())
                .exists())
        .isTrue();
  }

  @Test
  public void shouldTerminateProcessIfProcessInstanceKeyIsPassedAsTerminateInstruction() {
    // regression test for https://github.com/camunda/camunda/issues/11413
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask("A")
                .userTask("B")
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    getElementInstanceKeyOfElement(processInstanceKey, "A");

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .terminateElement(processInstanceKey)
        .modify();

    // then
    assertThatElementIsTerminated(processInstanceKey, PROCESS_ID);
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .onlyCommandRejections()
                .limit("B", ProcessInstanceIntent.ACTIVATE_ELEMENT))
        .describedAs("Activation of User Task B should be rejected")
        .isNotEmpty();
  }

  @Test
  public void shouldUseAncestorSelectionInsideMultiInstances() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "SubProcess",
                    sub ->
                        sub.multiInstance(
                            m ->
                                m.zeebeInputCollectionExpression("[1,2,3]")
                                    .zeebeInputElement("index")
                                    .parallel()))
                .embeddedSubProcess()
                .startEvent()
                .serviceTask("A", t -> t.zeebeJobType("A"))
                .serviceTask("B", t -> t.zeebeJobType("B"))
                .endEvent()
                .subProcessDone()
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var aTasks =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId("A")
            .limit(3)
            .toList();
    assertThat(aTasks).hasSize(3);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(aTasks.get(0).getKey())
        .activateElement("B", aTasks.get(0).getValue().getFlowScopeKey())
        .terminateElement(aTasks.get(2).getKey())
        .activateElement("B", aTasks.get(2).getValue().getFlowScopeKey())
        .modify();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withRecordKey(aTasks.get(0).getKey())
                .exists())
        .describedAs("Expect first A Task to be terminated")
        .isTrue();
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withRecordKey(aTasks.get(2).getKey())
                .exists())
        .describedAs("Expect third A Task to be terminated")
        .isTrue();
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId("B")
                .limit(2))
        .describedAs("Expect 2 B Tasks to be activated")
        .hasSize(2)
        .extracting(Record::getValue)
        .extracting(ProcessInstanceRecordValue::getFlowScopeKey)
        .describedAs("Expect each B Task to be activated inside specific flow scopes")
        .containsExactlyInAnyOrder(
            aTasks.get(0).getValue().getFlowScopeKey(), aTasks.get(2).getValue().getFlowScopeKey());
  }

  @Test
  public void shouldReActivateElementAndUpdateTreePath() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("A", a -> a.zeebeJobType("A"))
                .serviceTask("B", b -> b.zeebeJobType("B"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var elementBeforeModification =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst();
    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(elementBeforeModification.getKey())
        .activateElement("A")
        .modify();

    // then
    verifyThatElementTreePathIsUpdated(elementBeforeModification);
  }

  private void verifyThatRootElementIsActivated(
      final long processInstanceKey, final String elementId, final BpmnElementType elementType) {
    verifyThatElementIsActivated(processInstanceKey, elementId, elementType, processInstanceKey);
  }

  private void verifyThatElementIsActivated(
      final long processInstanceKey,
      final String elementId,
      final BpmnElementType elementType,
      final long flowScopeKey) {

    final var processActivatedEvent =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue();

    final var elementInstanceEvents =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withElementId(elementId)
            .withProcessInstanceKey(processInstanceKey)
            .limit(elementId, ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .toList();

    Assertions.assertThat(elementInstanceEvents)
        .extracting(Record::getIntent)
        .describedAs("Expect the element instance to have been activated")
        .containsExactly(
            ProcessInstanceIntent.ELEMENT_ACTIVATING, ProcessInstanceIntent.ELEMENT_ACTIVATED);

    Assertions.assertThat(elementInstanceEvents)
        .extracting(Record::getKey)
        .describedAs("Expect each element instance event to refer to the same entity")
        .containsOnly(elementInstanceEvents.getFirst().getKey());

    Assertions.assertThat(elementInstanceEvents)
        .extracting(Record::getValue)
        .describedAs("Expect each element instance event to contain the complete record value")
        .extracting(
            ProcessInstanceRecordValue::getProcessDefinitionKey,
            ProcessInstanceRecordValue::getBpmnProcessId,
            ProcessInstanceRecordValue::getVersion,
            ProcessInstanceRecordValue::getProcessInstanceKey,
            ProcessInstanceRecordValue::getBpmnElementType,
            ProcessInstanceRecordValue::getElementId,
            ProcessInstanceRecordValue::getFlowScopeKey,
            ProcessInstanceRecordValue::getParentProcessInstanceKey,
            ProcessInstanceRecordValue::getParentElementInstanceKey)
        .containsOnly(
            Tuple.tuple(
                processActivatedEvent.getProcessDefinitionKey(),
                processActivatedEvent.getBpmnProcessId(),
                processActivatedEvent.getVersion(),
                processActivatedEvent.getProcessInstanceKey(),
                elementType,
                elementId,
                flowScopeKey,
                -1L,
                -1L));
  }

  private void verifyThatElementIsCompleted(final long processInstanceKey, final String elementId) {

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords()
                .onlyEvents()
                .withElementId(elementId)
                .withProcessInstanceKey(processInstanceKey)
                .limit(elementId, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .describedAs("Expect the element instance to have been completed")
        .extracting(Record::getIntent)
        .containsSequence(
            ProcessInstanceIntent.ELEMENT_COMPLETING, ProcessInstanceIntent.ELEMENT_COMPLETED);
  }

  private void verifyThatProcessInstanceIsCompleted(final long processInstanceKey) {

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .limitToProcessInstanceCompleted()
                .findAny())
        .describedAs("Expect the process instance to have been completed")
        .isPresent();
  }

  private void completeJobs(final long processInstanceKey, final int numberOfJobs) {
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(numberOfJobs)
        .map(Record::getKey)
        .forEach(jobKey -> ENGINE.job().withKey(jobKey).complete());
  }

  private long getElementInstanceKeyOfElement(
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

  private void verifyThatElementTreePathIsUpdated(
      final Record<ProcessInstanceRecordValue> elementBeforeModification) {
    final var elementInstance = elementBeforeModification.getValue();

    final var modifiedRecord =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(elementInstance.getProcessInstanceKey())
            .withElementId(elementInstance.getElementId())
            .withElementType(elementInstance.getBpmnElementType())
            .withFlowScopeKey(elementInstance.getFlowScopeKey())
            .limit(2)
            .getLast();
    final var modifiedElementInstance = modifiedRecord.getValue();
    final var modifiedTreePath = modifiedElementInstance.getElementInstancePath();

    assertThat(modifiedRecord.getKey())
        .describedAs("Expect the element instance key to be updated")
        .isNotEqualTo(elementBeforeModification.getKey());

    assertThat(modifiedTreePath)
        .isNotNull()
        .isNotEmpty()
        .describedAs("Expect the tree path to be updated")
        .isNotEqualTo(elementInstance.getElementInstancePath());
  }
}
