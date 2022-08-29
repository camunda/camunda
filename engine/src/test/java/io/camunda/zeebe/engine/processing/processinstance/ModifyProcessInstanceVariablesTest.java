/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.assertj.core.groups.Tuple;
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
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that variable is created")
        .hasName("x")
        .hasValue("\"variable\"")
        .hasBpmnProcessId(PROCESS_ID)
        .hasProcessDefinitionKey(
            deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey())
        .hasProcessInstanceKey(processInstanceKey)
        .hasScopeKey(processInstanceKey);
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
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .describedAs("Expect that variable is created")
        .hasName("x")
        .hasValue("\"variable\"")
        .hasBpmnProcessId(PROCESS_ID)
        .hasProcessDefinitionKey(
            deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey())
        .hasProcessInstanceKey(processInstanceKey)
        .hasScopeKey(activatedElement.getKey());
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
    Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            VariableRecordValue::getName,
            VariableRecordValue::getValue,
            VariableRecordValue::getBpmnProcessId,
            VariableRecordValue::getProcessDefinitionKey,
            VariableRecordValue::getProcessInstanceKey,
            VariableRecordValue::getScopeKey)
        .describedAs("Expect that variable is created in correct scope")
        .containsExactlyInAnyOrder(
            Tuple.tuple(
                "x",
                "\"local\"",
                PROCESS_ID,
                deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey(),
                processInstanceKey,
                activatedElement.getKey()),
            Tuple.tuple(
                "y",
                "\"global\"",
                PROCESS_ID,
                deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey(),
                processInstanceKey,
                processInstanceKey));
  }

  // TODO testcase for creating variable on subprocess
}
