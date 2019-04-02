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
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.it.util.RecordingJobHandler;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.VariableRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.JsonUtil;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.stream.Collectors;
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
            .getClient()
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
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("yaml-workflow")
        .latestVersion()
        .send()
        .join();

    // when
    clientRule
        .getClient()
        .newWorker()
        .jobType("foo")
        .handler((client, job) -> client.newCompleteCommand(job.getKey()).variables("{ }").send())
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
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("workflow-headers")
        .latestVersion()
        .send()
        .join();

    // when
    final RecordingJobHandler recordingJobHandler = new RecordingJobHandler();

    clientRule.getClient().newWorker().jobType("foo").handler(recordingJobHandler).open();

    // then
    waitUntil(() -> recordingJobHandler.getHandledJobs().size() >= 1);

    final ActivatedJob jobEvent = recordingJobHandler.getHandledJobs().get(0);
    assertThat(jobEvent.getCustomHeaders()).containsEntry("foo", "f").containsEntry("bar", "b");
  }

  @Test
  public void shouldCompleteTaskWithVariables() {
    // given
    final ZeebeClient zeebeClient = clientRule.getClient();
    final String resource = "workflows/workflow-with-mappings.yaml";
    deploy(resource);

    final WorkflowInstanceEvent event =
        zeebeClient
            .newCreateInstanceCommand()
            .bpmnProcessId("workflow-mappings")
            .latestVersion()
            .variables("{\"foo\":1}")
            .send()
            .join();

    // when
    final RecordingJobHandler recordingTaskHandler =
        new RecordingJobHandler(
            (client, job) ->
                client.newCompleteCommand(job.getKey()).variables("{\"result\":3}").send());

    zeebeClient.newWorker().jobType("foo").handler(recordingTaskHandler).open();

    // then
    waitUntil(() -> recordingTaskHandler.getHandledJobs().size() >= 1);

    final ActivatedJob jobEvent = recordingTaskHandler.getHandledJobs().get(0);
    JsonUtil.assertEquality(jobEvent.getVariables(), "{'bar': 1, 'foo': 1}");

    final List<Record<VariableRecordValue>> variableRecords = getFinalVariableRecords(event);

    assertWorkflowInstanceCompleted(event.getWorkflowInstanceKey());
    assertThat(variableRecords).hasSize(2);
    assertThat(variableRecords)
        .extracting(r -> r.getValue().getName(), r -> r.getValue().getValue())
        .containsExactly(tuple("foo", "1"), tuple("result", "3"));
  }

  @Test
  public void shouldCreateInstanceAfterMultipleWorkflowsDeployed() {
    // given
    final long firstKey = deploy("workflows/workflow-with-headers.yaml");
    final long secondKey = deploy("workflows/simple-workflow.yaml");

    // when
    final WorkflowInstanceEvent firstWorkflowInstance =
        clientRule.getClient().newCreateInstanceCommand().workflowKey(firstKey).send().join();

    final WorkflowInstanceEvent secondWorkflowInstance =
        clientRule.getClient().newCreateInstanceCommand().workflowKey(secondKey).send().join();

    // then
    assertWorkflowInstanceCreated(firstWorkflowInstance.getWorkflowInstanceKey());
    assertWorkflowInstanceCreated(secondWorkflowInstance.getWorkflowInstanceKey());
  }

  private long deploy(String resource) {
    final DeploymentEvent deploymentEvent =
        clientRule.getClient().newDeployCommand().addResourceFromClasspath(resource).send().join();

    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());
    return deploymentEvent.getWorkflows().get(0).getWorkflowKey();
  }

  private List<Record<VariableRecordValue>> getFinalVariableRecords(WorkflowInstanceEvent event) {
    final Record<WorkflowInstanceRecordValue> finalRecord =
        RecordingExporter.workflowInstanceRecords()
            .withElementId(event.getBpmnProcessId())
            .withWorkflowInstanceKey(event.getWorkflowInstanceKey())
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withFlowScopeKey(-1)
            .getFirst();

    return RecordingExporter.records()
        .limit(finalRecord::equals)
        .variableRecords()
        .withScopeKey(event.getWorkflowInstanceKey())
        .collect(Collectors.toList());
  }
}
