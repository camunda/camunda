/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.message;

import static io.zeebe.test.util.MsgPackUtil.asMsgPack;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ZeebeVariablesMappingBuilder;
import io.zeebe.model.bpmn.instance.BoundaryEvent;
import io.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.zeebe.model.bpmn.instance.ReceiveTask;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.VariableIntent;
import io.zeebe.protocol.record.value.VariableRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import java.util.function.Consumer;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MessageMappingTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";
  private static final String MESSAGE_NAME = "message";
  private static final String CORRELATION_VARIABLE = "key";
  private static final BpmnModelInstance CATCH_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("catch")
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKey(CORRELATION_VARIABLE))
          .done();
  private static final BpmnModelInstance RECEIVE_TASK_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .receiveTask("catch")
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKey(CORRELATION_VARIABLE))
          .done();
  private static final BpmnModelInstance INTERRUPTING_BOUNDARY_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", b -> b.zeebeTaskType("type"))
          .boundaryEvent("catch")
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKey(CORRELATION_VARIABLE))
          .endEvent()
          .done();
  private static final BpmnModelInstance NON_INTERRUPTING_BOUNDARY_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", b -> b.zeebeTaskType("type"))
          .boundaryEvent("catch", b -> b.cancelActivity(false))
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKey(CORRELATION_VARIABLE))
          .endEvent()
          .done();
  private static final BpmnModelInstance EVENT_BASED_GATEWAY_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .eventBasedGateway()
          .id("gateway")
          .intermediateCatchEvent(
              "catch",
              c -> c.message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKey(CORRELATION_VARIABLE)))
          .sequenceFlowId("to-end1")
          .endEvent("end1")
          .moveToLastGateway()
          .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT10S"))
          .sequenceFlowId("to-end2")
          .endEvent("end2")
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter(0)
  public String elementType;

  @Parameter(1)
  public BpmnModelInstance workflow;

  private String correlationKey;

  @Parameters(name = "{0}")
  public static Object[][] parameters() {
    return new Object[][] {
      {"intermediate catch event", CATCH_EVENT_WORKFLOW},
      {"receive task", RECEIVE_TASK_WORKFLOW},
      {"event-based gateway", EVENT_BASED_GATEWAY_WORKFLOW},
      {"interrupting boundary event", INTERRUPTING_BOUNDARY_EVENT_WORKFLOW},
      {"non-interrupting boundary event", NON_INTERRUPTING_BOUNDARY_EVENT_WORKFLOW},
    };
  }

  @Before
  public void setUp() {
    correlationKey = UUID.randomUUID().toString();
  }

  @Test
  public void shouldMergeMessageVariablesByDefault() {
    // given
    deployWorkflowWithMapping(e -> {});

    final long workflowInstanceKey =
        ENGINE_RULE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(CORRELATION_VARIABLE, correlationKey)
            .create();

    // when
    ENGINE_RULE
        .message()
        .withCorrelationKey(correlationKey)
        .withName(MESSAGE_NAME)
        .withVariables(asMsgPack("foo", "bar"))
        .publish();

    // then
    final Record<VariableRecordValue> variableEvent =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withName("foo")
            .getFirst();

    Assertions.assertThat(variableEvent.getValue())
        .hasValue("\"bar\"")
        .hasScopeKey(workflowInstanceKey);
  }

  @Test
  public void shouldMergeMessageVariables() {
    // given
    deployWorkflowWithMapping(e -> {});

    final long workflowInstanceKey =
        ENGINE_RULE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(CORRELATION_VARIABLE, correlationKey)
            .create();

    // when
    ENGINE_RULE
        .message()
        .withCorrelationKey(correlationKey)
        .withName(MESSAGE_NAME)
        .withVariables(asMsgPack("foo", "bar"))
        .publish();

    // then
    final Record<VariableRecordValue> variableEvent =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withName("foo")
            .getFirst();

    Assertions.assertThat(variableEvent.getValue())
        .hasValue("\"bar\"")
        .hasScopeKey(workflowInstanceKey);
  }

  @Test
  public void shouldMapMessageVariablesIntoInstanceVariables() {
    // given
    deployWorkflowWithMapping(e -> e.zeebeOutput("foo", MESSAGE_NAME));
    final long workflowInstanceKey =
        ENGINE_RULE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(CORRELATION_VARIABLE, correlationKey)
            .create();

    // when

    ENGINE_RULE
        .message()
        .withCorrelationKey(correlationKey)
        .withName(MESSAGE_NAME)
        .withVariables(asMsgPack("foo", "bar"))
        .publish();

    // then
    final Record<VariableRecordValue> variableEvent =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withName(MESSAGE_NAME)
            .getFirst();

    Assertions.assertThat(variableEvent.getValue())
        .hasValue("\"bar\"")
        .hasScopeKey(workflowInstanceKey);
  }

  private long deployWorkflowWithMapping(Consumer<ZeebeVariablesMappingBuilder<?>> c) {
    final BpmnModelInstance modifiedWorkflow = workflow.clone();
    final ModelElementInstance element = modifiedWorkflow.getModelElementById("catch");
    if (element instanceof IntermediateCatchEvent) {
      c.accept(((IntermediateCatchEvent) element).builder());
    } else if (element instanceof StartEvent) {
      c.accept(((StartEvent) element).builder());
    } else if (element instanceof BoundaryEvent) {
      c.accept(((BoundaryEvent) element).builder());
    } else {
      c.accept(((ReceiveTask) element).builder());
    }
    return ENGINE_RULE
        .deployment()
        .withXmlResource(modifiedWorkflow)
        .deploy()
        .getValue()
        .getDeployedWorkflows()
        .get(0)
        .getWorkflowKey();
  }
}
