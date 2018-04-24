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
package io.zeebe.broker.it.clustering;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.event.DeploymentEvent;
import io.zeebe.client.event.WorkflowInstanceEvent;
import io.zeebe.client.topic.Partition;
import io.zeebe.client.topic.Topic;
import io.zeebe.client.workflow.impl.CreateWorkflowInstanceCommandImpl;
import io.zeebe.client.impl.topic.Topic;
import io.zeebe.client.impl.workflow.impl.CreateWorkflowInstanceCommandImpl;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.test.util.AutoCloseableRule;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

import static io.zeebe.broker.it.clustering.ClusteringRule.DEFAULT_REPLICATION_FACTOR;
import static org.assertj.core.api.Assertions.assertThat;

public class DeploymentClusteredTest
{
    private static final int PARTITION_COUNT = 3;

    private static final WorkflowDefinition INVALID_WORKFLOW = Bpmn.createExecutableWorkflow("invalid").done();

    private static final WorkflowDefinition WORKFLOW = Bpmn.createExecutableWorkflow("process")
                                                           .startEvent()
                                                           .endEvent()
                                                           .done();

    private static final WorkflowDefinition WORKFLOW_WITH_TASK = Bpmn.createExecutableWorkflow("process-2")
                                                                     .startEvent()
                                                                     .serviceTask()
                                                                     .taskType("task")
                                                                     .taskRetries(3)
                                                                     .done()
                                                                     .endEvent()
                                                                     .done();

    private static final WorkflowDefinition WORKFLOW_WITH_OTHER_TASK = Bpmn.createExecutableWorkflow("process-3")
                                                                     .startEvent()
                                                                     .serviceTask()
                                                                     .taskType("otherTask")
                                                                     .taskRetries(3)
                                                                     .done()
                                                                     .endEvent()
                                                                     .done();


    public AutoCloseableRule closeables = new AutoCloseableRule();
    public Timeout testTimeout = Timeout.seconds(60);
    public ClientRule clientRule = new ClientRule(false);
    public ClusteringRule clusteringRule = new ClusteringRule(closeables, clientRule);

    @Rule
    public RuleChain ruleChain =
        RuleChain.outerRule(closeables)
                 .around(testTimeout)
                 .around(clientRule)
                 .around(clusteringRule);

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private ZeebeClient client;

    @Before
    public void init()
    {
        client = clientRule.getClient();
    }

    @Test
    public void shouldDeployInCluster()
    {
        // given
        clusteringRule.createTopic("test", PARTITION_COUNT);

        // when
        final DeploymentEvent deploymentEvent = client.workflows()
                                                      .deploy("test")
                                                      .addWorkflowModel(WORKFLOW, "workflow.bpmn")
                                                      .execute();

        // then
        assertThat(deploymentEvent.getDeployedWorkflows().size()).isEqualTo(1);
        assertThat(deploymentEvent.getErrorMessage()).isEmpty();
    }

    @Test
    public void shouldDeployWorkflowAndCreateInstances()
    {
        // given
        clusteringRule.createTopic("test", PARTITION_COUNT);

        // when
        client.workflows().deploy("test")
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .execute();

        // then
        for (int p = 0; p < PARTITION_COUNT; p++)
        {
            final WorkflowInstanceEvent workflowInstanceEvent = client.workflows()
                                                                      .create("test")
                                                                      .bpmnProcessId("process")
                                                                      .execute();

            assertThat(workflowInstanceEvent.getState()).isEqualTo("WORKFLOW_INSTANCE_CREATED");
        }
    }

    @Test
    @Ignore("https://github.com/zeebe-io/zeebe/issues/844")
    public void shouldDeployOnRemainingBrokers()
    {
        // given
        clusteringRule.createTopic("test", PARTITION_COUNT);

        // when
        clusteringRule.stopBroker(ClusteringRule.BROKER_3_CLIENT_ADDRESS);

        // then
        final DeploymentEvent deploymentEvent = client.workflows()
                                           .deploy("test")
                                           .addWorkflowModel(WORKFLOW, "workflow.bpmn")
                                           .execute();

        assertThat(deploymentEvent.getDeployedWorkflows().size()).isEqualTo(1);
        assertThat(deploymentEvent.getErrorMessage()).isEmpty();
    }

    @Test
    @Ignore
    public void shouldCreateInstancesOnRestartedBroker()
    {
        // given
        final Topic topic = clusteringRule.createTopic("test", PARTITION_COUNT);

        clusteringRule.stopBroker(ClusteringRule.BROKER_3_CLIENT_ADDRESS);
        client.workflows()
              .deploy("test")
              .addWorkflowModel(WORKFLOW, "workflow.bpmn")
              .execute();

        // when
        clusteringRule.restartBroker(ClusteringRule.BROKER_3_CLIENT_ADDRESS);
        clusteringRule.waitForTopicPartitionReplicationFactor("test", PARTITION_COUNT, DEFAULT_REPLICATION_FACTOR);

        // then create wf instance on each partition
        topic.getPartitions().stream()
             .mapToInt(Partition::getId)
             .forEach(partitionId ->
             {
                 final WorkflowInstanceEvent workflowInstanceEvent = createWorkflowInstanceOnPartition("test", partitionId, "process");

                 assertThat(workflowInstanceEvent.getState()).isEqualTo("WORKFLOW_INSTANCE_CREATED");
             });
    }

    protected WorkflowInstanceEvent createWorkflowInstanceOnPartition(String topic, int partition, String processId)
    {
        final CreateWorkflowInstanceCommandImpl createTaskCommand =
            (CreateWorkflowInstanceCommandImpl) client.workflows().create(topic).bpmnProcessId(processId);
        createTaskCommand.getCommand().setPartitionId(partition);
        return createTaskCommand.execute();
    }

    @Test
    @Ignore("https://github.com/zeebe-io/zeebe/issues/844")
    public void shouldDeployAfterRestartBroker()
    {
        // given
        final String topicName = "test";
        clusteringRule.createTopic(topicName, PARTITION_COUNT);

        // when
        clusteringRule.restartBroker(ClusteringRule.BROKER_3_CLIENT_ADDRESS);

        // then
        final DeploymentEvent deploymentEvent = client.workflows()
                                                      .deploy(topicName)
                                                      .addWorkflowModel(WORKFLOW, "workflow.bpmn")
                                                      .execute();

        assertThat(deploymentEvent.getDeployedWorkflows().size()).isEqualTo(1);
        assertThat(deploymentEvent.getErrorMessage()).isEmpty();
    }

    @Test
    public void shouldDeployOnDifferentTopics()
    {
        // given
        clusteringRule.createTopic("test-1", PARTITION_COUNT);
        clusteringRule.createTopic("test-2", PARTITION_COUNT);

        // when
        final DeploymentEvent deploymentEventOnTest1 = client.workflows()
                                                             .deploy("test-2")
                                                             .addWorkflowModel(WORKFLOW, "workflow.bpmn")
                                                             .execute();

        final DeploymentEvent deploymentEventOnTest2 = client.workflows()
                                                             .deploy("test-2")
                                                             .addWorkflowModel(WORKFLOW, "workflow.bpmn")
                                                             .execute();

        // then
        assertThat(deploymentEventOnTest1.getDeployedWorkflows().size()).isEqualTo(1);
        assertThat(deploymentEventOnTest1.getErrorMessage()).isEmpty();

        assertThat(deploymentEventOnTest2.getDeployedWorkflows().size()).isEqualTo(1);
        assertThat(deploymentEventOnTest2.getErrorMessage()).isEmpty();
    }

    @Test
    public void shouldNotDeployUnparsable()
    {
        // given
        clusteringRule.createTopic("test", PARTITION_COUNT);

        // expect
        expectedException.expect(ClientCommandRejectedException.class);
        expectedException.expectMessage("Deployment was rejected");
        expectedException.expectMessage("Failed to deploy resource 'invalid.bpmn'");
        expectedException.expectMessage("Failed to read BPMN model");

        // when
        client.workflows()
              .deploy("test")
              .addResourceStringUtf8("invalid", "invalid.bpmn")
              .execute();
    }

    @Test
    public void shouldNotDeployForNonExistingTopic()
    {
        // given

        // expect
        expectedException.expect(ClientCommandRejectedException.class);
        expectedException.expectMessage("Deployment was rejected");

        // when
        client.workflows()
              .deploy("test")
              .addWorkflowModel(WORKFLOW, "workflow.bpmn")
              .execute();
    }
}
