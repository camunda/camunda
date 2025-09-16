/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.ByteValue;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ModifyProcessInstanceRejectionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @ClassRule
  public static final BrokerClassRuleHelper CLASS_RULE_HELPER = new BrokerClassRuleHelper();

  private static final String PROCESS_ID = "process";
  private static final long MAX_MESSAGE_SIZE = ByteValue.ofMegabytes(4);

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectCommandWhenProcessInstanceIsUnknown() {
    // given
    final long unknownKey = 12345L;

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(unknownKey)
        .modification()
        .activateElement("A")
        .expectRejection()
        .modify();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceModificationRecords().onlyCommandRejections().getFirst();

    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceModificationIntent.MODIFY)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            String.format(
                "Expected to modify process instance but no process instance found with key '%d'",
                unknownKey))
        .hasKey(unknownKey);
  }

  @Test
  public void shouldRejectCommandWhenAtLeastOneActivateElementIdIsUnknown() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask("A").endEvent().done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("A")
            .activateElement("B")
            .activateElement("C")
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .describedAs("Expect that elements with ids 'B' and 'C' are not found")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            ("Expected to modify instance of process '%s' but it contains one or more activate instructions"
                    + " with an element that could not be found: 'B', 'C'")
                .formatted(PROCESS_ID));
  }

  @Test
  public void shouldRejectCommandWhenAtLeastOneTerminateElementInstanceKeyIsUnknown() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask("A").endEvent().done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var userTaskActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .terminateElement(userTaskActivated.getKey())
            .terminateElement(123L)
            .terminateElement(456L)
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .describedAs("Expect that element instance with key '123' and '456' are not found")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            String.format(
                "Expected to modify instance of process '%s' but it contains one or more terminate "
                    + "instructions with an element instance that could not be found: '123', '456'",
                PROCESS_ID));
  }

  @Test
  public void shouldRejectCommandWhenFlowScopeCantBeCreated() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask("A")
                .subProcess(
                    "sp", sp -> sp.embeddedSubProcess().startEvent().userTask("B").endEvent())
                .boundaryEvent()
                .message(m -> m.name("message").zeebeCorrelationKeyExpression("missingVariable"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("B")
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .describedAs("Expect that flow scope could not be created")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
            Expected to subscribe to catch event(s) of 'sp' but \
            Failed to extract the correlation key for 'missingVariable': \
            The value must be either a string or a number, but was 'NULL'. \
            The evaluation reported the following warnings:
            [NO_VARIABLE_FOUND] No variable found with name 'missingVariable'""");
  }

  @Test
  public void shouldRejectCommandWhenItExceedsMaxMessageSize() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask("A").endEvent().done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("A")
            .withGlobalVariables(
                // Create a variable with a size of the max message size, minus 1 kB, in order to
                // save some space for the rest of the message.
                Map.of("x", "x".repeat((int) (MAX_MESSAGE_SIZE - ByteValue.ofKilobytes(1)))))
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .describedAs("Expect that message batch size too large")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            ("Unable to modify process instance with key '%d' as the size exceeds the maximum batch "
                    + "size. Please reduce the size by splitting the modification into multiple commands.")
                .formatted(processInstanceKey));
  }

  @Test
  public void shouldRejectCommandWhenVariableScopeIsUnknown() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask("A")
                .userTask("B")
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("B")
            .withVariables("B", Map.of("var", "B"))
            .withVariables("C", Map.of("var", "C"))
            .activateElement("A")
            .withVariables("D", Map.of("var", "D"))
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .describedAs("Expect that variable scopes with ids 'C' and 'D' are not found")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            String.format(
                """
                Expected to modify instance of process '%s' but it contains one or more variable \
                instructions with a scope element id that could not be found: 'C', 'D'""",
                PROCESS_ID));
  }

  @Test
  public void shouldRejectCommandWhenVariableScopeIsNotFlowScope() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask("A")
                .userTask("B")
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("B")
            .withVariables(PROCESS_ID, Map.of("var", "process"))
            .withVariables("A", Map.of("var", "A"))
            .activateElement("A")
            .withVariables("B", Map.of("var", "B"))
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .describedAs("Expect that variable scopes with ids 'A' and 'B' are no flow scopes")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            String.format(
                """
                Expected to modify instance of process '%s' but it contains one or more variable \
                instructions with a scope element that doesn't belong to the activating element's \
                flow scope. These variables should be set before or after the modification.""",
                PROCESS_ID));
  }

  @Test
  public void shouldRejectCommandWhenMoreThanOneAncestor() {
    // given
    final String correlationKey = CLASS_RULE_HELPER.getCorrelationValue();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .eventSubProcess(
                    "event-subprocess",
                    eventSubprocess ->
                        eventSubprocess
                            .startEvent()
                            .interrupting(false)
                            .message(m -> m.name("start").zeebeCorrelationKeyExpression("key"))
                            .userTask("B")
                            .userTask("C")
                            .endEvent())
                .startEvent()
                .userTask("A")
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", correlationKey)
            .create();

    ENGINE.message().withName("start").withCorrelationKey(correlationKey).publish();
    ENGINE.message().withName("start").withCorrelationKey(correlationKey).publish();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("B")
                .limit(2)
                .count())
        .describedAs("Assuming that two event subprocesses are active")
        .isEqualTo(2);

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("C")
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .describedAs("Expect that the flow scope can have only one instance")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            String.format(
                """
                Expected to modify instance of process '%s' but it contains one or more activate \
                instructions for an element that has a flow scope with more than one active \
                instance: 'event-subprocess'. Can't decide in which instance of the flow scope the \
                element should be activated. Please specify an ancestor element instance key for \
                this activate instruction.""",
                PROCESS_ID));
  }

  @Test
  public void shouldRejectTerminationOfChildProcess() {
    // given
    final var callActivityProcessId = "callActivityProcess";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(callActivityProcessId)
                .startEvent()
                .userTask("A")
                .endEvent()
                .done())
        .deploy();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .callActivity(
                    "callActivity",
                    callActivity -> callActivity.zeebeProcessId(callActivityProcessId))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var userTaskKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst()
            .getKey();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .terminateElement(userTaskKey)
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .describedAs("Expect that a child instance may not be terminated directly")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            String.format(
                """
                Expected to modify instance of process '%s' but the given instructions would terminate \
                the instance. The instance was created by a call activity in the parent process. \
                To terminate this instance please modify the parent process instead.""",
                callActivityProcessId));
  }

  @Test
  public void shouldRejectActivationWithNonExistingAncestor() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask("A").endEvent().done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("A", 12345L)
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .describedAs("Expect that the ancestor with key 12345 is not found")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            ("Expected to modify instance of process '%s' but it contains one or more activate"
                    + " instructions with an ancestor scope key that does not exist, or is not in an"
                    + " active state: '12345'")
                .formatted(PROCESS_ID));
  }

  @Test
  public void shouldRejectActivationWhenAncestorScopeIsActivating() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "sp", sp -> sp.embeddedSubProcess().startEvent().userTask("A").endEvent())
                .zeebeInputExpression("assert(doesNotExist, doesNotExist != null)", "variable")
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var subProcessKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("sp")
            .getFirst()
            .getKey();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("A", subProcessKey)
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .describedAs("Expect that activating an element in an ACTIVATING ancestor is not allowed")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            ("Expected to modify instance of process '%s' but it contains one or more activate"
                    + " instructions with an ancestor scope key that does not exist, or is not in an"
                    + " active state: '%d'")
                .formatted(PROCESS_ID, subProcessKey));
  }

  @Test
  public void shouldRejectActivationWhenAncestorScopeIsCompleted() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess("sp", sp -> sp.embeddedSubProcess().startEvent().task("A").endEvent())
                .userTask("B")
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var subProcessKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("sp")
            .getFirst()
            .getKey();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("A", subProcessKey)
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .describedAs("Expect that activating an element in a COMPLETING ancestor is not allowed")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            ("Expected to modify instance of process '%s' but it contains one or more activate"
                    + " instructions with an ancestor scope key that does not exist, or is not in an"
                    + " active state: '%d'")
                .formatted(PROCESS_ID, subProcessKey));
  }

  @Test
  public void shouldRejectActivationWhenAncestorScopeIsTerminated() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "sp", sp -> sp.embeddedSubProcess().startEvent().userTask("A").endEvent())
                .boundaryEvent(
                    "be",
                    be ->
                        be.message(
                            m ->
                                m.name("msg").zeebeCorrelationKeyExpression("=\"correlationKey\"")))
                .cancelActivity(true)
                .userTask("B")
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    ENGINE.message().withName("msg").withCorrelationKey("correlationKey").publish();
    final var subProcessKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("sp")
            .getFirst()
            .getKey();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("A", subProcessKey)
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .describedAs("Expect that activating an element in a TERMINATING ancestor is not allowed")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            ("Expected to modify instance of process '%s' but it contains one or more activate"
                    + " instructions with an ancestor scope key that does not exist, or is not in an"
                    + " active state: '%d'")
                .formatted(PROCESS_ID, subProcessKey));
  }

  @Test
  public void shouldRejectActivationWhenAncestorScopeIsNotFlowScope() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .parallelGateway("split")
                .subProcess(
                    "subProcess1",
                    sp -> sp.embeddedSubProcess().startEvent().manualTask("A").endEvent())
                .parallelGateway("join")
                .moveToLastGateway()
                .subProcess(
                    "subProcess2",
                    sp2 -> sp2.embeddedSubProcess().startEvent().userTask("B").endEvent())
                .connectTo("join")
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("subProcess1")
        .await();

    // when
    final var subProcess2 =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("subProcess2")
            .getFirst();
    final var taskB =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("B")
            .getFirst();
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("A", subProcess2.getKey())
            .activateElement("B", subProcess2.getKey())
            .activateElement("A", taskB.getKey())
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .describedAs("Expect that subProcess2 cannot be selected as ancestor of task A")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
                Expected to modify instance of process '%s' \
                but it contains one or more activate instructions with an ancestor scope key \
                that is not an ancestor of the element to activate:\
                %n- instance '%s' of element 'subProcess2' is not an ancestor of element 'A'\
                %n- instance '%s' of element 'B' is not an ancestor of element 'A'"""
                .formatted(PROCESS_ID, subProcess2.getKey(), taskB.getKey()));
  }

  @Test
  public void shouldRejectActivationOfMultiInstanceInstance() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask("A")
                .subProcess(
                    "subprocess", s -> s.embeddedSubProcess().startEvent().manualTask("B").done())
                .multiInstance(m -> m.zeebeInputCollectionExpression("[1]"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("A")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("B")
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .describedAs("Expect we cannot activate an instance of the multi-instance")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
            Expected to modify instance of process 'process' but it contains one or more activate \
            instructions that would result in the activation of multi-instance element \
            'subprocess', which is currently unsupported.""");
  }

  @Test
  public void shouldRejectSelectedAncestorIsMultiInstance() {
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
                .endEvent()
                .subProcessDone()
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId("A")
                .limit(3))
        .describedAs("Wait until all service tasks have activated")
        .hasSize(3);

    // when
    final long multiInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
            .getFirst()
            .getKey();
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("A", multiInstanceKey)
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .describedAs("Expect that a multi-instance body may not be selected as ancestor")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            String.format(
                """
                Expected to modify instance of process '%s' but it contains one or more activate \
                instructions that would result in the activation of multi-instance element \
                'SubProcess', which is currently unsupported.""",
                PROCESS_ID));
  }

  @Test
  public void shouldRejectSelectedAncestorWouldActivateMultiInstance() {
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
                                m.zeebeInputCollectionExpression("[1]").zeebeInputElement("index")))
                .embeddedSubProcess()
                .startEvent()
                .serviceTask("A", t -> t.zeebeJobType("A"))
                .endEvent()
                .subProcessDone()
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withElementId("A")
        .await();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("A", processInstanceKey)
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .describedAs(
            "Expect that the activation of a multi-instance's descendant may not result in an activated multi-instance")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            String.format(
                """
                Expected to modify instance of process '%s' but it contains one or more activate \
                instructions that would result in the activation of multi-instance element \
                'SubProcess', which is currently unsupported.""",
                PROCESS_ID));
  }

  @Test
  public void shouldRejectActivationWhenAncestorBelongsToDifferentProcessInstance() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    "sp", sp -> sp.embeddedSubProcess().startEvent().userTask("A").endEvent())
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKeyOne = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKeyOne)
        .withElementId("A")
        .await();
    final var processInstanceKeyTwo = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var subProcessKeyTwo =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKeyTwo)
            .withElementId("A")
            .getFirst()
            .getKey();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKeyOne)
            .modification()
            .activateElement("A", subProcessKeyTwo)
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .describedAs(
            "Expect that activating an element with ancestor key of a different process"
                + " instance is not allowed")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
                Expected to modify instance of process '%s' but it contains one or more \
                activate instructions with an ancestor scope key that does not belong to the \
                modified process instance: '%d'"""
                .formatted(PROCESS_ID, subProcessKeyTwo));
  }

  @Test
  public void shouldRejectActivationWhenFlowScopeIsTerminated() {
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
                            .endEvent("sub-end"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var subProcessElementInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SUB_PROCESS)
            .getFirst();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("sub-end")
            .terminateElement(subProcessElementInstance.getKey())
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .describedAs("Expect that activation is rejected when flow scope is terminated")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            """
              Expected to modify instance of process '%s' but it contains one or more activate instructions \
              for elements whose required flow scope instance is also being terminated: element 'sub-end' requires flow scope instance '%s' \
              which is being terminated. Please provide a valid ancestor scope key for the activation or avoid terminating the required flow scope."""
                .formatted(PROCESS_ID, subProcessElementInstance.getKey()));
  }

  @Test
  public void shouldRejectMultipleActivationsWhenFlowScopeIsTerminated() {
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
                            .endEvent("sub-end"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var subProcessElementInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SUB_PROCESS)
            .getFirst();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("A")
            .activateElement("B")
            .terminateElement(subProcessElementInstance.getKey())
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .describedAs("Expect that multiple activations are rejected when flow scope is terminated")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            ("Expected to modify instance of process '%s' but it contains one or more activate instructions "
                    + "for elements whose required flow scope instance is also being terminated: "
                    + "element 'A' requires flow scope instance '%s' which is being terminated, "
                    + "element 'B' requires flow scope instance '%s' which is being terminated. "
                    + "Please provide a valid ancestor scope key for the activation or avoid terminating the required flow scope.")
                .formatted(
                    PROCESS_ID,
                    subProcessElementInstance.getKey(),
                    subProcessElementInstance.getKey()));
  }

  @Test
  public void shouldRejectOnlyInvalidActivationsWhenFlowScopeIsTerminated() {
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
                            .endEvent("sub-end"))
                .serviceTask("C", c -> c.zeebeJobType("C"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var subProcessElementInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SUB_PROCESS)
            .getFirst();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("B") // inside terminated scope
            .activateElement("C") // outside terminated scope
            .terminateElement(subProcessElementInstance.getKey())
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .describedAs(
            "Expect that only invalid activation is rejected when flow scope is terminated")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            ("Expected to modify instance of process '%s' but it contains one or more activate instructions "
                    + "for elements whose required flow scope instance is also being terminated: "
                    + "element 'B' requires flow scope instance '%s' which is being terminated. "
                    + "Please provide a valid ancestor scope key for the activation or avoid terminating the required flow scope.")
                .formatted(PROCESS_ID, subProcessElementInstance.getKey()));
  }

  @Test
  public void shouldRejectActivationWithAncestorScopeKeyIfTerminated() {
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
                            .endEvent("sub-end"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var subProcessElementInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SUB_PROCESS)
            .getFirst();

    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("sub-end", subProcessElementInstance.getKey())
            .terminateElement(subProcessElementInstance.getKey())
            .expectRejection()
            .modify();

    // then
    assertThat(rejection)
        .describedAs(
            "Expect that activation with ancestor scope key is rejected if ancestor is terminated")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            ("Expected to modify instance of process '%s' but it contains one or more activate instructions "
                    + "for elements whose required flow scope instance is also being terminated: "
                    + "element 'sub-end' requires flow scope instance '%s' which is being terminated. "
                    + "Please provide a valid ancestor scope key for the activation or avoid terminating the required flow scope.")
                .formatted(PROCESS_ID, subProcessElementInstance.getKey()));
  }

  @Test
  public void shouldAcceptActivationWithValidAncestorScopeKey() {
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
                            .endEvent("sub-end"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var subProcessElementInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SUB_PROCESS)
            .getFirst();

    // when
    final var event =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("sub-end", processInstanceKey)
            .terminateElement(subProcessElementInstance.getKey())
            .modify();

    // then
    assertThat(event)
        .describedAs("Expect that activation with valid ancestor scope key is accepted")
        .hasRecordType(io.camunda.zeebe.protocol.record.RecordType.EVENT)
        .hasIntent(ProcessInstanceModificationIntent.MODIFIED);
  }

  @Test
  public void shouldRejectActivationInsideTerminatedEventSubProcess() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .eventSubProcess(
                    "event-subprocess",
                    esp ->
                        esp.startEvent()
                            .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
                            .serviceTask("B", t -> t.zeebeJobType("B"))
                            .endEvent())
                .startEvent()
                .userTask("C")
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "1").create();
    ENGINE.message().withName("msg").withCorrelationKey("1").publish();
    final var eventSubProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
            .getFirst();
    // when
    final var rejection =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("B")
            .terminateElement(eventSubProcessInstance.getKey())
            .expectRejection()
            .modify();
    // then
    assertThat(rejection)
        .describedAs("Expect activation inside terminated event subprocess is rejected")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            ("Expected to modify instance of process '%s' but it contains one or more activate instructions "
                    + "for elements whose required flow scope instance is also being terminated: "
                    + "element 'B' requires flow scope instance '%s' which is being terminated. "
                    + "Please provide a valid ancestor scope key for the activation or avoid terminating the required flow scope.")
                .formatted(PROCESS_ID, eventSubProcessInstance.getKey()));
  }

  @Test
  public void shouldAcceptActivationWithValidAncestorScopeKeyInEventSubProcess() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .eventSubProcess(
                    "event-subprocess",
                    esp ->
                        esp.startEvent()
                            .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
                            .serviceTask("B", t -> t.zeebeJobType("B"))
                            .endEvent())
                .startEvent()
                .userTask("C")
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "1").create();
    ENGINE.message().withName("msg").withCorrelationKey("1").publish();
    final var eventSubProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.EVENT_SUB_PROCESS)
            .getFirst();
    // when
    final var event =
        ENGINE
            .processInstance()
            .withInstanceKey(processInstanceKey)
            .modification()
            .activateElement("B", eventSubProcessInstance.getKey())
            .modify();
    // then
    assertThat(event)
        .describedAs(
            "Expect activation with valid ancestor scope key in event subprocess is accepted")
        .hasRecordType(io.camunda.zeebe.protocol.record.RecordType.EVENT)
        .hasIntent(ProcessInstanceModificationIntent.MODIFIED);
  }
}
