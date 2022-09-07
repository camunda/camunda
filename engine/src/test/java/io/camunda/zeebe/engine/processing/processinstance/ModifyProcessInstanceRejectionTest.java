/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.ByteValue;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ModifyProcessInstanceRejectionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
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
            ("Expected to subscribe to catch event(s) of 'sp' but failed to evaluate expression "
                + "'missingVariable': no variable found for name 'missingVariable'"));
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
                Expected to modify instance of process '%s' but it contains one or more variable instructions \
                with a scope element id that could not be found: 'C', 'D'""",
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
                Expected to modify instance of process '%s' but it contains one or more variable instructions \
                with a scope element that doesn't belong the element's flow scope: 'A', 'B'. \
                These variables should be set before or after the modification.""",
                PROCESS_ID));
  }
}
