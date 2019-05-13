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
package io.zeebe.engine.processor.workflow.deployment;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.workflow.CatchEventBehavior;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.util.StreamProcessorControl;
import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.ResourceType;
import io.zeebe.protocol.intent.DeploymentIntent;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

public class TransformingDeploymentCreateProcessorTest {

  @Rule public StreamProcessorRule rule = new StreamProcessorRule(Protocol.DEPLOYMENT_PARTITION);

  private StreamProcessorControl streamProcessor;
  private WorkflowState workflowState;
  private SubscriptionCommandSender mockSubscriptionCommandSender;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    mockSubscriptionCommandSender = mock(SubscriptionCommandSender.class);

    when(mockSubscriptionCommandSender.openMessageSubscription(
            anyInt(), anyLong(), anyLong(), any(), any(), anyBoolean()))
        .thenReturn(true);
    when(mockSubscriptionCommandSender.correlateMessageSubscription(
            anyInt(), anyLong(), anyLong(), any()))
        .thenReturn(true);
    when(mockSubscriptionCommandSender.closeMessageSubscription(
            anyInt(), anyLong(), anyLong(), any(DirectBuffer.class)))
        .thenReturn(true);

    streamProcessor =
        rule.initTypedStreamProcessor(
            (typedEventStreamProcessorBuilder, zeebeDb, dbContext) -> {
              final ZeebeState zeebeState = new ZeebeState(zeebeDb, dbContext);
              workflowState = zeebeState.getWorkflowState();

              DeploymentEventProcessors.addTransformingDeploymentProcessor(
                  typedEventStreamProcessorBuilder,
                  zeebeState,
                  new CatchEventBehavior(zeebeState, mockSubscriptionCommandSender, 1));

              return typedEventStreamProcessorBuilder.build();
            });
  }

  @Test
  public void shouldCreateDeploymentAndAddToWorkflowCache() {
    // given
    streamProcessor.start();

    // when
    creatingDeployment();

    // then
    waitUntil(() -> rule.events().onlyDeploymentRecords().count() >= 2);

    final List<TypedRecord<DeploymentRecord>> collect =
        rule.events().onlyDeploymentRecords().collect(Collectors.toList());
    //
    Assertions.assertThat(collect)
        .extracting(r -> r.getMetadata().getIntent())
        .containsExactly(DeploymentIntent.CREATE, DeploymentIntent.CREATED);
    Assertions.assertThat(collect)
        .extracting(r -> r.getMetadata().getRecordType())
        .containsExactly(RecordType.COMMAND, RecordType.EVENT);

    Assertions.assertThat(workflowState.getWorkflows().size()).isEqualTo(1);
    Assertions.assertThat(workflowState.getWorkflowsByBpmnProcessId(wrapString("processId")))
        .isNotNull();
  }

  private void creatingDeployment() {
    creatingDeployment(4);
  }

  private void creatingDeployment(final long key) {
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("processId")
            .startEvent()
            .serviceTask(
                "test",
                task -> {
                  task.zeebeTaskType("type");
                })
            .endEvent()
            .done();

    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    deploymentRecord
        .resources()
        .add()
        .setResourceName(wrapString("process.bpmn"))
        .setResource(wrapString(Bpmn.convertToString(modelInstance)))
        .setResourceType(ResourceType.BPMN_XML);

    rule.writeCommand(key, DeploymentIntent.CREATE, deploymentRecord);
  }
}
