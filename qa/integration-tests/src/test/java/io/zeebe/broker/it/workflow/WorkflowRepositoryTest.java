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

import static io.zeebe.broker.it.util.StatusCodeMatcher.hasStatusCode;
import static io.zeebe.broker.it.util.StatusDescriptionMatcher.descriptionContains;
import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Status.Code;
import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.commands.Workflow;
import io.zeebe.client.api.commands.WorkflowResource;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.cmd.ClientStatusException;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.util.StreamUtil;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class WorkflowRepositoryTest {
  private final List<Workflow> deployedWorkflows = new ArrayList<>();
  private final BpmnModelInstance workflow1v1 =
      Bpmn.createExecutableProcess("wf1").startEvent("foo").done();
  private final BpmnModelInstance workflow1v2 =
      Bpmn.createExecutableProcess("wf1").startEvent("bar").done();
  private final BpmnModelInstance workflow2 =
      Bpmn.createExecutableProcess("wf2").startEvent("start").done();
  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Before
  public void deployWorkflows() {
    final DeploymentEvent firstDeployment =
        clientRule
            .getClient()
            .newDeployCommand()
            .addWorkflowModel(workflow1v1, "workflow1.bpmn")
            .send()
            .join();

    final DeploymentEvent secondDeployment =
        clientRule
            .getClient()
            .newDeployCommand()
            .addWorkflowModel(workflow1v2, "workflow1.bpmn")
            .send()
            .join();

    final DeploymentEvent thirdDeployment =
        clientRule
            .getClient()
            .newDeployCommand()
            .addWorkflowModel(workflow2, "workflow2.bpmn")
            .send()
            .join();

    clientRule.waitUntilDeploymentIsDone(thirdDeployment.getKey());

    deployedWorkflows.addAll(firstDeployment.getWorkflows());
    deployedWorkflows.addAll(secondDeployment.getWorkflows());
    deployedWorkflows.addAll(thirdDeployment.getWorkflows());
  }

  @Test
  public void shouldGetResourceByBpmnProcessIdAndLatestVersion() {
    final WorkflowResource workflowResource =
        clientRule
            .getClient()
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
        clientRule.getClient().newResourceRequest().bpmnProcessId("wf1").version(1).send().join();

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
            .getClient()
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
        clientRule.getClient().newResourceRequest().workflowKey(123).send();

    // expect
    exception.expect(ClientStatusException.class);
    exception.expect(hasStatusCode(Code.NOT_FOUND));
    exception.expect(descriptionContains("key '123'"));

    // when
    future.join();
  }

  @Test
  public void shouldFailToGetResourceByBpmnProcessIdIfNotExist() {
    final ZeebeFuture<WorkflowResource> future =
        clientRule.getClient().newResourceRequest().bpmnProcessId("foo").latestVersion().send();

    // expect
    exception.expect(ClientStatusException.class);
    exception.expect(hasStatusCode(Code.NOT_FOUND));
    exception.expect(descriptionContains("BPMN process ID 'foo'"));

    // when
    future.join();
  }

  @Test
  public void shouldGetDeployedWorkflows() {
    final List<Workflow> workflows =
        clientRule.getClient().newWorkflowRequest().send().join().getWorkflows();

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
            .getClient()
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
            .getClient()
            .newWorkflowRequest()
            .bpmnProcessId("foo")
            .send()
            .join()
            .getWorkflows();

    assertThat(workflows).isEmpty();
  }

  private long getWorkflowKey(final String bpmnProcessId, final int version) {
    return deployedWorkflows
        .stream()
        .filter(w -> w.getBpmnProcessId().equals(bpmnProcessId) && w.getVersion() == version)
        .findAny()
        .map(Workflow::getWorkflowKey)
        .orElseThrow(() -> new RuntimeException("no workflow deployed"));
  }
}
