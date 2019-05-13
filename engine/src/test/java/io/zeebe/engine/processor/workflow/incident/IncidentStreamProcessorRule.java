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
package io.zeebe.engine.processor.workflow.incident;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.workflow.BpmnStepProcessor;
import io.zeebe.engine.processor.workflow.CatchEventBehavior;
import io.zeebe.engine.processor.workflow.WorkflowEventProcessors;
import io.zeebe.engine.processor.workflow.job.JobEventProcessors;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.processor.workflow.timer.DueDateTimerChecker;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.Process;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.ResourceType;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.io.ByteArrayOutputStream;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

public class IncidentStreamProcessorRule extends ExternalResource {

  @Rule public TemporaryFolder folder = new TemporaryFolder();
  private final StreamProcessorRule environmentRule;

  private SubscriptionCommandSender mockSubscriptionCommandSender;
  private DueDateTimerChecker mockTimerEventScheduler;

  private WorkflowState workflowState;
  private ZeebeState zeebeState;

  public IncidentStreamProcessorRule(StreamProcessorRule streamProcessorRule) {
    this.environmentRule = streamProcessorRule;
  }

  @Override
  protected void before() {
    mockSubscriptionCommandSender = mock(SubscriptionCommandSender.class);
    mockTimerEventScheduler = mock(DueDateTimerChecker.class);

    when(mockSubscriptionCommandSender.openMessageSubscription(
            anyInt(), anyLong(), anyLong(), any(), any(), anyBoolean()))
        .thenReturn(true);
    when(mockSubscriptionCommandSender.correlateMessageSubscription(
            anyInt(), anyLong(), anyLong(), any()))
        .thenReturn(true);
    when(mockSubscriptionCommandSender.closeMessageSubscription(
            anyInt(), anyLong(), anyLong(), any(DirectBuffer.class)))
        .thenReturn(true);

    environmentRule.runTypedStreamProcessor(
        (typedEventStreamProcessorBuilder, zeebeDb, dbContext) -> {
          this.zeebeState = new ZeebeState(zeebeDb, dbContext);
          this.workflowState = zeebeState.getWorkflowState();
          final BpmnStepProcessor stepProcessor =
              WorkflowEventProcessors.addWorkflowProcessors(
                  typedEventStreamProcessorBuilder,
                  zeebeState,
                  mockSubscriptionCommandSender,
                  new CatchEventBehavior(zeebeState, mockSubscriptionCommandSender, 1),
                  mockTimerEventScheduler);

          IncidentEventProcessors.addProcessors(
              typedEventStreamProcessorBuilder, zeebeState, stepProcessor);
          JobEventProcessors.addJobProcessors(typedEventStreamProcessorBuilder, zeebeState);

          return typedEventStreamProcessorBuilder.build();
        });
  }

  public ZeebeState getZeebeState() {
    return zeebeState;
  }

  public void deploy(final BpmnModelInstance modelInstance) {
    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(outStream, modelInstance);
    final DirectBuffer xmlBuffer = new UnsafeBuffer(outStream.toByteArray());

    final DeploymentRecord record = new DeploymentRecord();
    final DirectBuffer resourceName = wrapString("resourceName");

    final Process process = modelInstance.getModelElementsByType(Process.class).iterator().next();

    record
        .resources()
        .add()
        .setResource(xmlBuffer)
        .setResourceName(resourceName)
        .setResourceType(ResourceType.BPMN_XML);

    record
        .workflows()
        .add()
        .setKey(1)
        .setResourceName(resourceName)
        .setBpmnProcessId(BufferUtil.wrapString(process.getId()))
        .setVersion(1);

    workflowState.putDeployment(1, record);
  }

  public TypedRecord<WorkflowInstanceRecord> createWorkflowInstance(final String processId) {
    return createWorkflowInstance(processId, wrapString(""));
  }

  public TypedRecord<WorkflowInstanceRecord> createWorkflowInstance(
      final String processId, final DirectBuffer variables) {
    environmentRule.writeCommand(
        WorkflowInstanceCreationIntent.CREATE,
        workflowInstanceCreationRecord(BufferUtil.wrapString(processId), variables));
    final TypedRecord<WorkflowInstanceRecord> createdEvent =
        awaitAndGetFirstRecordInState(WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    return createdEvent;
  }

  private static WorkflowInstanceCreationRecord workflowInstanceCreationRecord(
      final DirectBuffer processId, final DirectBuffer variables) {
    final WorkflowInstanceCreationRecord record = new WorkflowInstanceCreationRecord();

    record.setKey(1);
    record.setBpmnProcessId(processId);
    record.setVariables(variables);

    return record;
  }

  private void awaitFirstRecordInState(final Intent state) {
    waitUntil(() -> environmentRule.events().withIntent(state).findFirst().isPresent());
  }

  private TypedRecord<WorkflowInstanceRecord> awaitAndGetFirstRecordInState(
      final WorkflowInstanceIntent state) {
    awaitFirstRecordInState(state);
    return environmentRule
        .events()
        .onlyWorkflowInstanceRecords()
        .withIntent(state)
        .findFirst()
        .get();
  }

  public void awaitIncidentInState(Intent state) {
    waitUntil(
        () ->
            environmentRule
                .events()
                .onlyIncidentRecords()
                .onlyEvents()
                .withIntent(state)
                .findFirst()
                .isPresent());
  }

  public void awaitIncidentRejection(Intent state) {
    waitUntil(
        () ->
            environmentRule
                .events()
                .onlyIncidentRecords()
                .onlyRejections()
                .withIntent(state)
                .findFirst()
                .isPresent());
  }
}
