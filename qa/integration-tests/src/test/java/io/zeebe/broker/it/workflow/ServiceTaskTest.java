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

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.util.RecordingJobHandler;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.gateway.api.clients.JobClient;
import io.zeebe.gateway.api.events.DeploymentEvent;
import io.zeebe.gateway.api.events.JobEvent;
import io.zeebe.gateway.api.events.JobState;
import io.zeebe.gateway.api.events.WorkflowInstanceEvent;
import io.zeebe.gateway.api.events.WorkflowInstanceState;
import io.zeebe.gateway.api.subscription.JobHandler;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class ServiceTaskTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientRule clientRule = new ClientRule(brokerRule);
  public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(brokerRule).around(clientRule).around(eventRecorder);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void shouldCreateWorkflowInstanceWithServiceTask() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task", t -> t.zeebeTaskType("foo"))
            .endEvent("end")
            .done();
    deploy(modelInstance);

    // when
    final WorkflowInstanceEvent workflowInstance =
        clientRule
            .getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    // then
    assertThat(workflowInstance.getWorkflowInstanceKey()).isGreaterThan(0);
    waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.CREATED));
  }

  @Test
  public void shouldLockServiceTask() {
    // given
    final Map<String, String> customHeaders = new HashMap<>();
    customHeaders.put("cust1", "a");
    customHeaders.put("cust2", "b");
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask(
                "task",
                t ->
                    t.zeebeTaskType("foo")
                        .zeebeTaskHeader("cust1", "a")
                        .zeebeTaskHeader("cust2", "b"))
            .endEvent("end")
            .done();
    deploy(modelInstance);

    final WorkflowInstanceEvent workflowInstance =
        clientRule
            .getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    // when
    final RecordingJobHandler recordingJobHandler = new RecordingJobHandler();

    clientRule.getJobClient().newWorker().jobType("foo").handler(recordingJobHandler).open();

    // then
    waitUntil(() -> recordingJobHandler.getHandledJobs().size() >= 1);

    assertThat(recordingJobHandler.getHandledJobs()).hasSize(1);

    final WorkflowInstanceEvent activityInstance =
        eventRecorder.getElementInState("task", WorkflowInstanceState.ELEMENT_ACTIVATED);

    final JobEvent jobEvent = recordingJobHandler.getHandledJobs().get(0);
    assertThat(jobEvent.getHeaders())
        .containsOnly(
            entry("bpmnProcessId", "process"),
            entry("workflowDefinitionVersion", 1),
            entry("workflowKey", (int) workflowInstance.getWorkflowKey()),
            entry("workflowInstanceKey", (int) workflowInstance.getWorkflowInstanceKey()),
            entry("activityId", "task"),
            entry("activityInstanceKey", (int) activityInstance.getMetadata().getKey()));

    assertThat(jobEvent.getCustomHeaders()).containsOnly(entry("cust1", "a"), entry("cust2", "b"));
  }

  @Test
  public void shouldCompleteServiceTask() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task", t -> t.zeebeTaskType("foo"))
            .endEvent("end")
            .done();

    deploy(modelInstance);

    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .send()
        .join();

    // when
    clientRule
        .getJobClient()
        .newWorker()
        .jobType("foo")
        .handler((client, job) -> client.newCompleteCommand(job).send())
        .open();

    // then
    waitUntil(() -> eventRecorder.hasJobEvent(JobState.COMPLETED));
    waitUntil(
        () -> eventRecorder.hasElementInState("process", WorkflowInstanceState.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldMapPayloadIntoTask() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task", t -> t.zeebeTaskType("foo").zeebeInput("$.foo", "$.bar"))
            .endEvent("end")
            .done();
    deploy(modelInstance);

    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .payload("{\"foo\":1}")
        .send()
        .join();

    // when
    final RecordingJobHandler recordingJobHandler = new RecordingJobHandler();

    clientRule.getJobClient().newWorker().jobType("foo").handler(recordingJobHandler).open();

    // then
    waitUntil(() -> recordingJobHandler.getHandledJobs().size() >= 1);

    final JobEvent jobEvent = recordingJobHandler.getHandledJobs().get(0);
    assertThat(jobEvent.getPayload()).isEqualTo("{\"bar\":1}");
  }

  @Test
  public void shouldMapPayloadFromTask() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task", t -> t.zeebeTaskType("foo").zeebeOutput("$.foo", "$.bar"))
            .endEvent("end")
            .done();
    deploy(modelInstance);

    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .send()
        .join();

    // when
    clientRule
        .getJobClient()
        .newWorker()
        .jobType("foo")
        .handler((client, job) -> client.newCompleteCommand(job).payload("{\"foo\":2}").send())
        .open();

    // then
    waitUntil(
        () -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.ELEMENT_COMPLETED));

    final WorkflowInstanceEvent workflowEvent =
        eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.ELEMENT_COMPLETED);
    assertThat(workflowEvent.getPayload()).isEqualTo("{\"bar\":2}");
  }

  @Test
  public void shouldModifyPayloadInTask() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask(
                "task",
                t ->
                    t.zeebeTaskType("foo")
                        .zeebeInput("$.foo", "$.foo")
                        .zeebeOutput("$.foo", "$.foo"))
            .endEvent("end")
            .done();
    deploy(modelInstance);

    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .payload("{\"foo\":1}")
        .send()
        .join();

    // when
    clientRule
        .getJobClient()
        .newWorker()
        .jobType("foo")
        .handler(
            (client, job) -> {
              final String modifiedPayload = job.getPayload().replaceAll("1", "2");
              client.newCompleteCommand(job).payload(modifiedPayload).send();
            })
        .open();

    // then
    waitUntil(
        () -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.ELEMENT_COMPLETED));

    final WorkflowInstanceEvent workflowEvent =
        eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.ELEMENT_COMPLETED);
    assertThat(workflowEvent.getPayload()).isEqualTo("{\"foo\":2}");
  }

  @Test
  public void shouldCompleteTasksAndMergePayload() throws Exception {

    // given
    clientRule
        .getWorkflowClient()
        .newDeployCommand()
        .addResourceFile(getClass().getResource("/workflows/orderProcess.bpmn").getFile())
        .send()
        .join();

    final WorkflowInstanceEvent wfEvent =
        clientRule
            .getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("order-process")
            .latestVersion()
            .payload("{\"foo\":1}")
            .send()
            .join();

    // when
    final JobHandler defaultHandler =
        new JobHandler() {
          @Override
          public void handle(JobClient client, JobEvent job) {
            client.newCompleteCommand(job).payload("{}").send().join();
          }
        };
    clientRule.getJobClient().newWorker().jobType("collect-money").handler(defaultHandler).open();
    clientRule
        .getJobClient()
        .newWorker()
        .jobType("fetch-items")
        .handler(
            (client, job) -> {
              client.newCompleteCommand(job).payload("{\"foo\":\"bar\"}").send().join();
            })
        .open();
    clientRule.getJobClient().newWorker().jobType("ship-parcel").handler(defaultHandler).open();

    // then
    waitUntil(
        () ->
            eventRecorder.hasElementInState(
                wfEvent.getActivityId(), WorkflowInstanceState.ELEMENT_COMPLETED));

    final WorkflowInstanceEvent workflowEvent =
        eventRecorder.getElementInState(
            wfEvent.getActivityId(), WorkflowInstanceState.ELEMENT_COMPLETED);
    assertThat(workflowEvent.getPayload()).isEqualTo("{\"foo\":\"bar\"}");
  }

  @Test
  public void shouldCompleteTasksFromMultipleProcesses() throws InterruptedException {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask(
                "task",
                t ->
                    t.zeebeTaskType("foo")
                        .zeebeInput("$.foo", "$.foo")
                        .zeebeOutput("$.foo", "$.foo"))
            .endEvent("end")
            .done();
    deploy(modelInstance);

    // when
    final int instances = 10;
    for (int i = 0; i < instances; i++) {
      clientRule
          .getWorkflowClient()
          .newCreateInstanceCommand()
          .bpmnProcessId("process")
          .latestVersion()
          .payload("{\"foo\":1}")
          .send()
          .join();
    }

    clientRule
        .getJobClient()
        .newWorker()
        .jobType("foo")
        .handler((client, job) -> client.newCompleteCommand(job).payload("{\"foo\":2}").send())
        .open();

    // then
    waitUntil(() -> eventRecorder.getJobEvents(JobState.COMPLETED).size() == instances);
    waitUntil(
        () ->
            eventRecorder
                    .getElementsInState("process", WorkflowInstanceState.ELEMENT_COMPLETED)
                    .size()
                == instances);
  }

  private DeploymentEvent deploy(BpmnModelInstance modelInstance) {
    final DeploymentEvent deploymentEvent =
        clientRule
            .getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(modelInstance, "workflow.bpmn")
            .send()
            .join();
    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());
    return deploymentEvent;
  }
}
