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

import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertJobCompleted;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertWorkflowInstanceCompleted;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertWorkflowInstanceCreated;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.it.util.RecordingJobHandler;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.clients.WorkflowClient;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.response.ActivatedJob;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class YamlWorkflowTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void shouldCreateWorkflowInstance() {
    // given
    final String resource = "workflows/simple-workflow.yaml";
    deploy(resource);

    // when
    final WorkflowInstanceEvent workflowInstance =
        clientRule
            .getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("yaml-workflow")
            .latestVersion()
            .send()
            .join();

    // then
    assertThat(workflowInstance.getWorkflowInstanceKey()).isGreaterThan(0);
    assertWorkflowInstanceCreated();
  }

  @Test
  public void shouldCompleteWorkflowInstanceWithTask() {
    // given
    final String resource = "workflows/simple-workflow.yaml";
    deploy(resource);

    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("yaml-workflow")
        .latestVersion()
        .send()
        .join();

    // when
    clientRule
        .getJobClient()
        .newWorker()
        .jobType("foo")
        .handler((client, job) -> client.newCompleteCommand(job.getKey()).payload("{ }").send())
        .open();

    // then
    assertJobCompleted();
    assertWorkflowInstanceCompleted("yaml-workflow");
  }

  @Test
  public void shouldGetTaskWithHeaders() {
    // given
    final String resource = "workflows/workflow-with-headers.yaml";
    deploy(resource);

    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("workflow-headers")
        .latestVersion()
        .send()
        .join();

    // when
    final RecordingJobHandler recordingJobHandler = new RecordingJobHandler();

    clientRule.getJobClient().newWorker().jobType("foo").handler(recordingJobHandler).open();

    // then
    waitUntil(() -> recordingJobHandler.getHandledJobs().size() >= 1);

    final ActivatedJob jobEvent = recordingJobHandler.getHandledJobs().get(0);
    assertThat(jobEvent.getCustomHeaders()).containsEntry("foo", "f").containsEntry("bar", "b");
  }

  @Test
  public void shouldCompleteTaskWithPayload() {
    // given
    final WorkflowClient workflowClient = clientRule.getWorkflowClient();
    final String resource = "workflows/workflow-with-mappings.yaml";
    deploy(resource);

    workflowClient
        .newCreateInstanceCommand()
        .bpmnProcessId("workflow-mappings")
        .latestVersion()
        .payload("{\"foo\":1}")
        .send()
        .join();

    // when
    final RecordingJobHandler recordingTaskHandler =
        new RecordingJobHandler(
            (client, job) ->
                client.newCompleteCommand(job.getKey()).payload("{\"result\":3}").send());

    clientRule.getJobClient().newWorker().jobType("foo").handler(recordingTaskHandler).open();

    // then
    waitUntil(() -> recordingTaskHandler.getHandledJobs().size() >= 1);

    final ActivatedJob jobEvent = recordingTaskHandler.getHandledJobs().get(0);
    assertThat(jobEvent.getPayload()).isEqualTo("{\"bar\":1}");

    assertWorkflowInstanceCompleted(
        "workflow-mappings",
        (workflowInstance) -> {
          assertThat(workflowInstance.getPayload()).isEqualTo("{\"foo\":1,\"result\":3}");
        });
  }

  private void deploy(String resource) {
    final DeploymentEvent deploymentEvent =
        clientRule
            .getWorkflowClient()
            .newDeployCommand()
            .addResourceFromClasspath(resource)
            .send()
            .join();

    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());
  }
}
