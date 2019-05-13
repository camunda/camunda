/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor.workflow.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.zeebe.engine.processor.CommandProcessorTestCase;
import io.zeebe.engine.processor.KeyGenerator;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.workflow.deployment.transform.DeploymentTransformer;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.DeployedWorkflow;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.engine.state.instance.VariablesState;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.Workflow;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.collection.Maps;
import io.zeebe.util.buffer.BufferUtil;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.junit.MockitoJUnitRunner;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class CreateWorkflowInstanceProcessorTest
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
    transformer =
        new DeploymentTransformer(CommandProcessorTestCase.zeebeStateRule.getZeebeState());
  }

  @Before
  public void setUp() {
    state = CommandProcessorTestCase.zeebeStateRule.getZeebeState();
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
    command.getValue().setKey(keyGenerator.nextKey());

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
            .message(m -> m.name("message").zeebeCorrelationKey("key"))
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
    final long instanceKey = acceptedRecord.getInstanceKey();
    final ElementInstance instance = elementInstanceState.getInstance(instanceKey);
    final WorkflowInstanceRecord expectedElementActivatingRecord =
        newExpectedElementActivatingRecord(workflow, instanceKey);
    Assertions.assertThat(instance.getState()).isEqualTo(WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    Assertions.assertThat(instance.getValue()).isEqualTo(expectedElementActivatingRecord);
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
    final long scopeKey = acceptedRecord.getInstanceKey();
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
    assertThat(acceptedRecord.getKey()).isEqualTo(workflow.getKey());
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
    command.getValue().setKey(workflow.getKey());

    // when
    processor.onCommand(command, controller, streamWriter);

    // then
    final WorkflowInstanceCreationRecord acceptedRecord =
        getAcceptedRecord(WorkflowInstanceCreationIntent.CREATED);
    assertThat(acceptedRecord.getBpmnProcessId()).isEqualTo(workflow.getBpmnProcessId());
  }

  private WorkflowInstanceRecord newExpectedElementActivatingRecord(
      DeployedWorkflow workflow, long instanceKey) {
    return new WorkflowInstanceRecord()
        .setElementId(workflow.getBpmnProcessId())
        .setBpmnElementType(BpmnElementType.PROCESS)
        .setWorkflowKey(workflow.getKey())
        .setFlowScopeKey(-1L)
        .setVersion(workflow.getVersion())
        .setWorkflowInstanceKey(instanceKey)
        .setBpmnProcessId(workflow.getBpmnProcessId());
  }

  private void verifyElementActivatingPublished(long instanceKey, ElementInstance instance) {
    verify(streamWriter, times(1))
        .appendFollowUpEvent(
            eq(instanceKey),
            eq(WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            ArgumentMatchers.eq(instance.getValue()));
  }

  private DeployedWorkflow deployNewWorkflow() {
    return deployNewWorkflow(VALID_WORKFLOW);
  }

  private DeployedWorkflow deployNewWorkflow(BpmnModelInstance model) {
    final long key = keyGenerator.nextKey();
    final DeploymentRecord deployment = newDeployment(model);
    final Workflow workflow = deployment.workflows().iterator().next();
    workflowState.putDeployment(key, deployment);

    return workflowState.getLatestWorkflowVersionByProcessId(workflow.getBpmnProcessId());
  }

  private DeploymentRecord newDeployment(BpmnModelInstance model) {
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
