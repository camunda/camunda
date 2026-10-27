/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.EventSubProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.SubProcessBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ModifyProcessInstanceVariablesTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateGlobalVariables() {
    final var deployment =
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

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .withGlobalVariables(Map.of("x", "variable"))
        .modify();

    // then
    assertThatVariableCreatedInScope(
        processInstanceKey,
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey(),
        processInstanceKey,
        "x",
        "\"variable\"");
  }

  @Test
  public void shouldCreateGlobalVariablesForMove() {
    final var deployment =
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

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .moveElements("A", "B")
        .withGlobalVariables(Map.of("x", "variable"))
        .modify();

    // then
    assertThatVariableCreatedInScope(
        processInstanceKey,
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey(),
        processInstanceKey,
        "x",
        "\"variable\"");
  }

  @Test
  public void shouldUpdateGlobalVariablesIfTheyAlreadyExist() {
    final var deployment =
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

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("x", "variable"))
            .create();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .withGlobalVariables(Map.of("x", "updated"))
        .modify();

    // then
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.UPDATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that variable is updated")
        .hasName("x")
        .hasValue("\"updated\"")
        .hasBpmnProcessId(PROCESS_ID)
        .hasProcessDefinitionKey(
            deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey())
        .hasProcessInstanceKey(processInstanceKey)
        .hasScopeKey(processInstanceKey);
  }

  @Test
  public void shouldCreateVariablesBeforeElementScopes() {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .exclusiveGateway()
                .defaultFlow()
                .userTask("A")
                .endEvent()
                .moveToLastExclusiveGateway()
                .conditionExpression("false")
                .subProcess(
                    "subprocess",
                    sp -> sp.embeddedSubProcess().startEvent().userTask("B").endEvent())
                .boundaryEvent(
                    "timer", b -> b.timerWithDurationExpression("duration( durationVar )"))
                .endEvent()
                .moveToActivity("subprocess")
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
        .withGlobalVariables(Map.of("durationVar", "PT1H"))
        .modify();

    // then
    Assertions.assertThat(
            RecordingExporter.records()
                .skipUntil(
                    r ->
                        r.getValueType() == ValueType.PROCESS_INSTANCE_MODIFICATION
                            && r.getIntent() == ProcessInstanceModificationIntent.MODIFY)
                .onlyEvents()
                .limit(
                    r ->
                        r.getValueType() == ValueType.PROCESS_INSTANCE
                            && ((ProcessInstanceRecordValue) r.getValue())
                                .getElementId()
                                .equals("B")
                            && r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .extracting(
            Record::getValueType,
            r ->
                r.getValueType() == ValueType.PROCESS_INSTANCE
                    ? ((ProcessInstanceRecordValue) r.getValue()).getBpmnElementType()
                    : null,
            Record::getIntent)
        .describedAs("Expect to create variable before element scopes")
        .containsSubsequence(
            tuple(ValueType.VARIABLE, null, VariableIntent.CREATED),
            tuple(
                ValueType.PROCESS_INSTANCE,
                BpmnElementType.SUB_PROCESS,
                ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(
                ValueType.PROCESS_INSTANCE,
                BpmnElementType.USER_TASK,
                ProcessInstanceIntent.ELEMENT_ACTIVATING));

    Assertions.assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withHandlerNodeId("timer")
                .findAny())
        .describedAs("Expect timer boundary event subscription opened")
        .isPresent();
  }

  @Test
  public void shouldCreateLocalVariables() {
    final var deployment =
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

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .withVariables("B", Map.of("x", "variable"))
        .modify();

    final Record<ProcessInstanceRecordValue> activatedElement =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withElementId("B")
            .withProcessInstanceKey(processInstanceKey)
            .limit("B", ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .getFirst();

    // then
    assertThatVariableCreatedInScope(
        processInstanceKey,
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey(),
        activatedElement.getKey(),
        "x",
        "\"variable\"");
  }

  @Test
  public void shouldCreateLocalVariablesWithMove() {
    final var deployment =
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

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .moveElements("A", "B")
        .withVariables("B", Map.of("x", "variable"))
        .modify();

    final Record<ProcessInstanceRecordValue> activatedElement =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withElementId("B")
            .withProcessInstanceKey(processInstanceKey)
            .limit("B", ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .getFirst();

    // then
    assertThatVariableCreatedInScope(
        processInstanceKey,
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey(),
        activatedElement.getKey(),
        "x",
        "\"variable\"");
  }

  @Test
  public void shouldCreateLocalAndGlobalVariables() {
    final var deployment =
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

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .withVariables("B", Map.of("x", "local"))
        .withGlobalVariables(Map.of("y", "global"))
        .modify();

    final Record<ProcessInstanceRecordValue> activatedElement =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withElementId("B")
            .withProcessInstanceKey(processInstanceKey)
            .limit("B", ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .getFirst();

    // then
    assertThatVariableCreatedInScope(
        processInstanceKey,
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey(),
        activatedElement.getKey(),
        "x",
        "\"local\"");
    assertThatVariableCreatedInScope(
        processInstanceKey,
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey(),
        processInstanceKey,
        "y",
        "\"global\"");
  }

  @Test
  public void shouldCreateLocalVariablesInNonExistingFlowscope() {
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .userTask("A")
                    .subProcess(
                        "sp", sp -> sp.embeddedSubProcess().startEvent().userTask("B").endEvent())
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
        .withVariables("sp", Map.of("x", "variable"))
        .modify();

    final Record<ProcessInstanceRecordValue> activatedElement =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withElementId("sp")
            .withProcessInstanceKey(processInstanceKey)
            .limit("sp", ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .getFirst();

    // then
    assertThatVariableCreatedInScope(
        processInstanceKey,
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey(),
        activatedElement.getKey(),
        "x",
        "\"variable\"");
  }

  @Test
  public void shouldCreateLocalVariablesInExistingFlowscope() {
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .subProcess(
                        "sp",
                        sp ->
                            sp.embeddedSubProcess()
                                .startEvent()
                                .userTask("A")
                                .userTask("B")
                                .endEvent())
                    .endEvent()
                    .done())
            .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<ProcessInstanceRecordValue> activatedElement =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withElementId("sp")
            .withProcessInstanceKey(processInstanceKey)
            .limit("sp", ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .getFirst();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("B")
        .withVariables("sp", Map.of("x", "variable"))
        .modify();

    // then
    assertThatVariableCreatedInScope(
        processInstanceKey,
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey(),
        activatedElement.getKey(),
        "x",
        "\"variable\"");
  }

  @Test
  public void shouldCreateVariablesBeforeEventSubscriptions() {
    final Consumer<EventSubProcessBuilder> eventSubProcess =
        esp ->
            esp.startEvent()
                .message(
                    m -> m.name("event-subprocess-start").zeebeCorrelationKeyExpression("local"))
                .userTask("B")
                .endEvent();
    final Consumer<SubProcessBuilder> subprocess =
        sp ->
            sp.embeddedSubProcess()
                .eventSubProcess("event-subprocess", eventSubProcess)
                .startEvent()
                .userTask("C")
                .endEvent();

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .userTask("A")
                    .subProcess("sp", subprocess)
                    .boundaryEvent()
                    .message(m -> m.name("message").zeebeCorrelationKeyExpression("global"))
                    .endEvent()
                    .done())
            .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.processInstanceRecords()
        .onlyEvents()
        .withElementId("A")
        .withProcessInstanceKey(processInstanceKey)
        .limit("A", ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .getFirst();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("C")
        .withVariables("sp", Map.of("local", "local"))
        .withGlobalVariables(Map.of("global", "global"))
        .modify();

    final long subProcessKey =
        RecordingExporter.processInstanceRecords()
            .onlyEvents()
            .withElementId("sp")
            .withProcessInstanceKey(processInstanceKey)
            .limit("sp", ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .getFirst()
            .getKey();

    // then
    assertThatVariableCreatedInScope(
        processInstanceKey,
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey(),
        subProcessKey,
        "local",
        "\"local\"");
    assertThatVariableCreatedInScope(
        processInstanceKey,
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey(),
        processInstanceKey,
        "global",
        "\"global\"");
    Assertions.assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2L))
        .extracting(Record::getValue)
        .extracting(
            MessageSubscriptionRecordValue::getCorrelationKey,
            MessageSubscriptionRecordValue::getElementInstanceKey)
        .containsExactlyInAnyOrder(tuple("global", subProcessKey), tuple("local", subProcessKey));
  }

  private void assertThatVariableCreatedInScope(
      final long processInstanceKey,
      final long processDefinitionKey,
      final long scopeKey,
      final String name,
      final String value) {
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(scopeKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that variable is created")
        .hasName(name)
        .hasValue(value)
        .hasBpmnProcessId(PROCESS_ID)
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasProcessInstanceKey(processInstanceKey)
        .hasScopeKey(scopeKey);
  }
}
