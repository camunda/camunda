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

import static io.zeebe.broker.it.clustering.ClusteringRule.DEFAULT_REPLICATION_FACTOR;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.Partition;
import io.zeebe.client.api.commands.Topic;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.events.WorkflowInstanceState;
import io.zeebe.client.cmd.ClientCommandRejectedException;
import io.zeebe.client.impl.workflow.CreateWorkflowInstanceCommandImpl;
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

public class DeploymentClusteredTest {
  private static final int PARTITION_COUNT = 3;

  private static final WorkflowDefinition WORKFLOW =
      Bpmn.createExecutableWorkflow("process").startEvent().endEvent().done();

  public AutoCloseableRule closeables = new AutoCloseableRule();
  public Timeout testTimeout = Timeout.seconds(60);
  public ClientRule clientRule = new ClientRule();
  public ClusteringRule clusteringRule = new ClusteringRule(closeables, clientRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(closeables).around(testTimeout).around(clientRule).around(clusteringRule);

  @Rule public ExpectedException expectedException = ExpectedException.none();

  private ZeebeClient client;

  @Before
  public void init() {
    client = clientRule.getClient();
  }

  @Test
  public void shouldDeployInCluster() {
    // given
    clusteringRule.createTopic("test", PARTITION_COUNT);

    // when
    final DeploymentEvent deploymentEvent =
        client
            .topicClient("test")
            .workflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();

    // then
    assertThat(deploymentEvent.getDeployedWorkflows().size()).isEqualTo(1);
  }

  @Test
  public void shouldDeployWorkflowAndCreateInstances() {
    // given
    clusteringRule.createTopic("test", PARTITION_COUNT);

    // when
    client
        .topicClient("test")
        .workflowClient()
        .newDeployCommand()
        .addWorkflowModel(WORKFLOW, "workflow.bpmn")
        .send()
        .join();

    // then
    for (int p = 0; p < PARTITION_COUNT; p++) {
      final WorkflowInstanceEvent workflowInstanceEvent =
          client
              .topicClient("test")
              .workflowClient()
              .newCreateInstanceCommand()
              .bpmnProcessId("process")
              .latestVersion()
              .send()
              .join();

      assertThat(workflowInstanceEvent.getState()).isEqualTo(WorkflowInstanceState.CREATED);
    }
  }

  @Test
  @Ignore("https://github.com/zeebe-io/zeebe/issues/844")
  public void shouldDeployOnRemainingBrokers() {
    // given
    clusteringRule.createTopic("test", PARTITION_COUNT);

    // when
    clusteringRule.stopBroker(ClusteringRule.BROKER_3_CLIENT_ADDRESS);

    // then
    final DeploymentEvent deploymentEvent =
        client
            .topicClient("test")
            .workflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();

    assertThat(deploymentEvent.getDeployedWorkflows().size()).isEqualTo(1);
  }

  @Test
  @Ignore
  public void shouldCreateInstancesOnRestartedBroker() {
    // given
    final Topic topic = clusteringRule.createTopic("test", PARTITION_COUNT);

    clusteringRule.stopBroker(ClusteringRule.BROKER_3_CLIENT_ADDRESS);
    client
        .topicClient("test")
        .workflowClient()
        .newDeployCommand()
        .addWorkflowModel(WORKFLOW, "workflow.bpmn")
        .send()
        .join();

    // when
    clusteringRule.restartBroker(ClusteringRule.BROKER_3_CLIENT_ADDRESS);
    clusteringRule.waitForTopicPartitionReplicationFactor(
        "test", PARTITION_COUNT, DEFAULT_REPLICATION_FACTOR);

    // then create wf instance on each partition
    topic
        .getPartitions()
        .stream()
        .mapToInt(Partition::getId)
        .forEach(
            partitionId -> {
              final WorkflowInstanceEvent workflowInstanceEvent =
                  createWorkflowInstanceOnPartition("test", partitionId, "process");

              assertThat(workflowInstanceEvent.getState()).isEqualTo(WorkflowInstanceState.CREATED);
            });
  }

  protected WorkflowInstanceEvent createWorkflowInstanceOnPartition(
      String topic, int partition, String processId) {
    final CreateWorkflowInstanceCommandImpl command =
        (CreateWorkflowInstanceCommandImpl)
            client
                .topicClient(topic)
                .workflowClient()
                .newCreateInstanceCommand()
                .bpmnProcessId(processId)
                .latestVersion();

    command.getCommand().setPartitionId(partition);
    return command.send().join();
  }

  @Test
  @Ignore("https://github.com/zeebe-io/zeebe/issues/844")
  public void shouldDeployAfterRestartBroker() {
    // given
    final String topicName = "test";
    clusteringRule.createTopic(topicName, PARTITION_COUNT);

    // when
    clusteringRule.restartBroker(ClusteringRule.BROKER_3_CLIENT_ADDRESS);

    // then
    final DeploymentEvent deploymentEvent =
        client
            .topicClient("test")
            .workflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();

    assertThat(deploymentEvent.getDeployedWorkflows().size()).isEqualTo(1);
  }

  @Test
  public void shouldDeployOnDifferentTopics() {
    // given
    clusteringRule.createTopic("test-1", PARTITION_COUNT);
    clusteringRule.createTopic("test-2", PARTITION_COUNT);

    // when
    final DeploymentEvent deploymentEventOnTest1 =
        client
            .topicClient("test-1")
            .workflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();

    final DeploymentEvent deploymentEventOnTest2 =
        client
            .topicClient("test-2")
            .workflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();

    // then
    assertThat(deploymentEventOnTest1.getDeployedWorkflows().size()).isEqualTo(1);
    assertThat(deploymentEventOnTest2.getDeployedWorkflows().size()).isEqualTo(1);
  }

  @Test
  public void shouldNotDeployUnparsable() {
    // given
    clusteringRule.createTopic("test", PARTITION_COUNT);

    // expect
    expectedException.expect(ClientCommandRejectedException.class);
    expectedException.expectMessage("Command (CREATE) was rejected");
    expectedException.expectMessage("Failed to deploy resource 'invalid.bpmn'");
    expectedException.expectMessage("Failed to read BPMN model");

    // when
    client
        .topicClient("test")
        .workflowClient()
        .newDeployCommand()
        .addResourceStringUtf8("invalid", "invalid.bpmn")
        .send()
        .join();
  }

  @Test
  public void shouldNotDeployForNonExistingTopic() {
    // expect
    expectedException.expect(ClientCommandRejectedException.class);
    expectedException.expectMessage("Command (CREATE) was rejected");

    // when
    client
        .topicClient("test")
        .workflowClient()
        .newDeployCommand()
        .addWorkflowModel(WORKFLOW, "workflow.bpmn")
        .send()
        .join();
  }
}
