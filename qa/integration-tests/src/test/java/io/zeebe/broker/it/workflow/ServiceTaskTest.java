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

import java.util.HashMap;
import java.util.Map;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.RecordingJobHandler;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.events.JobEvent.JobState;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.events.WorkflowInstanceEvent.WorkflowInstanceState;
import io.zeebe.model.bpmn.Bpmn;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class ServiceTaskTest
{

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientRule clientRule = new ClientRule();
    public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule);

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule)
        .around(eventRecorder);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldCreateWorkflowInstanceWithServiceTask()
    {
        // given
        clientRule.getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(Bpmn.createExecutableWorkflow("process")
                   .startEvent("start")
                   .serviceTask("task", t -> t.taskType("foo"))
                   .endEvent("end")
                   .done(),
                   "workflow.bpmn")
            .send()
            .join();

        // when
        final WorkflowInstanceEvent workflowInstance = clientRule.getWorkflowClient()
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
    public void shouldLockServiceTask()
    {
        // given
        final Map<String, String> customHeaders = new HashMap<>();
        customHeaders.put("cust1", "a");
        customHeaders.put("cust2", "b");

        clientRule.getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(Bpmn.createExecutableWorkflow("process")
                   .startEvent("start")
                   .serviceTask("task", t -> t.taskType("foo")
                                .taskHeader("cust1", "a")
                                .taskHeader("cust2", "b"))
                   .endEvent("end")
                   .done(),
                   "workflow.bpmn")
            .send()
            .join();

        final WorkflowInstanceEvent workflowInstance = clientRule.getWorkflowClient()
                .newCreateInstanceCommand()
                .bpmnProcessId("process")
                .latestVersion()
                .send()
                .join();

        // when
        final RecordingJobHandler recordingJobHandler = new RecordingJobHandler();

        clientRule.getSubscriptionClient()
            .newJobSubscription()
            .jobType("foo")
            .handler(recordingJobHandler)
            .open();

        // then
        waitUntil(() -> recordingJobHandler.getHandledJobs().size() >= 1);

        assertThat(recordingJobHandler.getHandledJobs()).hasSize(1);

        final WorkflowInstanceEvent activityInstance = eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_ACTIVATED);

        final JobEvent jobEvent = recordingJobHandler.getHandledJobs().get(0);
        assertThat(jobEvent.getHeaders()).containsOnly(
            entry("bpmnProcessId", "process"),
            entry("workflowDefinitionVersion", 1),
            entry("workflowKey", (int) workflowInstance.getWorkflowKey()),
            entry("workflowInstanceKey", workflowInstance.getWorkflowInstanceKey()),
            entry("activityId", "task"),
            entry("activityInstanceKey", activityInstance.getMetadata().getKey()));

        assertThat(jobEvent.getCustomHeaders()).containsOnly(
            entry("cust1", "a"),
            entry("cust2", "b"));
    }

    @Test
    public void shouldCompleteServiceTask()
    {
        // given
        clientRule.getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(Bpmn.createExecutableWorkflow("process")
                   .startEvent("start")
                   .serviceTask("task", t -> t.taskType("foo"))
                   .endEvent("end")
                   .done(),
                   "workflow.bpmn")
            .send()
            .join();

        clientRule.getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

        // when
        clientRule.getSubscriptionClient()
            .newJobSubscription()
            .jobType("foo")
            .handler((client, job) -> client.newCompleteCommand(job).send())
            .open();

        // then
        waitUntil(() -> eventRecorder.hasJobEvent(JobState.COMPLETED));
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.COMPLETED));
    }

    @Test
    public void shouldMapPayloadIntoTask()
    {
        // given
        clientRule.getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(
                    Bpmn.createExecutableWorkflow("process")
                   .startEvent("start")
                   .serviceTask("task", t -> t.taskType("foo")
                       .input("$.foo", "$.bar"))
                   .endEvent("end")
                   .done(),
                   "workflow.bpmn")
            .send()
            .join();

        clientRule.getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .payload("{\"foo\":1}")
            .send()
            .join();

        // when
        final RecordingJobHandler recordingJobHandler = new RecordingJobHandler();

        clientRule.getSubscriptionClient()
            .newJobSubscription()
            .jobType("foo")
            .handler(recordingJobHandler)
            .open();

        // then
        waitUntil(() -> recordingJobHandler.getHandledJobs().size() >= 1);

        final JobEvent jobEvent = recordingJobHandler.getHandledJobs().get(0);
        assertThat(jobEvent.getPayload()).isEqualTo("{\"bar\":1}");
    }

    @Test
    public void shouldMapPayloadFromTask()
    {
        // given
        clientRule.getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(
                    Bpmn.createExecutableWorkflow("process")
                   .startEvent("start")
                   .serviceTask("task", t -> t.taskType("foo")
                       .output("$.foo", "$.bar"))
                   .endEvent("end")
                   .done(),
                   "workflow.bpmn")
            .send()
            .join();

        clientRule.getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

        // when
        clientRule.getSubscriptionClient()
            .newJobSubscription()
            .jobType("foo")
            .handler((client, job) ->  client.newCompleteCommand(job).payload("{\"foo\":2}").send())
            .open();

        // then
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_COMPLETED));

        final WorkflowInstanceEvent workflowEvent = eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_COMPLETED);
        assertThat(workflowEvent.getPayload()).isEqualTo("{\"bar\":2}");
    }

    @Test
    public void shouldModifyPayloadInTask()
    {
        // given
        clientRule.getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(Bpmn.createExecutableWorkflow("process")
                   .startEvent("start")
                   .serviceTask("task", t -> t.taskType("foo")
                       .input("$.foo", "$.foo")
                       .output("$.foo", "$.foo"))
                   .endEvent("end")
                   .done(),
                   "workflow.bpmn")
            .send()
            .join();

        clientRule.getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .payload("{\"foo\":1}")
            .send()
            .join();

        // when
        clientRule.getSubscriptionClient()
            .newJobSubscription()
            .jobType("foo")
            .handler((client, job) ->
            {
                final String modifiedPayload = job.getPayload().replaceAll("1", "2");
                client.newCompleteCommand(job).payload(modifiedPayload).send();
            })
            .open();

        // then
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_COMPLETED));

        final WorkflowInstanceEvent workflowEvent = eventRecorder.getSingleWorkflowInstanceEvent(WorkflowInstanceState.ACTIVITY_COMPLETED);
        assertThat(workflowEvent.getPayload()).isEqualTo("{\"foo\":2}");
    }

    @Test
    public void shouldCompleteTasksFromMultipleProcesses() throws InterruptedException
    {
        // given
        clientRule.getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(Bpmn.createExecutableWorkflow("process")
                       .startEvent("start")
                       .serviceTask("task", t -> t.taskType("foo")
                       .input("$.foo", "$.foo")
                       .output("$.foo", "$.foo"))
                       .endEvent("end")
                       .done(),
                       "workflow.bpmn")
            .send()
            .join();

        // when
        final int instances = 10;
        for (int i = 0; i < instances; i++)
        {
            clientRule.getWorkflowClient()
                .newCreateInstanceCommand()
                .bpmnProcessId("process")
                .latestVersion()
                .payload("{\"foo\":1}")
                .send()
                .join();
        }

        clientRule.getSubscriptionClient()
            .newJobSubscription()
            .jobType("foo")
            .handler((client, job) -> client.newCompleteCommand(job).payload("{\"foo\":2}").send())
            .open();

        // then
        waitUntil(() -> eventRecorder.getJobEvents(JobState.COMPLETED).size() == instances);
        waitUntil(() -> eventRecorder.getWorkflowInstanceEvents(WorkflowInstanceState.COMPLETED).size() == instances);
    }

}
