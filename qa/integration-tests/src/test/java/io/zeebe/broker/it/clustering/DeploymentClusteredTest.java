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

import static io.zeebe.broker.it.util.StatusCodeMatcher.hasStatusCode;
import static io.zeebe.broker.it.util.StatusDescriptionMatcher.descriptionContains;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertWorkflowInstanceCreated;
import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Status.Code;
import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.cmd.ClientStatusException;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
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

  public Timeout testTimeout = Timeout.seconds(120);
  public ClusteringRule clusteringRule = new ClusteringRule();
  public GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  @Rule public ExpectedException expectedException = ExpectedException.none();

  private ZeebeClient client;

  @Before
  public void init() {
    client = clientRule.getClient();
  }

  @Test
  public void shouldDeployInCluster() {
    // given

    // when
    final DeploymentEvent deploymentEvent =
        client.newDeployCommand().addWorkflowModel(WORKFLOW, "workflow.bpmn").send().join();

    // then
    assertThat(deploymentEvent.getWorkflows().size()).isEqualTo(1);
  }

  @Test
  public void shouldDeployWorkflowAndCreateInstances() throws Exception {
    // given

    // when
    final DeploymentEvent deploymentEvent =
        client.newDeployCommand().addWorkflowModel(WORKFLOW, "workflow.bpmn").send().join();

    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());

    // then
    for (int p = 0; p < PARTITION_COUNT; p++) {
      final long workflowInstanceKey =
          client
              .newCreateInstanceCommand()
              .bpmnProcessId("process")
              .latestVersion()
              .send()
              .join()
              .getWorkflowInstanceKey();

      assertWorkflowInstanceCreated(workflowInstanceKey);
    }
  }

  @Test
  public void shouldDeployOnRemainingBrokers() {
    // given

    // when
    clusteringRule.stopBroker(2);

    // then
    final DeploymentEvent deploymentEvent =
        client.newDeployCommand().addWorkflowModel(WORKFLOW, "workflow.bpmn").send().join();

    assertThat(deploymentEvent.getWorkflows().size()).isEqualTo(1);
    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());
  }

  @Test
  @Ignore
  public void shouldCreateInstancesOnRestartedBroker() {
    // given

    clusteringRule.stopBroker(2);
    final DeploymentEvent deploymentEvent =
        client.newDeployCommand().addWorkflowModel(WORKFLOW, "workflow.bpmn").send().join();

    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());

    // when
    clusteringRule.restartBroker(2);

    // then create wf instance on each partition
    clusteringRule.getPartitionIds().stream()
        .forEach(
            partitionId -> {
              final long instanceKey =
                  clusteringRule.createWorkflowInstanceOnPartition(partitionId, "process");
              assertWorkflowInstanceCreated(instanceKey);
            });
  }

  @Test
  public void shouldDeployAfterRestartBroker() {
    // given

    // when
    clusteringRule.restartBroker(2);

    // then
    final DeploymentEvent deploymentEvent =
        client.newDeployCommand().addWorkflowModel(WORKFLOW, "workflow.bpmn").send().join();

    assertThat(deploymentEvent.getWorkflows().size()).isEqualTo(1);
    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());
  }

  @Test
  public void shouldNotDeployUnparseable() {
    // expect
    expectedException.expect(ClientStatusException.class);
    expectedException.expect(hasStatusCode(Code.INVALID_ARGUMENT));
    expectedException.expect(
        descriptionContains("'invalid.bpmn': SAXException while parsing input stream"));

    // when
    client.newDeployCommand().addResourceStringUtf8("invalid", "invalid.bpmn").send().join();
  }
}
