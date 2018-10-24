/*
 * Zeebe Broker Core
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
package io.zeebe.broker.workflow.processor;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.topic.StreamProcessorControl;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.broker.workflow.processor.timer.DueDateTimerChecker;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.ResourceType;
import io.zeebe.protocol.intent.DeploymentIntent;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TransformingDeploymentCreateProcessorTest {

  @Rule public StreamProcessorRule rule = new StreamProcessorRule(Protocol.DEPLOYMENT_PARTITION);

  @Mock TopologyManager topologyManager;
  @Mock private SubscriptionCommandSender mockSubscriptionCommandSender;
  @Mock private DueDateTimerChecker mockTimerEventScheduler;

  private StreamProcessorControl streamProcessor;
  private WorkflowInstanceStreamProcessor workflowInstanceStreamProcessor;
  private WorkflowState workflowState;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    workflowState = new WorkflowState();
    workflowInstanceStreamProcessor =
        new WorkflowInstanceStreamProcessor(
            workflowState, mockSubscriptionCommandSender, topologyManager, mockTimerEventScheduler);

    streamProcessor =
        rule.initStreamProcessor(env -> workflowInstanceStreamProcessor.createStreamProcessor(env));
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
    assertThat(collect)
        .extracting(r -> r.getMetadata().getIntent())
        .containsExactly(DeploymentIntent.CREATE, DeploymentIntent.CREATED);
    assertThat(collect)
        .extracting(r -> r.getMetadata().getRecordType())
        .containsExactly(RecordType.COMMAND, RecordType.EVENT);

    assertThat(workflowState.getWorkflows().size()).isEqualTo(1);
    assertThat(workflowState.getWorkflowsByBpmnProcessId(wrapString("processId"))).isNotNull();
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
