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
package io.zeebe.broker.it.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.commands.Workflow;
import io.zeebe.client.api.commands.WorkflowResource;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.cmd.BrokerErrorException;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import io.zeebe.util.StreamUtil;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class WorkflowRepositoryTest {
  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientRule clientRule = new ClientRule();
  public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(brokerRule).around(clientRule).around(eventRecorder);

  @Rule public ExpectedException exception = ExpectedException.none();

  private final List<Workflow> deployedWorkflows = new ArrayList<>();

  private final WorkflowDefinition workflow1v1 =
      Bpmn.createExecutableWorkflow("wf1").startEvent("foo").done();
  private final WorkflowDefinition workflow1v2 =
      Bpmn.createExecutableWorkflow("wf1").startEvent("bar").done();
  private final WorkflowDefinition workflow2 =
      Bpmn.createExecutableWorkflow("wf2").startEvent("start").done();

  @Before
  public void deployWorkflows() {
    final DeploymentEvent firstDeployment =
        clientRule
            .getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(workflow1v1, "workflow1.bpmn")
            .send()
            .join();

    final DeploymentEvent secondDeployment =
        clientRule
            .getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(workflow1v2, "workflow1.bpmn")
            .send()
            .join();

    final DeploymentEvent thirdDeployment =
        clientRule
            .getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(workflow2, "workflow2.bpmn")
            .send()
            .join();

    deployedWorkflows.addAll(firstDeployment.getDeployedWorkflows());
    deployedWorkflows.addAll(secondDeployment.getDeployedWorkflows());
    deployedWorkflows.addAll(thirdDeployment.getDeployedWorkflows());
  }

  @Test
  public void shouldGetResourceByBpmnProcessIdAndLatestVersion() {
    final WorkflowResource workflowResource =
        clientRule
            .getWorkflowClient()
            .newResourceRequest()
            .bpmnProcessId("wf1")
            .latestVersion()
            .send()
            .join();

    assertThat(workflowResource.getBpmnProcessId()).isEqualTo("wf1");
    assertThat(workflowResource.getVersion()).isEqualTo(2);
    assertThat(workflowResource.getWorkflowKey()).isEqualTo(getWorkflowKey("wf1", 2));
    assertThat(workflowResource.getResourceName()).isEqualTo("workflow1.bpmn");
    assertThat(workflowResource.getBpmnXml()).isEqualTo(Bpmn.convertToString(workflow1v2));
  }

  @Test
  public void shouldGetResourceByBpmnProcessIdAndVersion() {
    final WorkflowResource workflowResource =
        clientRule
            .getWorkflowClient()
            .newResourceRequest()
            .bpmnProcessId("wf1")
            .version(1)
            .send()
            .join();

    assertThat(workflowResource.getBpmnProcessId()).isEqualTo("wf1");
    assertThat(workflowResource.getVersion()).isEqualTo(1);
    assertThat(workflowResource.getWorkflowKey()).isEqualTo(getWorkflowKey("wf1", 1));
    assertThat(workflowResource.getResourceName()).isEqualTo("workflow1.bpmn");
    assertThat(workflowResource.getBpmnXml()).isEqualTo(Bpmn.convertToString(workflow1v1));
  }

  @Test
  public void shouldGetResourceByWorkflowKey() throws Exception {
    final WorkflowResource workflowResource =
        clientRule
            .getWorkflowClient()
            .newResourceRequest()
            .workflowKey(getWorkflowKey("wf2", 1))
            .send()
            .join();

    assertThat(workflowResource.getBpmnProcessId()).isEqualTo("wf2");
    assertThat(workflowResource.getVersion()).isEqualTo(1);
    assertThat(workflowResource.getWorkflowKey()).isEqualTo(getWorkflowKey("wf2", 1));
    assertThat(workflowResource.getResourceName()).isEqualTo("workflow2.bpmn");
    assertThat(workflowResource.getBpmnXml()).isEqualTo(Bpmn.convertToString(workflow2));
    assertThat(StreamUtil.read(workflowResource.getBpmnXmlAsStream()))
        .isEqualTo(Bpmn.convertToString(workflow2).getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void shouldFailToGetResourceByWorkflowKeyIfNotExist() {
    final ZeebeFuture<WorkflowResource> future =
        clientRule.getWorkflowClient().newResourceRequest().workflowKey(123).send();

    assertThatThrownBy(() -> future.join())
        .isInstanceOf(BrokerErrorException.class)
        .hasMessageContaining("No workflow found with key '123'");
  }

  @Test
  public void shouldFailToGetResourceByBpmnProcessIdIfNotExist() {
    final ZeebeFuture<WorkflowResource> future =
        clientRule
            .getWorkflowClient()
            .newResourceRequest()
            .bpmnProcessId("foo")
            .latestVersion()
            .send();

    assertThatThrownBy(() -> future.join())
        .isInstanceOf(BrokerErrorException.class)
        .hasMessageContaining("No workflow found with BPMN process id 'foo'");
  }

  @Test
  public void shouldGetDeployedWorkflows() {
    final List<Workflow> workflows =
        clientRule.getWorkflowClient().newWorkflowRequest().send().join().getWorkflows();

    assertThat(workflows).hasSize(3);
    assertThat(workflows).extracting(Workflow::getBpmnProcessId).contains("wf1", "wf1", "wf2");
    assertThat(workflows).extracting(Workflow::getVersion).contains(1, 2, 1);
    assertThat(workflows)
        .extracting(Workflow::getResourceName)
        .contains("workflow1.bpmn", "workflow2.bpmn");
    assertThat(workflows)
        .extracting(Workflow::getWorkflowKey)
        .containsAll(
            deployedWorkflows.stream().map(Workflow::getWorkflowKey).collect(Collectors.toList()));
  }

  @Test
  public void shouldGetDeployedWorkflowsByBpmnProcessId() {
    final List<Workflow> workflows =
        clientRule
            .getWorkflowClient()
            .newWorkflowRequest()
            .bpmnProcessId("wf1")
            .send()
            .join()
            .getWorkflows();

    assertThat(workflows).hasSize(2);
    assertThat(workflows).extracting(Workflow::getBpmnProcessId).contains("wf1", "wf1");
    assertThat(workflows).extracting(Workflow::getVersion).contains(1, 2);
    assertThat(workflows).extracting(Workflow::getResourceName).contains("workflow1.bpmn");
    assertThat(workflows)
        .extracting(Workflow::getWorkflowKey)
        .contains(getWorkflowKey("wf1", 1), getWorkflowKey("wf1", 2));
  }

  @Test
  public void shouldGetNoDeployedWorkflowsIfBpmnProcessIdNotExist() {
    final List<Workflow> workflows =
        clientRule
            .getWorkflowClient()
            .newWorkflowRequest()
            .bpmnProcessId("foo")
            .send()
            .join()
            .getWorkflows();

    assertThat(workflows).isEmpty();
  }

  private long getWorkflowKey(String bpmnProcessId, int version) {
    return deployedWorkflows
        .stream()
        .filter(w -> w.getBpmnProcessId().equals(bpmnProcessId) && w.getVersion() == version)
        .findAny()
        .map(Workflow::getWorkflowKey)
        .orElseThrow(() -> new RuntimeException("no workflow deployed"));
  }
}
