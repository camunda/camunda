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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

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
import io.zeebe.broker.topic.Events;
import io.zeebe.broker.topic.StreamProcessorControl;
import io.zeebe.broker.topic.TestStreams;
import io.zeebe.broker.transport.clientapi.BufferingServerOutput;
import io.zeebe.broker.workflow.data.DeploymentEvent;
import io.zeebe.broker.workflow.data.DeploymentState;
import io.zeebe.broker.workflow.data.ResourceType;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.testing.ActorSchedulerRule;

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

    public TemporaryFolder tempFolder = new TemporaryFolder();
    public AutoCloseableRule closeables = new AutoCloseableRule();

    public ControlledActorClock clock = new ControlledActorClock();
    public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(clock);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(tempFolder).around(actorSchedulerRule).around(closeables);

    public BufferingServerOutput output;

    protected TestStreams streams;

    private TypedStreamProcessor streamProcessor;

    @Before
    public void setUp()
    {
        output = new BufferingServerOutput();

        streams = new TestStreams(tempFolder.getRoot(), closeables, actorSchedulerRule.get());
        streams.createLogStream(STREAM_NAME);

        streams.newEvent(STREAM_NAME) // TODO: workaround for https://github.com/zeebe-io/zeebe/issues/478
            .event(new UnpackedObject())
            .write();

        rebuildStreamProcessor();
    }

    protected void rebuildStreamProcessor()
    {
        final WorkflowVersions versions = new WorkflowVersions();
        final PendingWorkflows pendingWorkflows = new PendingWorkflows();
        final PendingDeployments pendingDeployments = new PendingDeployments();

        final TypedStreamEnvironment streamEnvironment = new TypedStreamEnvironment(streams.getLogStream(STREAM_NAME), output);
        final DeploymentEventWriter writer =
                new DeploymentEventWriter(
                        streamEnvironment.buildStreamWriter(),
                        streamEnvironment.buildStreamReader());

        final RemoteWorkflowsManager remoteManager = mock(RemoteWorkflowsManager.class);
        when(remoteManager.distributeWorkflow(any(), anyLong(), any())).thenReturn(true);
        when(remoteManager.deleteWorkflow(any(), anyLong(), any())).thenReturn(true);

        this.streamProcessor = DeploymentManager.createDeploymentStreamProcessor(
                versions,
                pendingDeployments,
                pendingWorkflows,
                DEPLOYMENT_TIMEOUT,
                streamEnvironment,
                writer,
                remoteManager
            );
    }

    @Test
    public void shouldTimeOutDeploymentAfterStreamProcessorRestart()
    {
        // given
        clock.pinCurrentTime();

        final StreamProcessorControl control = streams.runStreamProcessor(STREAM_NAME, streamProcessor);

        control.blockAfterEvent(e ->
            Events.isDeploymentEvent(e) &&
            Events.asDeploymentEvent(e).getState() == DeploymentState.VALIDATED);

        control.unblock();

        writeEventToStream(partitionCreated(STREAM_NAME, 1));
        writeEventToStream(topicCreated(STREAM_NAME, 1));
        writeEventToStream(createDeployment(ONE_TASK_PROCESS));

        waitUntil(() -> control.isBlocked());

        // restarting the stream processor
        control.close();

        rebuildStreamProcessor();
        final StreamProcessorControl restartedControl = streams.runStreamProcessor(STREAM_NAME, streamProcessor);

        restartedControl.blockAfterEvent(e -> false);
        restartedControl.unblock();

        // when
        clock.addTime(DEPLOYMENT_TIMEOUT.plus(Duration.ofSeconds(1)));

        // then
        waitUntil(() ->
            streams.events(STREAM_NAME)
                .filter(Events::isDeploymentEvent)
                .map(Events::asDeploymentEvent)
                .filter(e -> e.getState() == DeploymentState.TIMED_OUT)
                .count()
            > 0);
    }

    private long writeEventToStream(UnpackedObject object)
    {
        return streams.newEvent(STREAM_NAME)
            .event(object)
            .write();
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
