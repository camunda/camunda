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
package io.zeebe.broker.it.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.ActivateJobsResponse;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.metrics.MetricsManager;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.stream.Collectors;
import org.agrona.ExpandableArrayBuffer;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class MetricsTest {

  public static EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public static GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @ClassRule public static RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  private static MetricsManager metricsManager;
  private static ZeebeClient client;

  @BeforeClass
  public static void setUp() {
    metricsManager = brokerRule.getBroker().getBrokerContext().getScheduler().getMetricsManager();
    client = clientRule.getClient();
  }

  @Test
  public void shouldUpdateWorkflowInstanceMetrics() {
    // given
    final String processId = "p" + UUID.randomUUID().toString();
    final String jobType = UUID.randomUUID().toString();
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", tb -> tb.zeebeTaskType(jobType))
            .endEvent()
            .done();
    client.newDeployCommand().addWorkflowModel(modelInstance, "process.bpmn").send().join();

    // when creating and completing a workflow
    long workflowInstanceKey = createWorkflowInstance(processId);
    awaitJobCreated(workflowInstanceKey);
    completeJob(workflowInstanceKey, jobType);
    awaitWorkflowInstanceCompleted(workflowInstanceKey);

    // and when creating and canceling a workflow
    workflowInstanceKey = createWorkflowInstance(processId);
    awaitJobCreated(workflowInstanceKey);
    cancelWorkflowInstance(workflowInstanceKey);
    awaitWorkflowInstanceCanceled(workflowInstanceKey);

    // then
    assertThat(metricsAsString())
        .contains(
            metric(
                "zb_workflow_instance_events_count",
                2,
                clusterLabel(),
                nodeLabel(),
                entry("partition", "1"),
                entry("type", "created")),
            metric(
                "zb_workflow_instance_events_count",
                1,
                clusterLabel(),
                nodeLabel(),
                entry("partition", "1"),
                entry("type", "completed")),
            metric(
                "zb_workflow_instance_events_count",
                1,
                clusterLabel(),
                nodeLabel(),
                entry("partition", "1"),
                entry("type", "canceled")));
  }

  private long createWorkflowInstance(String processId) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .send()
        .join()
        .getWorkflowInstanceKey();
  }

  private void awaitJobCreated(long workflowInstanceKey) {
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .await();
  }

  private void completeJob(long workflowInstanceKey, String jobType) {
    final ActivateJobsResponse response =
        client.newActivateJobsCommand().jobType(jobType).maxJobsToActivate(1).send().join();
    assertThat(response.getJobs()).hasSize(1);
    client.newCompleteCommand(response.getJobs().get(0).getKey()).send().join();
  }

  private void awaitWorkflowInstanceCompleted(long workflowInstanceKey) {
    RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
  }

  private void cancelWorkflowInstance(long workflowInstanceKey) {
    client.newCancelInstanceCommand(workflowInstanceKey).send().join();
  }

  private void awaitWorkflowInstanceCanceled(long workflowInstanceKey) {
    RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_TERMINATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
  }

  @SafeVarargs
  private final String metric(String name, int value, Entry<String, String>... labels) {
    final StringBuilder builder = new StringBuilder();
    builder.append(name);
    if (labels != null) {
      builder
          .append("{")
          .append(
              Arrays.stream(labels)
                  .map(label -> String.format("%s=\"%s\"", label.getKey(), label.getValue()))
                  .collect(Collectors.joining(",")))
          .append("}");
    }
    return builder.append(" ").append(value).toString();
  }

  private String metricsAsString() {
    final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
    final int length = metricsManager.dump(buffer, 0, Instant.now().toEpochMilli());

    return BufferUtil.bufferAsString(buffer, 0, length);
  }

  private Entry<String, String> clusterLabel() {
    return entry("cluster", "zeebe");
  }

  private Entry<String, String> nodeLabel() {
    return entry("node", brokerRule.getClientAddress().toString());
  }
}
