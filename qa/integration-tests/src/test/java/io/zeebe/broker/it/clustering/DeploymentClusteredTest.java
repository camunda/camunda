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
import static io.zeebe.protocol.Protocol.DEFAULT_TOPIC;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.gateway.ZeebeClient;
import io.zeebe.gateway.api.commands.Partition;
import io.zeebe.gateway.api.commands.Topic;
import io.zeebe.gateway.api.events.DeploymentEvent;
import io.zeebe.gateway.api.events.WorkflowInstanceEvent;
import io.zeebe.gateway.api.events.WorkflowInstanceState;
import io.zeebe.gateway.cmd.ClientCommandRejectedException;
import io.zeebe.gateway.impl.workflow.CreateWorkflowInstanceCommandImpl;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
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

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

  public AutoCloseableRule closeables = new AutoCloseableRule();
  public Timeout testTimeout = Timeout.seconds(60);
  public ClientRule clientRule = new ClientRule();
  public ClusteringRule clusteringRule = new ClusteringRule(closeables, clientRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(closeables).around(testTimeout).around(clientRule).around(clusteringRule);

  @Rule public ExpectedException expectedException = ExpectedException.none();

  private ZeebeClient client;
  private Topic topic;

  @Before
  public void init() {
    client = clientRule.getClient();

    topic = clusteringRule.waitForTopic(PARTITION_COUNT);
  }

  @Test
  public void shouldDeployInCluster() {
    // given

    // when
    final DeploymentEvent deploymentEvent =
        client
            .topicClient()
            .workflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();

    // then
    assertThat(deploymentEvent.getDeployedWorkflows().size()).isEqualTo(1);
  }

  @Test
  public void shouldDeployWorkflowAndCreateInstances() throws Exception {
    // given

    // when
    final DeploymentEvent deploymentEvent =
        client
            .topicClient()
            .workflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();

    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());

    // then
    for (int p = 0; p < PARTITION_COUNT; p++) {
      final WorkflowInstanceEvent workflowInstanceEvent =
          client
              .topicClient()
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

    // when
    clusteringRule.stopBroker(ClusteringRule.BROKER_3_CLIENT_ADDRESS);

    // then
    final DeploymentEvent deploymentEvent =
        client
            .topicClient()
            .workflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();

    assertThat(deploymentEvent.getDeployedWorkflows().size()).isEqualTo(1);
    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());
  }

  @Test
  @Ignore
  public void shouldCreateInstancesOnRestartedBroker() {
    // given

    clusteringRule.stopBroker(ClusteringRule.BROKER_3_CLIENT_ADDRESS);
    final DeploymentEvent deploymentEvent =
        client
            .topicClient()
            .workflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();

    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());

    // when
    clusteringRule.restartBroker(ClusteringRule.BROKER_3_CLIENT_ADDRESS);
    clusteringRule.waitForTopicPartitionReplicationFactor(
        DEFAULT_TOPIC, PARTITION_COUNT, DEFAULT_REPLICATION_FACTOR);

    // then create wf instance on each partition
    topic
        .getPartitions()
        .stream()
        .mapToInt(Partition::getId)
        .forEach(
            partitionId -> {
              final WorkflowInstanceEvent workflowInstanceEvent =
                  createWorkflowInstanceOnPartition(DEFAULT_TOPIC, partitionId, "process");

              assertThat(workflowInstanceEvent.getState()).isEqualTo(WorkflowInstanceState.CREATED);
            });
  }

  protected WorkflowInstanceEvent createWorkflowInstanceOnPartition(
      String topic, int partition, String processId) {
    final CreateWorkflowInstanceCommandImpl command =
        (CreateWorkflowInstanceCommandImpl)
            client
                .topicClient()
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

    // when
    clusteringRule.restartBroker(ClusteringRule.BROKER_3_CLIENT_ADDRESS);

    // then
    final DeploymentEvent deploymentEvent =
        client
            .topicClient()
            .workflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();

    assertThat(deploymentEvent.getDeployedWorkflows().size()).isEqualTo(1);
    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());
  }

  @Test
  public void shouldNotDeployUnparsable() {
    // given
    clusteringRule.waitForTopic(PARTITION_COUNT);

    // expect
    expectedException.expect(ClientCommandRejectedException.class);
    expectedException.expectMessage("Command (CREATE) was rejected");
    expectedException.expectMessage("Failed to deploy resource 'invalid.bpmn'");
    expectedException.expectMessage("SAXException while parsing input stream");

    // when
    client
        .topicClient()
        .workflowClient()
        .newDeployCommand()
        .addResourceStringUtf8("invalid", "invalid.bpmn")
        .send()
        .join();
  }
}
