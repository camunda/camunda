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
package io.zeebe.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.commands.BrokerInfo;
import io.zeebe.client.api.commands.PartitionBrokerRole;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.util.TestEnvironmentRule;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ZeebeClientTest {

  @Rule public TestEnvironmentRule rule = new TestEnvironmentRule();

  private ZeebeClient client;

  @Before
  public void setUp() {
    client = rule.getClient();
  }

  @Test
  public void shouldGetHealthCheck() {
    Stream.generate(() -> client.newTopologyRequest().send())
        .limit(10)
        .map(ZeebeFuture::join)
        .forEach(
            response -> {
              assertThat(response).isNotNull();

              final BrokerInfo broker = response.getBrokers().get(0);
              assertThat(broker.getAddress()).isNotEmpty();
              assertThat(broker.getPartitions().size()).isEqualTo(1);

              broker
                  .getPartitions()
                  .forEach(
                      partition -> {
                        assertThat(partition.getPartitionId()).isEqualTo(0);
                        assertThat(partition.getRole()).isEqualTo(PartitionBrokerRole.LEADER);
                      });
            });
  }

  @Test
  public void shouldDeployWorkflow() throws URISyntaxException {
    final String filePath =
        getClass().getResource("/workflows/demo-process.bpmn").toURI().getPath();

    final DeploymentEvent deploymentResponse =
        client.workflowClient().newDeployCommand().addResourceFile(filePath).send().join();

    assertThat(deploymentResponse).isNotNull();
    assertThat(deploymentResponse.getDeployedWorkflows().size()).isEqualTo(1);
    assertThat(deploymentResponse.getDeployedWorkflows().get(0).getBpmnProcessId())
        .isEqualTo("demoProcess");
    assertThat(deploymentResponse.getDeployedWorkflows().get(0).getVersion()).isEqualTo(1);
    assertThat(deploymentResponse.getDeployedWorkflows().get(0).getWorkflowKey()).isEqualTo(1);
    assertThat(deploymentResponse.getDeployedWorkflows().get(0).getResourceName())
        .endsWith("demo-process.bpmn");
  }

  @Test
  public void shouldDeployMultipleWorkflows() throws URISyntaxException {
    final String demoProcessPath =
        getClass().getResource("/workflows/demo-process.bpmn").toURI().getPath();
    final String anotherDemoProcessPath =
        getClass().getResource("/workflows/another-demo-process.bpmn").toURI().getPath();
    final String simpleWorkflowPath =
        getClass().getResource("/workflows/simple-workflow.yaml").toURI().getPath();

    final String[] ids = {"yaml-workflow", "anotherDemoProcess", "demoProcess"};
    final List<String> processIds = Arrays.asList(ids);
    final Long[] keys = {1L, 2L, 3L};
    final List<Long> workflowKeys = Arrays.asList(keys);

    final DeploymentEvent deploymentResponse =
        client
            .workflowClient()
            .newDeployCommand()
            .addResourceFile(demoProcessPath)
            .addResourceFile(anotherDemoProcessPath)
            .addResourceFile(simpleWorkflowPath)
            .send()
            .join();

    assertThat(deploymentResponse).isNotNull();
    assertThat(deploymentResponse.getDeployedWorkflows().size()).isEqualTo(3);

    deploymentResponse
        .getDeployedWorkflows()
        .forEach(
            workflow -> {
              assertTrue(processIds.contains(workflow.getBpmnProcessId()));
              assertThat(workflow.getVersion()).isEqualTo(1);
              assertTrue(workflowKeys.contains(workflow.getWorkflowKey()));
            });
  }

  @Test
  public void shouldNotFailIfClosedTwice() {
    client.close();
    client.close();
  }
}
