/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.variable.mapping;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ZeebeVariablesMappingBuilder;
import io.camunda.zeebe.model.bpmn.instance.BoundaryEvent;
import io.camunda.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.camunda.zeebe.model.bpmn.instance.ReceiveTask;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import java.util.function.Consumer;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class MessageCatchElementOutputMappingTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";
  private static final String MESSAGE_NAME = "message";
  private static final String CORRELATION_VARIABLE = "key";
  private static final String MAPPING_ELEMENT_ID = "catch";

  private static final BpmnModelInstance CATCH_EVENT_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent(MAPPING_ELEMENT_ID)
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(CORRELATION_VARIABLE))
          .done();

  private static final BpmnModelInstance RECEIVE_TASK_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .receiveTask(MAPPING_ELEMENT_ID)
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(CORRELATION_VARIABLE))
          .done();

  private static final BpmnModelInstance INTERRUPTING_BOUNDARY_EVENT_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", b -> b.zeebeJobType("type"))
          .boundaryEvent(MAPPING_ELEMENT_ID)
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(CORRELATION_VARIABLE))
          .endEvent()
          .done();

  private static final BpmnModelInstance NON_INTERRUPTING_BOUNDARY_EVENT_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", b -> b.zeebeJobType("type"))
          .boundaryEvent(MAPPING_ELEMENT_ID, b -> b.cancelActivity(false))
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(CORRELATION_VARIABLE))
          .endEvent()
          .done();

  private static final BpmnModelInstance EVENT_BASED_GATEWAY_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .eventBasedGateway()
          .id("gateway")
          .intermediateCatchEvent(
              MAPPING_ELEMENT_ID,
              c ->
                  c.message(
                      m ->
                          m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(CORRELATION_VARIABLE)))
          .sequenceFlowId("to-end1")
          .endEvent("end1")
          .moveToLastGateway()
          .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT10S"))
          .sequenceFlowId("to-end2")
          .endEvent("end2")
          .done();

  private static final BpmnModelInstance INTERRUPTING_EVENT_SUBPROCESS_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .eventSubProcess(
              "event-subprocess",
              eventSubProcess ->
                  eventSubProcess
                      .startEvent(MAPPING_ELEMENT_ID)
                      .message(
                          m ->
                              m.name(MESSAGE_NAME)
                                  .zeebeCorrelationKeyExpression(CORRELATION_VARIABLE))
                      .interrupting(true)
                      .serviceTask("task-2", t -> t.zeebeJobType("type"))
                      .endEvent())
          .startEvent()
          .serviceTask("task", t -> t.zeebeJobType("type"))
          .endEvent()
          .done();

  private static final BpmnModelInstance NON_INTERRUPTING_EVENT_SUBPROCESS_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .eventSubProcess(
              "event-subprocess",
              eventSubProcess ->
                  eventSubProcess
                      .startEvent(MAPPING_ELEMENT_ID)
                      .message(
                          m ->
                              m.name(MESSAGE_NAME)
                                  .zeebeCorrelationKeyExpression(CORRELATION_VARIABLE))
                      .interrupting(false)
                      .serviceTask("task-2", t -> t.zeebeJobType("type"))
                      .endEvent())
          .startEvent()
          .serviceTask("task", t -> t.zeebeJobType("type"))
          .endEvent()
          .done();

  private static final BpmnModelInstance INTERRUPTING_BOUNDARY_EVENT_ON_RECEIVE_TASK_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .receiveTask(
              "task",
              t ->
                  t.message(
                      m -> m.name("other").zeebeCorrelationKeyExpression(CORRELATION_VARIABLE)))
          .boundaryEvent(MAPPING_ELEMENT_ID)
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(CORRELATION_VARIABLE))
          .endEvent()
          .done();

  private static final BpmnModelInstance NON_INTERRUPTING_BOUNDARY_EVENT_ON_RECEIVE_TASK_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .receiveTask(
              "task",
              t ->
                  t.message(
                      m -> m.name("other").zeebeCorrelationKeyExpression(CORRELATION_VARIABLE)))
          .boundaryEvent(MAPPING_ELEMENT_ID, b -> b.cancelActivity(false))
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(CORRELATION_VARIABLE))
          .endEvent()
          .done();

  private static final BpmnModelInstance MESSAGE_START_EVENT_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent(MAPPING_ELEMENT_ID)
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(CORRELATION_VARIABLE))
          .endEvent()
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter(0)
  public String elementType;

  @Parameter(1)
  public BpmnModelInstance process;

  @Parameter(2)
  public boolean createInstance;

  private String correlationKey;

  @Parameters(name = "{0}")
  public static Object[][] parameters() {
    return new Object[][] {
      {"intermediate catch event", CATCH_EVENT_PROCESS, true},
      {"receive task", RECEIVE_TASK_PROCESS, true},
      {"event-based gateway", EVENT_BASED_GATEWAY_PROCESS, true},
      {"interrupting boundary event", INTERRUPTING_BOUNDARY_EVENT_PROCESS, true},
      {"non-interrupting boundary event", NON_INTERRUPTING_BOUNDARY_EVENT_PROCESS, true},
      {"interrupting event subprocess", INTERRUPTING_EVENT_SUBPROCESS_PROCESS, true},
      {"non-interrupting event subprocess", NON_INTERRUPTING_EVENT_SUBPROCESS_PROCESS, true},
      {
        "interrupting boundary event on receive task",
        INTERRUPTING_BOUNDARY_EVENT_ON_RECEIVE_TASK_PROCESS,
        true
      },
      {
        "non-interrupting boundary event on receive task",
        NON_INTERRUPTING_BOUNDARY_EVENT_ON_RECEIVE_TASK_PROCESS,
        true
      },
      {"message start event", MESSAGE_START_EVENT_PROCESS, false}
    };
  }

  @Test
  public void shouldMergeMessageVariablesByDefault() {
    // given
    final var deploymentRecord = deployProcessWithMapping(e -> {});

    // when
    final long processInstanceKey = triggerProcessInstance(deploymentRecord);

    // then
    final Record<VariableRecordValue> variableEvent =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withScopeKey(processInstanceKey)
            .withName("foo")
            .getFirst();

    Assertions.assertThat(variableEvent.getValue())
        .hasValue("\"bar\"")
        .hasScopeKey(processInstanceKey);
  }

  @Test
  public void shouldMergeMessageVariables() {
    // given
    final var deploymentRecord = deployProcessWithMapping(e -> {});

    // when
    final long processInstanceKey = triggerProcessInstance(deploymentRecord);

    // then
    final Record<VariableRecordValue> variableEvent =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withScopeKey(processInstanceKey)
            .withName("foo")
            .getFirst();

    Assertions.assertThat(variableEvent.getValue())
        .hasValue("\"bar\"")
        .hasScopeKey(processInstanceKey);
  }

  @Test
  public void shouldMapMessageVariablesIntoInstanceVariables() {
    // given
    final var deploymentRecord =
        deployProcessWithMapping(e -> e.zeebeOutputExpression("foo", MESSAGE_NAME));

    // when
    final long processInstanceKey = triggerProcessInstance(deploymentRecord);

    // then
    final Record<VariableRecordValue> variableEvent =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName(MESSAGE_NAME)
            .getFirst();

    Assertions.assertThat(variableEvent.getValue())
        .hasValue("\"bar\"")
        .hasScopeKey(processInstanceKey);
  }

  private Record<DeploymentRecordValue> deployProcessWithMapping(
      final Consumer<ZeebeVariablesMappingBuilder<?>> c) {
    final BpmnModelInstance modifiedProcess = process.clone();
    final ModelElementInstance element = modifiedProcess.getModelElementById(MAPPING_ELEMENT_ID);
    if (element instanceof IntermediateCatchEvent) {
      c.accept(((IntermediateCatchEvent) element).builder());
    } else if (element instanceof StartEvent) {
      c.accept(((StartEvent) element).builder());
    } else if (element instanceof BoundaryEvent) {
      c.accept(((BoundaryEvent) element).builder());
    } else if (element instanceof SubProcess) {
      c.accept(((SubProcess) element).builder());
    } else {
      c.accept(((ReceiveTask) element).builder());
    }

    return ENGINE_RULE.deployment().withXmlResource(modifiedProcess).deploy();
  }

  private long triggerProcessInstance(final Record<DeploymentRecordValue> deploymentRecord) {
    correlationKey = UUID.randomUUID().toString();

    ENGINE_RULE
        .message()
        .withCorrelationKey(correlationKey)
        .withName(MESSAGE_NAME)
        .withVariables(asMsgPack("foo", "bar"))
        .publish();

    if (createInstance) {
      return ENGINE_RULE
          .processInstance()
          .ofBpmnProcessId(PROCESS_ID)
          .withVariable(CORRELATION_VARIABLE, correlationKey)
          .create();
    } else {
      return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
          .withProcessDefinitionKey(
              deploymentRecord.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey())
          .withElementType(BpmnElementType.PROCESS)
          .getFirst()
          .getKey();
    }
  }
}
