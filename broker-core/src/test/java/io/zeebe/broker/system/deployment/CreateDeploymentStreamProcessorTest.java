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
package io.zeebe.broker.system.deployment;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.Rule;
import org.junit.Test;

import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.system.deployment.data.PendingDeployments;
import io.zeebe.broker.system.deployment.data.PendingWorkflows;
import io.zeebe.broker.system.deployment.data.WorkflowVersions;
import io.zeebe.broker.system.deployment.handler.DeploymentEventWriter;
import io.zeebe.broker.system.deployment.handler.RemoteWorkflowsManager;
import io.zeebe.broker.system.deployment.service.DeploymentManager;
import io.zeebe.broker.system.log.PartitionEvent;
import io.zeebe.broker.system.log.PartitionState;
import io.zeebe.broker.system.log.TopicEvent;
import io.zeebe.broker.system.log.TopicState;
import io.zeebe.broker.topic.StreamProcessorControl;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.broker.workflow.data.DeploymentEvent;
import io.zeebe.broker.workflow.data.DeploymentState;
import io.zeebe.broker.workflow.data.ResourceType;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.util.buffer.BufferUtil;

public class CreateDeploymentStreamProcessorTest
{

    private static final String STREAM_NAME = "stream";
    protected static final WorkflowDefinition ONE_TASK_PROCESS =
        Bpmn.createExecutableWorkflow("foo")
            .startEvent()
            .serviceTask()
            .taskType("bar")
            .done()
            .endEvent()
            .done();
    private static final Duration DEPLOYMENT_TIMEOUT = Duration.ofSeconds(50);

    @Rule
    public StreamProcessorRule rule = new StreamProcessorRule();

    protected TypedStreamProcessor buildStreamProcessor(TypedStreamEnvironment env)
    {
        final WorkflowVersions versions = new WorkflowVersions();
        final PendingWorkflows pendingWorkflows = new PendingWorkflows();
        final PendingDeployments pendingDeployments = new PendingDeployments();

        final DeploymentEventWriter writer =
                new DeploymentEventWriter(
                        env.buildStreamWriter(),
                        env.buildStreamReader());

        final RemoteWorkflowsManager remoteManager = mock(RemoteWorkflowsManager.class);
        when(remoteManager.distributeWorkflow(any(), anyLong(), any())).thenReturn(true);
        when(remoteManager.deleteWorkflow(any(), anyLong(), any())).thenReturn(true);

        return DeploymentManager.createDeploymentStreamProcessor(
                versions,
                pendingDeployments,
                pendingWorkflows,
                DEPLOYMENT_TIMEOUT,
                env,
                writer,
                remoteManager
            );
    }

    @Test
    public void shouldTimeOutDeploymentAfterStreamProcessorRestart()
    {
        // given
        rule.getClock().pinCurrentTime();

        final StreamProcessorControl control = rule.runStreamProcessor(this::buildStreamProcessor);

        control.blockAfterDeploymentEvent(e -> e.getValue().getState() == DeploymentState.VALIDATED);

        rule.writeEvent(partitionCreated(STREAM_NAME, 1));
        rule.writeEvent(topicCreated(STREAM_NAME, 1));
        rule.writeEvent(createDeployment(ONE_TASK_PROCESS));

        waitUntil(() -> control.isBlocked());

        control.restart();

        // when
        rule.getClock().addTime(DEPLOYMENT_TIMEOUT.plus(Duration.ofSeconds(1)));

        // then
        waitUntil(() ->
            rule.events()
                .onlyDeploymentEvents()
                .inState(DeploymentState.TIMED_OUT)
                .count()
            > 0);
    }

    protected DeploymentEvent createDeployment(WorkflowDefinition workflow)
    {
        final DeploymentEvent event = new DeploymentEvent();

        event.setState(DeploymentState.CREATE);
        event.setTopicName(STREAM_NAME);
        event.resources().add()
            .setResourceName(BufferUtil.wrapString("foo.bpmn"))
            .setResourceType(ResourceType.BPMN_XML)
            .setResource(BufferUtil.wrapString(Bpmn.convertToString(workflow)));

        return event;
    }

    protected TopicEvent topicCreated(String name, int partitions)
    {
        final TopicEvent event = new TopicEvent();
        event.setName(BufferUtil.wrapString(name));
        event.setPartitions(partitions);
        event.setState(TopicState.CREATED);

        return event;
    }

    protected PartitionEvent partitionCreated(String topicName, int partitionId)
    {
        final PartitionEvent event = new PartitionEvent();
        event.setState(PartitionState.CREATED);
        event.setTopicName(BufferUtil.wrapString(topicName));
        event.setId(partitionId);
        event.setCreator(BufferUtil.wrapString("host"), 1234);

        return event;
    }
}
