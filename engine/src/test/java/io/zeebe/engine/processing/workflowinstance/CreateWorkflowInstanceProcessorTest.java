/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.workflowinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.zeebe.el.ExpressionLanguageFactory;
import io.zeebe.engine.processing.CommandProcessorTestCase;
import io.zeebe.engine.processing.common.ExpressionProcessor;
import io.zeebe.engine.processing.common.ExpressionProcessor.VariablesLookup;
import io.zeebe.engine.processing.deployment.transform.DeploymentTransformer;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.DeployedWorkflow;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.engine.state.instance.VariablesState;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.Workflow;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.collection.Maps;
import io.zeebe.util.buffer.BufferUtil;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public final class CreateWorkflowInstanceProcessorTest
    extends CommandProcessorTestCase<WorkflowInstanceCreationRecord> {

  private static final BpmnModelInstance VALID_WORKFLOW =
      Bpmn.createExecutableProcess().startEvent().endEvent().done();
  private static DeploymentTransformer transformer;

  private ZeebeState state;
  private KeyGenerator keyGenerator;
  private WorkflowState workflowState;
  private ElementInstanceState elementInstanceState;
  private VariablesState variablesState;
  private CreateWorkflowInstanceProcessor processor;

  @BeforeClass
  public static void init() {
    final VariablesLookup emptyLookup = (variable, scopeKey) -> null;
    final var expressionProcessor =
        new ExpressionProcessor(ExpressionLanguageFactory.createExpressionLanguage(), emptyLookup);
    transformer =
        new DeploymentTransformer(
            CommandProcessorTestCase.ZEEBE_STATE_RULE.getZeebeState(), expressionProcessor);
  }

  @Before
  public void setUp() {
    state = CommandProcessorTestCase.ZEEBE_STATE_RULE.getZeebeState();
    keyGenerator = state.getKeyGenerator();
    workflowState = state.getWorkflowState();
    elementInstanceState = workflowState.getElementInstanceState();
    variablesState = elementInstanceState.getVariablesState();

    processor =
        new CreateWorkflowInstanceProcessor(
            workflowState, elementInstanceState, variablesState, keyGenerator);
  }

  @Test
  public void shouldRejectIfNoBpmnProcessIdOrKeyGiven() {
    // given
    final TypedRecord<WorkflowInstanceCreationRecord> command =
        newCommand(WorkflowInstanceCreationRecord.class);

    // when
    processor.onCommand(command, controller, streamWriter);

    // then
    refuteAccepted();
    assertRejected(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectIfNoWorkflowFoundByKey() {
    // given
    final TypedRecord<WorkflowInstanceCreationRecord> command =
        newCommand(WorkflowInstanceCreationRecord.class);
    command.getValue().setWorkflowKey(keyGenerator.nextKey());

    // when
    processor.onCommand(command, controller, streamWriter);

    // then
    refuteAccepted();
    assertRejected(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectIfNoWorkflowFoundByProcessId() {
    // given
    final TypedRecord<WorkflowInstanceCreationRecord> command =
        newCommand(WorkflowInstanceCreationRecord.class);
    command.getValue().setBpmnProcessId("workflow");

    // when
    processor.onCommand(command, controller, streamWriter);

    // then
    refuteAccepted();
    assertRejected(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectIfNoWorkflowFoundByProcessIdAndVersion() {
    // given
    final TypedRecord<WorkflowInstanceCreationRecord> command =
        newCommand(WorkflowInstanceCreationRecord.class);
    command.getValue().setBpmnProcessId("workflow").setVersion(1);

    // when
    processor.onCommand(command, controller, streamWriter);

    // then
    refuteAccepted();
    assertRejected(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectIfWorkflowHasNoNoneStartEvent() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess()
            .startEvent()
            .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
            .endEvent()
            .done();
    final DeployedWorkflow workflow = deployNewWorkflow(process);
    final TypedRecord<WorkflowInstanceCreationRecord> command =
        newCommand(WorkflowInstanceCreationRecord.class);
    command.getValue().setBpmnProcessId(workflow.getBpmnProcessId());

    // when
    processor.onCommand(command, controller, streamWriter);

    // then
    refuteAccepted();
    assertRejected(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldRejectIfVariablesIsAnInvalidDocument() {
    // given
    final DeployedWorkflow workflow = deployNewWorkflow();
    final TypedRecord<WorkflowInstanceCreationRecord> command =
        newCommand(WorkflowInstanceCreationRecord.class);
    final MutableDirectBuffer badDocument = new UnsafeBuffer(MsgPackUtil.asMsgPack("{ 'foo': 1 }"));
    command.getValue().setBpmnProcessId(workflow.getBpmnProcessId()).setVariables(badDocument);
    badDocument.putByte(0, (byte) 0); // overwrites map header

    // when
    processor.onCommand(command, controller, streamWriter);

    // then
    refuteAccepted();
    assertRejected(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldActivateElementInstance() {
    // given
    final DirectBuffer variables =
        MsgPackUtil.asMsgPack(Maps.of(entry("foo", "bar"), entry("baz", "boz")));
    final DeployedWorkflow workflow = deployNewWorkflow();
    final TypedRecord<WorkflowInstanceCreationRecord> command =
        newCommand(WorkflowInstanceCreationRecord.class);
    command.getValue().setBpmnProcessId(workflow.getBpmnProcessId()).setVariables(variables);

    // when
    processor.onCommand(command, controller, streamWriter);

    // then
    final WorkflowInstanceCreationRecord acceptedRecord =
        getAcceptedRecord(WorkflowInstanceCreationIntent.CREATED);
    final long instanceKey = acceptedRecord.getWorkflowInstanceKey();
    final ElementInstance instance = elementInstanceState.getInstance(instanceKey);
    final WorkflowInstanceRecord expectedElementActivatingRecord =
        newExpectedElementActivatingRecord(workflow, instanceKey);
    assertThat(instance.getState()).isEqualTo(WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    assertThat(instance.getValue()).isEqualTo(expectedElementActivatingRecord);
    verifyElementActivatingPublished(instanceKey, instance);
  }

  @Test
  public void shouldSetVariablesFromDocument() {
    // given
    final Map<String, Object> document = Maps.of(entry("foo", "bar"), entry("baz", "boz"));
    final DeployedWorkflow workflow = deployNewWorkflow();
    final TypedRecord<WorkflowInstanceCreationRecord> command =
        newCommand(WorkflowInstanceCreationRecord.class);
    command
        .getValue()
        .setBpmnProcessId(workflow.getBpmnProcessId())
        .setVariables(MsgPackUtil.asMsgPack(document));

    // when
    processor.onCommand(command, controller, streamWriter);

    // then
    final WorkflowInstanceCreationRecord acceptedRecord =
        getAcceptedRecord(WorkflowInstanceCreationIntent.CREATED);
    final long scopeKey = acceptedRecord.getWorkflowInstanceKey();
    MsgPackUtil.assertEquality(
        variablesState.getVariableLocal(scopeKey, BufferUtil.wrapString("foo")), "\"bar\"");
    MsgPackUtil.assertEquality(
        variablesState.getVariableLocal(scopeKey, BufferUtil.wrapString("baz")), "\"boz\"");
  }

  @Test
  public void shouldAcceptAndUpdateKey() {
    // given
    final DeployedWorkflow workflow = deployNewWorkflow();
    final TypedRecord<WorkflowInstanceCreationRecord> command =
        newCommand(WorkflowInstanceCreationRecord.class);
    command.getValue().setBpmnProcessId(workflow.getBpmnProcessId());

    // when
    processor.onCommand(command, controller, streamWriter);

    // then
    final WorkflowInstanceCreationRecord acceptedRecord =
        getAcceptedRecord(WorkflowInstanceCreationIntent.CREATED);
    assertThat(acceptedRecord.getWorkflowKey()).isEqualTo(workflow.getKey());
  }

  @Test
  public void shouldAcceptAndUpdateVersion() {
    // given
    final DeployedWorkflow workflow = deployNewWorkflow();
    final TypedRecord<WorkflowInstanceCreationRecord> command =
        newCommand(WorkflowInstanceCreationRecord.class);
    command.getValue().setBpmnProcessId(workflow.getBpmnProcessId());

    // when
    processor.onCommand(command, controller, streamWriter);

    // then
    final WorkflowInstanceCreationRecord acceptedRecord =
        getAcceptedRecord(WorkflowInstanceCreationIntent.CREATED);
    assertThat(acceptedRecord.getVersion()).isEqualTo(workflow.getVersion());
  }

  @Test
  public void shouldAcceptAndUpdateProcessId() {
    // given
    final DeployedWorkflow workflow = deployNewWorkflow();
    final TypedRecord<WorkflowInstanceCreationRecord> command =
        newCommand(WorkflowInstanceCreationRecord.class);
    command.getValue().setWorkflowKey(workflow.getKey());

    // when
    processor.onCommand(command, controller, streamWriter);

    // then
    final WorkflowInstanceCreationRecord acceptedRecord =
        getAcceptedRecord(WorkflowInstanceCreationIntent.CREATED);
    assertThat(acceptedRecord.getBpmnProcessIdBuffer()).isEqualTo(workflow.getBpmnProcessId());
  }

  private WorkflowInstanceRecord newExpectedElementActivatingRecord(
      final DeployedWorkflow workflow, final long instanceKey) {
    return new WorkflowInstanceRecord()
        .setElementId(workflow.getBpmnProcessId())
        .setBpmnElementType(BpmnElementType.PROCESS)
        .setWorkflowKey(workflow.getKey())
        .setFlowScopeKey(-1L)
        .setVersion(workflow.getVersion())
        .setWorkflowInstanceKey(instanceKey)
        .setBpmnProcessId(workflow.getBpmnProcessId());
  }

  private void verifyElementActivatingPublished(
      final long instanceKey, final ElementInstance instance) {
    verify(streamWriter, times(1))
        .appendFollowUpEvent(
            eq(instanceKey),
            eq(WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            eq(instance.getValue()));
  }

  private DeployedWorkflow deployNewWorkflow() {
    return deployNewWorkflow(VALID_WORKFLOW);
  }

  private DeployedWorkflow deployNewWorkflow(final BpmnModelInstance model) {
    final long key = keyGenerator.nextKey();
    final DeploymentRecord deployment = newDeployment(model);
    final Workflow workflow = deployment.workflows().iterator().next();
    workflowState.putDeployment(deployment);

    return workflowState.getLatestWorkflowVersionByProcessId(workflow.getBpmnProcessIdBuffer());
  }

  private DeploymentRecord newDeployment(final BpmnModelInstance model) {
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    final DeploymentRecord record = new DeploymentRecord();
    Bpmn.writeModelToStream(output, model);
    record.resources().add().setResource(output.toByteArray());

    final boolean transformed = transformer.transform(record);
    assertThat(transformed)
        .as("Failed to transform deployment: %s", transformer.getRejectionReason())
        .isTrue();

    return record;
  }
}
