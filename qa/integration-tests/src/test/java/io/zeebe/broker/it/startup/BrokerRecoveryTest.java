/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.it.startup;

import static io.zeebe.broker.it.util.TopicEventRecorder.incidentEvent;
import static io.zeebe.broker.it.util.TopicEventRecorder.taskEvent;
import static io.zeebe.broker.it.util.TopicEventRecorder.wfInstanceEvent;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.RecordingTaskHandler;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.impl.clustering.*;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftServiceNames;
import io.zeebe.raft.state.RaftState;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.FileUtil;

public class BrokerRecoveryTest
{
    private static final WorkflowDefinition WORKFLOW = Bpmn.createExecutableWorkflow("process")
            .startEvent("start")
            .serviceTask("task", t -> t.taskType("foo"))
            .endEvent("end")
            .done();

    private static final WorkflowDefinition WORKFLOW_TWO_TASKS = Bpmn.createExecutableWorkflow("process")
            .startEvent("start")
            .serviceTask("task1", t -> t.taskType("foo"))
            .serviceTask("task2", t -> t.taskType("bar"))
            .endEvent("end")
            .done();

    private static final WorkflowDefinition WORKFLOW_INCIDENT = Bpmn.createExecutableWorkflow("process")
            .startEvent("start")
            .serviceTask("task", t -> t.taskType("test")
                         .input("$.foo", "$.foo"))
            .endEvent("end")
            .done();

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule);

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule)
        .around(eventRecorder);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldCreateWorkflowInstanceAfterRestart()
    {
        // given
        clientRule.workflows().deploy(clientRule.getDefaultTopic())
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .execute();

        // when
        deleteSnapshotsAndRestart();

        clientRule.workflows().create(clientRule.getDefaultTopic())
            .bpmnProcessId("process")
            .execute();

        // then
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("WORKFLOW_INSTANCE_CREATED")));
    }

    @Test
    public void shouldContinueWorkflowInstanceAtTaskAfterRestart()
    {
        // given
        clientRule.workflows().deploy(clientRule.getDefaultTopic())
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .execute();

        clientRule.workflows().create(clientRule.getDefaultTopic())
            .bpmnProcessId("process")
            .execute();

        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("CREATED")));

        // when
        deleteSnapshotsAndRestart();

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .taskType("foo")
            .lockOwner("test")
            .lockTime(Duration.ofSeconds(5))
            .handler((c, t) -> c.complete(t).withoutPayload().execute())
            .open();

        // then
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("COMPLETED")));
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("WORKFLOW_INSTANCE_COMPLETED")));
    }

    @Test
    public void shouldContinueWorkflowInstanceWithLockedTaskAfterRestart()
    {
        // given
        clientRule.workflows().deploy(clientRule.getDefaultTopic())
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .execute();

        clientRule.workflows().create(clientRule.getDefaultTopic())
            .bpmnProcessId("process")
            .execute();

        final RecordingTaskHandler recordingTaskHandler = new RecordingTaskHandler();
        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .taskType("foo")
            .lockOwner("test")
            .lockTime(Duration.ofSeconds(5))
            .handler(recordingTaskHandler)
            .open();

        waitUntil(() -> !recordingTaskHandler.getHandledTasks().isEmpty());

        // when
        deleteSnapshotsAndRestart();

        final TaskEvent task = recordingTaskHandler.getHandledTasks().get(0);
        clientRule.tasks().complete(task).execute();

        // then
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("COMPLETED")));
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("WORKFLOW_INSTANCE_COMPLETED")));
    }

    @Test
    public void shouldContinueWorkflowInstanceAtSecondTaskAfterRestart()
    {
        // given
        clientRule.workflows().deploy(clientRule.getDefaultTopic())
            .addWorkflowModel(WORKFLOW_TWO_TASKS, "two-tasks.bpmn")
            .execute();

        clientRule.workflows().create(clientRule.getDefaultTopic())
            .bpmnProcessId("process")
            .execute();

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .taskType("foo")
            .lockOwner("test")
            .lockTime(Duration.ofSeconds(5))
            .handler((c, t) -> c.complete(t).withoutPayload().execute())
            .open();

        waitUntil(() -> eventRecorder.getTaskEvents(taskEvent("CREATED")).size() > 1);

        // when
        deleteSnapshotsAndRestart();

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .taskType("bar")
            .lockOwner("test")
            .lockTime(Duration.ofSeconds(5))
            .handler((c, t) -> c.complete(t).withoutPayload().execute())
            .open();

        // then
        waitUntil(() -> eventRecorder.getTaskEvents(taskEvent("COMPLETED")).size() > 1);
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("WORKFLOW_INSTANCE_COMPLETED")));
    }

    @Test
    public void shouldDeployNewWorkflowVersionAfterRestart()
    {
        // given
        clientRule.workflows().deploy(clientRule.getDefaultTopic())
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .execute();

        // when
        deleteSnapshotsAndRestart();

        final DeploymentEvent deploymentResult = clientRule.workflows().deploy(clientRule.getDefaultTopic())
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .execute();

        // then
        assertThat(deploymentResult.getDeployedWorkflows().get(0).getVersion()).isEqualTo(2);

        final WorkflowInstanceEvent workflowInstanceV1 = clientRule.workflows()
            .create(clientRule.getDefaultTopic())
            .bpmnProcessId("process")
            .version(1)
            .execute();

        final WorkflowInstanceEvent workflowInstanceV2 = clientRule.workflows()
            .create(clientRule.getDefaultTopic())
            .bpmnProcessId("process")
            .latestVersionForceRefresh()
            .execute();

        // then
        assertThat(workflowInstanceV1.getVersion()).isEqualTo(1);
        assertThat(workflowInstanceV2.getVersion()).isEqualTo(2);
    }

    @Test
    public void shouldLockAndCompleteStandaloneTaskAfterRestart()
    {
        // given
        clientRule.tasks().create(clientRule.getDefaultTopic(), "foo").execute();

        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("CREATED")));

        // when
        deleteSnapshotsAndRestart();

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .taskType("foo")
            .lockOwner("test")
            .lockTime(Duration.ofSeconds(5))
            .handler((c, t) -> c.complete(t).withoutPayload().execute())
            .open();

        // then
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("COMPLETED")));
    }

    @Test
    public void shouldCompleteStandaloneTaskAfterRestart()
    {
        // given
        clientRule.tasks().create(clientRule.getDefaultTopic(), "foo").execute();

        final RecordingTaskHandler recordingTaskHandler = new RecordingTaskHandler();
        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .taskType("foo")
            .lockOwner("test")
            .lockTime(Duration.ofSeconds(5))
            .handler(recordingTaskHandler)
            .open();

        waitUntil(() -> !recordingTaskHandler.getHandledTasks().isEmpty());

        // when
        deleteSnapshotsAndRestart();

        final TaskEvent task = recordingTaskHandler.getHandledTasks().get(0);
        clientRule.tasks().complete(task).execute();

        // then
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("COMPLETED")));
    }

    @Test
    public void shouldNotReceiveLockedTasksAfterRestart()
    {
        // given
        clientRule.tasks().create(clientRule.getDefaultTopic(), "foo").execute();

        final RecordingTaskHandler taskHandler = new RecordingTaskHandler();
        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .taskType("foo")
            .lockTime(Duration.ofSeconds(5))
            .lockOwner("test")
            .handler(taskHandler)
            .open();

        waitUntil(() -> !taskHandler.getHandledTasks().isEmpty());

        // when
        deleteSnapshotsAndRestart();

        taskHandler.clear();

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .taskType("foo")
            .lockTime(Duration.ofMinutes(10L))
            .lockOwner("test")
            .handler(taskHandler)
            .open();

        // then
        TestUtil.doRepeatedly(() -> null)
            .whileConditionHolds((o) -> taskHandler.getHandledTasks().isEmpty());

        assertThat(taskHandler.getHandledTasks()).isEmpty();
    }

    @Test
    public void shouldReceiveLockExpiredTasksAfterRestart()
    {
        // given
        clientRule.tasks().create(clientRule.getDefaultTopic(), "foo").execute();

        final RecordingTaskHandler recordingTaskHandler = new RecordingTaskHandler();
        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .taskType("foo")
            .lockTime(Duration.ofSeconds(5))
            .lockOwner("test")
            .handler(recordingTaskHandler)
            .open();

        waitUntil(() -> !recordingTaskHandler.getHandledTasks().isEmpty());

        // when
        deleteSnapshotsAndRestart();

        // wait until stream processor and scheduler process the lock task event which is not re-processed on recovery
        doRepeatedly(() ->
        {
            brokerRule.getClock().addTime(Duration.ofSeconds(60)); // retriggers lock expiration check in broker
            return null;
        }).until(t -> eventRecorder.hasTaskEvent(taskEvent("LOCK_EXPIRED")));

        recordingTaskHandler.clear();

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .taskType("foo")
            .lockTime(Duration.ofMinutes(10L))
            .lockOwner("test")
            .handler(recordingTaskHandler)
            .open();

        // then
        waitUntil(() -> !recordingTaskHandler.getHandledTasks().isEmpty());

        final TaskEvent task = recordingTaskHandler.getHandledTasks().get(0);
        clientRule.tasks().complete(task).execute();

        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("COMPLETED")));
    }

    @Test
    public void shouldResolveIncidentAfterRestart()
    {
        // given
        clientRule.workflows().deploy(clientRule.getDefaultTopic())
            .addWorkflowModel(WORKFLOW_INCIDENT, "incident.bpmn")
            .execute();

        clientRule.workflows().create(clientRule.getDefaultTopic())
            .bpmnProcessId("process")
            .execute();

        waitUntil(() -> eventRecorder.hasIncidentEvent(incidentEvent("CREATED")));

        final WorkflowInstanceEvent activityInstance =
                eventRecorder.getSingleWorkflowInstanceEvent(TopicEventRecorder.wfInstanceEvent("ACTIVITY_READY"));

        // when
        deleteSnapshotsAndRestart();

        clientRule.workflows().updatePayload(activityInstance)
            .payload("{\"foo\":\"bar\"}")
            .execute();

        // then
        waitUntil(() -> eventRecorder.hasIncidentEvent(incidentEvent("RESOLVED")));
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("CREATED")));

        assertThat(eventRecorder.getIncidentEvents(i -> true))
            .extracting("state")
            .containsExactly("CREATE", "CREATED", "RESOLVE", "RESOLVED");
    }

    @Test
    public void shouldResolveFailedIncidentAfterRestart()
    {
        // given
        clientRule.workflows().deploy(clientRule.getDefaultTopic())
            .addWorkflowModel(WORKFLOW_INCIDENT, "incident.bpmn")
            .execute();

        clientRule.workflows().create(clientRule.getDefaultTopic())
            .bpmnProcessId("process")
            .execute();

        waitUntil(() -> eventRecorder.hasIncidentEvent(incidentEvent("CREATED")));

        final WorkflowInstanceEvent activityInstance =
                eventRecorder.getSingleWorkflowInstanceEvent(TopicEventRecorder.wfInstanceEvent("ACTIVITY_READY"));

        clientRule.workflows().updatePayload(activityInstance)
            .payload("{\"x\":\"y\"}")
            .execute();

        waitUntil(() -> eventRecorder.hasIncidentEvent(incidentEvent("RESOLVE_FAILED")));

        // when
        deleteSnapshotsAndRestart();

        clientRule.workflows().updatePayload(activityInstance)
            .payload("{\"foo\":\"bar\"}")
            .execute();

        // then
        waitUntil(() -> eventRecorder.hasIncidentEvent(incidentEvent("RESOLVED")));
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("CREATED")));

        assertThat(eventRecorder.getIncidentEvents(i -> true))
            .extracting("state")
            .containsExactly("CREATE", "CREATED", "RESOLVE", "RESOLVE_FAILED", "RESOLVE", "RESOLVED");
    }

    @Test
    public void shouldLoadRaftConfiguration()
    {
        // given
        final int testTerm = 8;

        final ServiceName<Raft> serviceName = RaftServiceNames.raftServiceName(clientRule.getDefaultTopic() + "-" + clientRule.getDefaultPartition());

        final Raft raft = brokerRule.getService(serviceName);
        waitUntil(() -> raft.getState() == RaftState.LEADER);

        raft.setTerm(testTerm);

        // when
        deleteSnapshotsAndRestart();

        final Raft raftAfterRestart = brokerRule.getService(serviceName);
        waitUntil(() -> raftAfterRestart.getState() == RaftState.LEADER);

        // then
        assertThat(raftAfterRestart.getState()).isEqualTo(RaftState.LEADER);
        assertThat(raftAfterRestart.getTerm()).isGreaterThanOrEqualTo(9);
        assertThat(raftAfterRestart.getMemberSize()).isEqualTo(0);
        assertThat(raftAfterRestart.getVotedFor()).isEqualTo(new SocketAddress("0.0.0.0", 51017));
    }

    @Test
    public void shouldCreateTopicAfterRestart()
    {
        // given
        final ZeebeClient client = clientRule.getClient();
        deleteSnapshotsAndRestart();

        // when
        client.topics().create("foo", 2).execute();

        // then
        final TaskEvent taskEvent = client.tasks().create("foo", "bar").execute();
        assertThat(taskEvent.getState()).isEqualTo("CREATED");
    }


    @Test
    public void shouldNotCreatePreviouslyCreatedTopicAfterRestart()
    {
        // given
        final ZeebeClient client = clientRule.getClient();

        final String topicName = "foo";
        client.topics().create(topicName, 2).execute();

        deleteSnapshotsAndRestart();

        // then
        exception.expect(ClientCommandRejectedException.class);

        // when
        client.topics().create(topicName, 2).execute();
    }

    @Test
    public void shouldCreateUniquePartitionIdsAfterRestart()
    {
        // given
        final ZeebeClient client = clientRule.getClient();

        client.topics().create("foo", 2).execute();
        clientRule.waitUntilTopicsExists("foo");

        deleteSnapshotsAndRestart();

        // when
        client.topics().create("bar", 2).execute();
        clientRule.waitUntilTopicsExists("bar");

        // then
        final TopologyImpl topology = client.requestTopology().execute();
        final List<BrokerInfoImpl> brokers = topology.getBrokers();
        assertThat(brokers).hasSize(1);

        final BrokerInfoImpl topologyBroker = brokers.get(0);
        final List<PartitionInfoImpl> partitions = topologyBroker.getPartitions();

        assertThat(partitions).hasSize(6); // default partition + system partition + 4 partitions we create here
        assertThat(partitions).extracting("partitionId").doesNotHaveDuplicates();
    }

    protected void deleteSnapshotsAndRestart()
    {
        deleteSnapshotsAndRestart(() ->
        { });
    }

    protected void deleteSnapshotsAndRestart(Runnable onStop)
    {
        eventRecorder.stopRecordingEvents();
        brokerRule.stopBroker();

        // delete snapshot files to trigger recovery

        try
        {
            java.nio.file.Files.walkFileTree(brokerRule.getBrokerBase().toPath(), new SimpleFileVisitor<Path>()
            {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
                {
                    if (dir.endsWith("snapshots"))
                    {
                        FileUtil.deleteFolder(dir.normalize().toAbsolutePath().toString());
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    else
                    {
                        return FileVisitResult.CONTINUE;
                    }

                }
            });
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail(e.getMessage());
        }

        onStop.run();

        brokerRule.startBroker();
        doRepeatedly(() ->
        {
            eventRecorder.startRecordingEvents();
            return true;
        }).until(t -> t == Boolean.TRUE, e -> e == null);
        // this can fail immediately after restart due to https://github.com/zeebe-io/zeebe/issues/590
    }

}
