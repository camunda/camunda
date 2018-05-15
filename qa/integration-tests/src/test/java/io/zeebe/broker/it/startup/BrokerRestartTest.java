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

import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.RecordingJobHandler;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.*;
import io.zeebe.client.api.events.*;
import io.zeebe.client.api.events.IncidentEvent.IncidentState;
import io.zeebe.client.api.events.JobEvent.JobState;
import io.zeebe.client.api.events.WorkflowInstanceEvent.WorkflowInstanceState;
import io.zeebe.client.api.subscription.JobSubscription;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftServiceNames;
import io.zeebe.raft.state.RaftState;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.SocketAddress;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class BrokerRestartTest
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
        clientRule.getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();

        // when
        restartBroker();

        clientRule.getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

        // then
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.CREATED));
    }

    @Test
    public void shouldContinueWorkflowInstanceAtTaskAfterRestart()
    {
        // given
        clientRule.getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();

        clientRule.getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

        waitUntil(() -> eventRecorder.hasJobEvent(JobState.CREATED));

        // when
        restartBroker();

        clientRule.getSubscriptionClient()
            .newJobSubscription()
            .jobType("foo")
            .handler((client, job) -> client.newCompleteCommand(job).withoutPayload().send())
            .open();

        // then
        waitUntil(() -> eventRecorder.hasJobEvent(JobState.COMPLETED));
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.COMPLETED));
    }

    @Test
    public void shouldContinueWorkflowInstanceWithLockedTaskAfterRestart()
    {
        // given
        clientRule.getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();

        clientRule.getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

        final RecordingJobHandler recordingJobHandler = new RecordingJobHandler();
        clientRule.getSubscriptionClient()
            .newJobSubscription()
            .jobType("foo")
            .handler(recordingJobHandler)
            .open();

        waitUntil(() -> !recordingJobHandler.getHandledJobs().isEmpty());

        // when
        restartBroker();

        final JobEvent jobEvent = recordingJobHandler.getHandledJobs().get(0);

        clientRule.getJobClient()
            .newCompleteCommand(jobEvent)
            .send()
            .join();

        // then
        waitUntil(() -> eventRecorder.hasJobEvent(JobState.COMPLETED));
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.COMPLETED));
    }

    @Test
    public void shouldContinueWorkflowInstanceAtSecondTaskAfterRestart()
    {
        // given
        clientRule.getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW_TWO_TASKS, "two-tasks.bpmn")
            .send()
            .join();

        clientRule.getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

        clientRule.getSubscriptionClient()
            .newJobSubscription()
            .jobType("foo")
            .handler((client, job) -> client.newCompleteCommand(job).withoutPayload().send())
            .open();

        waitUntil(() -> eventRecorder.getJobEvents(JobState.CREATED).size() > 1);

        // when
        restartBroker();

        clientRule.getSubscriptionClient()
            .newJobSubscription()
            .jobType("bar")
            .handler((client, job) -> client.newCompleteCommand(job).withoutPayload().send())
            .open();

        // then
        waitUntil(() -> eventRecorder.getJobEvents(JobState.COMPLETED).size() > 1);
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.COMPLETED));
    }

    @Test
    // FIXME: https://github.com/zeebe-io/zeebe/issues/567
    @Category(io.zeebe.UnstableTest.class)
    public void shouldDeployNewWorkflowVersionAfterRestart()
    {
        // given
        clientRule.getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();

        // when
        restartBroker();

        final DeploymentEvent deploymentResult = clientRule.getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();

        // then
        assertThat(deploymentResult.getDeployedWorkflows().get(0).getVersion()).isEqualTo(2);

        final WorkflowInstanceEvent workflowInstanceV1 = clientRule.getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .version(1)
            .send()
            .join();

        final WorkflowInstanceEvent workflowInstanceV2 = clientRule.getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersionForce()
            .send()
            .join();

        // then
        assertThat(workflowInstanceV1.getVersion()).isEqualTo(1);
        assertThat(workflowInstanceV2.getVersion()).isEqualTo(2);
    }

    @Test
    public void shouldLockAndCompleteStandaloneJobAfterRestart()
    {
        // given
        clientRule.getJobClient()
            .newCreateCommand()
            .jobType("foo")
            .send()
            .join();

        waitUntil(() -> eventRecorder.hasJobEvent(JobState.CREATED));

        // when
        restartBroker();

        clientRule.getSubscriptionClient()
            .newJobSubscription()
            .jobType("foo")
            .handler((client, job) -> client.newCompleteCommand(job).withoutPayload().send())
            .open();

        // then
        waitUntil(() -> eventRecorder.hasJobEvent(JobState.COMPLETED));
    }

    @Test
    public void shouldCompleteStandaloneJobAfterRestart()
    {
        // given
        clientRule.getJobClient()
            .newCreateCommand()
            .jobType("foo")
            .send()
            .join();

        final RecordingJobHandler recordingJobHandler = new RecordingJobHandler();
        clientRule.getSubscriptionClient()
            .newJobSubscription()
            .jobType("foo")
            .handler(recordingJobHandler)
            .open();

        waitUntil(() -> !recordingJobHandler.getHandledJobs().isEmpty());

        // when
        restartBroker();

        final JobEvent jobEvent = recordingJobHandler.getHandledJobs().get(0);
        clientRule.getJobClient()
            .newCompleteCommand(jobEvent)
            .send()
            .join();

        // then
        waitUntil(() -> eventRecorder.hasJobEvent(JobState.COMPLETED));
    }

    @Test
    public void shouldNotReceiveLockedJobAfterRestart()
    {
        // given
        clientRule.getJobClient()
            .newCreateCommand()
            .jobType("foo")
            .send()
            .join();

        final RecordingJobHandler jobHandler = new RecordingJobHandler();
        clientRule.getSubscriptionClient()
            .newJobSubscription()
            .jobType("foo")
            .handler(jobHandler)
            .open();

        waitUntil(() -> !jobHandler.getHandledJobs().isEmpty());

        // when
        restartBroker();

        jobHandler.clear();

        clientRule.getSubscriptionClient()
            .newJobSubscription()
            .jobType("foo")
            .handler(jobHandler)
            .open();

        // then
        TestUtil.doRepeatedly(() -> null)
            .whileConditionHolds((o) -> jobHandler.getHandledJobs().isEmpty());

        assertThat(jobHandler.getHandledJobs()).isEmpty();
    }

    @Test
    public void shouldReceiveLockExpiredJobAfterRestart()
    {
        // given
        clientRule.getJobClient()
            .newCreateCommand()
            .jobType("foo")
            .send()
            .join();

        final RecordingJobHandler jobHandler = new RecordingJobHandler();
        final JobSubscription subscription = clientRule.getSubscriptionClient()
            .newJobSubscription()
            .jobType("foo")
            .handler(jobHandler)
            .open();

        waitUntil(() -> !jobHandler.getHandledJobs().isEmpty());
        subscription.close();

        // when
        restartBroker();

        doRepeatedly(() ->
        {
            brokerRule.getClock().addTime(Duration.ofSeconds(60)); // retriggers lock expiration check in broker
            return null;
        }).until(t -> eventRecorder.hasJobEvent(JobState.LOCK_EXPIRED));
        jobHandler.clear();

        clientRule.getSubscriptionClient()
            .newJobSubscription()
            .jobType("foo")
            .handler(jobHandler)
            .open();

        // then
        waitUntil(() -> !jobHandler.getHandledJobs().isEmpty());

        final JobEvent jobEvent = jobHandler.getHandledJobs().get(0);
        clientRule.getJobClient()
            .newCompleteCommand(jobEvent)
            .send()
            .join();

        waitUntil(() -> eventRecorder.hasJobEvent(JobState.COMPLETED));
    }

    @Test
    public void shouldResolveIncidentAfterRestart()
    {
        // given
        clientRule.getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW_INCIDENT, "incident.bpmn")
            .send()
            .join();

        clientRule.getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

        waitUntil(() -> eventRecorder.hasIncidentEvent(IncidentState.CREATED));

        final WorkflowInstanceEvent activityInstance =
                eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_READY);

        // when
        restartBroker();

        clientRule.getWorkflowClient()
            .newUpdatePayloadCommand(activityInstance)
            .payload("{\"foo\":\"bar\"}")
            .send()
            .join();

        // then
        waitUntil(() -> eventRecorder.hasIncidentEvent(IncidentState.RESOLVED));
        waitUntil(() -> eventRecorder.hasJobEvent(JobState.CREATED));
    }

    @Test
    public void shouldResolveFailedIncidentAfterRestart()
    {
        // given
        clientRule.getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW_INCIDENT, "incident.bpmn")
            .send()
            .join();

        clientRule.getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

        waitUntil(() -> eventRecorder.hasIncidentEvent(IncidentState.CREATED));

        final WorkflowInstanceEvent activityInstance =
                eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_READY);

        clientRule.getWorkflowClient()
            .newUpdatePayloadCommand(activityInstance)
            .payload("{\"x\":\"y\"}")
            .send()
            .join();

        waitUntil(() -> eventRecorder.hasIncidentEvent(IncidentState.RESOLVE_FAILED));

        // when
        restartBroker();

        clientRule.getWorkflowClient()
            .newUpdatePayloadCommand(activityInstance)
            .payload("{\"foo\":\"bar\"}")
            .send()
            .join();

        // then
        waitUntil(() -> eventRecorder.hasIncidentEvent(IncidentState.RESOLVED));
        waitUntil(() -> eventRecorder.hasJobEvent(JobState.CREATED));
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
        restartBroker();

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
        restartBroker();

        // when
        client.newCreateTopicCommand()
            .name("foo")
            .partitions(2)
            .replicationFactor(1)
            .send()
            .join();

        // then
        final JobEvent jobEvent = client.topicClient("foo").jobClient()
                .newCreateCommand()
                .jobType("bar")
                .send()
                .join();

        assertThat(jobEvent.getState()).isEqualTo(JobState.CREATED);
    }

    @Test
    public void shouldNotCreatePreviouslyCreatedTopicAfterRestart()
    {
        // given
        final ZeebeClient client = clientRule.getClient();

        final String topicName = "foo";
        client.newCreateTopicCommand()
            .name(topicName)
            .partitions(2)
            .replicationFactor(1)
            .send()
            .join();

        restartBroker();

        // then
        exception.expect(ClientCommandRejectedException.class);

        // when
        client.newCreateTopicCommand()
            .name(topicName)
            .partitions(2)
            .replicationFactor(1)
            .send()
            .join();
    }

    @Test
    public void shouldCreateUniquePartitionIdsAfterRestart()
    {
        // given
        final ZeebeClient client = clientRule.getClient();

        client.newCreateTopicCommand()
            .name("foo")
            .partitions(2)
            .replicationFactor(1)
            .send()
            .join();

        clientRule.waitUntilTopicsExists("foo");

        restartBroker();

        // when
        client.newCreateTopicCommand()
            .name("bar")
            .partitions(2)
            .replicationFactor(1)
            .send()
            .join();

        clientRule.waitUntilTopicsExists("bar");

        // then
        final Topology topology = client.newTopologyRequest().send().join();
        final List<BrokerInfo> brokers = topology.getBrokers();
        assertThat(brokers).hasSize(1);

        final BrokerInfo topologyBroker = brokers.get(0);
        final List<PartitionInfo> partitions = topologyBroker.getPartitions();

        assertThat(partitions).hasSize(6); // default partition + system partition + 4 partitions we create here
        assertThat(partitions).extracting("partitionId").doesNotHaveDuplicates();
    }

    protected void restartBroker()
    {
        restartBroker(() ->
        { });
    }

    protected void restartBroker(Runnable onStop)
    {
        eventRecorder.stopRecordingEvents();
        brokerRule.stopBroker();

        onStop.run();

        brokerRule.startBroker();
        eventRecorder.startRecordingEvents();
    }

}
