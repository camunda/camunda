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

import static io.zeebe.broker.it.util.TopicEventRecorder.taskEvent;
import static io.zeebe.broker.it.util.TopicEventRecorder.wfInstanceEvent;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.wrap;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.RecordingTaskHandler;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.ZeebeModelInstance;
import io.zeebe.client.TasksClient;
import io.zeebe.client.WorkflowsClient;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.event.WorkflowInstanceEvent;

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

    private WorkflowsClient workflowClient;
    private TasksClient taskClient;

    @Before
    public void init()
    {
        workflowClient = clientRule.workflows();
        taskClient = clientRule.tasks();
    }

    private static ZeebeModelInstance oneTaskProcess(String taskType)
    {
        return wrap(Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task")
            .endEvent("end")
            .done())
        .taskDefinition("task", taskType, 3);
    }

    @Test
    public void shouldStartWorkflowInstanceWithServiceTask()
    {
        // given
        workflowClient.deploy(clientRule.getDefaultTopic())
            .bpmnModelInstance(oneTaskProcess("foo"))
            .execute();

        // when
        final WorkflowInstanceEvent workflowInstance = workflowClient.create(clientRule.getDefaultTopic())
                .bpmnProcessId("process")
                .execute();

        // then
        assertThat(workflowInstance.getWorkflowInstanceKey()).isGreaterThan(0);
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("WORKFLOW_INSTANCE_CREATED")));
    }

    @Test
    public void shouldLockServiceTask()
    {
        // given
        final Map<String, String> taskHeaders = new HashMap<>();
        taskHeaders.put("cust1", "a");
        taskHeaders.put("cust2", "b");

        workflowClient.deploy(clientRule.getDefaultTopic())
            .bpmnModelInstance(
                    oneTaskProcess("foo")
                        .taskHeaders("task", taskHeaders))
            .execute();

        final WorkflowInstanceEvent workflowInstance = workflowClient.create(clientRule.getDefaultTopic())
                .bpmnProcessId("process")
                .execute();

        // when
        final RecordingTaskHandler recordingTaskHandler = new RecordingTaskHandler();

        taskClient.newTaskSubscription(clientRule.getDefaultTopic())
            .taskType("foo")
            .lockOwner("owner")
            .lockTime(Duration.ofMinutes(5))
            .handler(recordingTaskHandler)
            .open();

        // then
        waitUntil(() -> recordingTaskHandler.getHandledTasks().size() >= 1);

        assertThat(recordingTaskHandler.getHandledTasks()).hasSize(1);

        final WorkflowInstanceEvent activityInstance = eventRecorder.getSingleWorkflowInstanceEvent(e -> "ACTIVITY_ACTIVATED".equals(e.getState()));

        final TaskEvent taskLockedEvent = recordingTaskHandler.getHandledTasks().get(0);
        assertThat(taskLockedEvent.getHeaders()).containsOnly(
            entry("bpmnProcessId", "process"),
            entry("workflowDefinitionVersion", 1),
            entry("workflowKey", workflowInstance.getWorkflowKey()),
            entry("workflowInstanceKey", workflowInstance.getWorkflowInstanceKey()),
            entry("activityId", "task"),
            entry("activityInstanceKey", activityInstance.getMetadata().getKey()));

        assertThat(taskLockedEvent.getCustomHeaders()).containsOnly(
            entry("cust1", "a"),
            entry("cust2", "b"));
    }

    @Test
    public void shouldCompleteServiceTask()
    {
        // given
        workflowClient.deploy(clientRule.getDefaultTopic())
            .bpmnModelInstance(oneTaskProcess("foo"))
            .execute();

        workflowClient.create(clientRule.getDefaultTopic())
                .bpmnProcessId("process")
                .execute();

        // when
        taskClient.newTaskSubscription(clientRule.getDefaultTopic())
            .taskType("foo")
            .lockOwner("owner")
            .lockTime(Duration.ofMinutes(5))
            .handler((c, t) -> c.complete(t).withoutPayload().execute())
            .open();

        // then
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("COMPLETED")));
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("WORKFLOW_INSTANCE_COMPLETED")));
    }

    @Test
    public void shouldMapPayloadIntoTask()
    {
        // given
        workflowClient.deploy(clientRule.getDefaultTopic())
            .bpmnModelInstance(
                    oneTaskProcess("foo")
                        .ioMapping("task")
                            .input("$.foo", "$.bar")
                            .done())
            .execute();

        workflowClient.create(clientRule.getDefaultTopic())
                .bpmnProcessId("process")
                .payload("{\"foo\":1}")
                .execute();

        // when
        final RecordingTaskHandler recordingTaskHandler = new RecordingTaskHandler();

        taskClient.newTaskSubscription(clientRule.getDefaultTopic())
            .taskType("foo")
            .lockOwner("owner")
            .lockTime(Duration.ofMinutes(5))
            .handler(recordingTaskHandler)
            .open();

        // then
        waitUntil(() -> recordingTaskHandler.getHandledTasks().size() >= 1);

        final TaskEvent taskLockedEvent = recordingTaskHandler.getHandledTasks().get(0);
        assertThat(taskLockedEvent.getPayload()).isEqualTo("{\"bar\":1}");
    }

    @Test
    public void shouldMapPayloadFromTask()
    {
        // given
        workflowClient.deploy(clientRule.getDefaultTopic())
            .bpmnModelInstance(
                    oneTaskProcess("foo")
                        .ioMapping("task")
                            .output("$.foo", "$.bar")
                            .done())
            .execute();

        workflowClient.create(clientRule.getDefaultTopic())
                .bpmnProcessId("process")
                .execute();

        // when
        taskClient.newTaskSubscription(clientRule.getDefaultTopic())
            .taskType("foo")
            .lockOwner("owner")
            .lockTime(Duration.ofMinutes(5))
            .handler((c, t) ->  c.complete(t).payload("{\"foo\":2}").execute())
            .open();

        // then
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("ACTIVITY_COMPLETED")));

        final WorkflowInstanceEvent workflowEvent = eventRecorder.getSingleWorkflowInstanceEvent(wfInstanceEvent("ACTIVITY_COMPLETED"));
        assertThat(workflowEvent.getPayload()).isEqualTo("{\"bar\":2}");
    }

    @Test
    public void shouldModifyPayloadInTask()
    {
        // given
        workflowClient.deploy(clientRule.getDefaultTopic())
            .bpmnModelInstance(
                    oneTaskProcess("foo")
                        .ioMapping("task")
                            .input("$.foo", "$.foo")
                            .output("$.foo", "$.foo")
                            .done())
            .execute();

        workflowClient.create(clientRule.getDefaultTopic())
                .bpmnProcessId("process")
                .payload("{\"foo\":1}")
                .execute();

        // when
        taskClient.newTaskSubscription(clientRule.getDefaultTopic())
            .taskType("foo")
            .lockOwner("owner")
            .lockTime(Duration.ofMinutes(5))
            .handler((c, t) ->
            {
                final String modifiedPayload = t.getPayload().replaceAll("1", "2");
                c.complete(t).payload(modifiedPayload).execute();
            })
            .open();

        // then
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("ACTIVITY_COMPLETED")));

        final WorkflowInstanceEvent workflowEvent = eventRecorder.getSingleWorkflowInstanceEvent(wfInstanceEvent("ACTIVITY_COMPLETED"));
        assertThat(workflowEvent.getPayload()).isEqualTo("{\"foo\":2}");
    }

    @Test
    public void shouldCompleteTasksFromMultipleProcesses()
    {
        // given
        workflowClient.deploy(clientRule.getDefaultTopic())
            .bpmnModelInstance(
                    oneTaskProcess("foo")
                        .ioMapping("task")
                            .input("$.foo", "$.foo")
                            .output("$.foo", "$.foo")
                            .done())
            .execute();

        // when
        final int instances = 10;
        for (int i = 0; i < instances; i++)
        {
            workflowClient.create(clientRule.getDefaultTopic())
                .bpmnProcessId("process")
                .payload("{\"foo\":1}")
                .execute();
        }

        taskClient.newTaskSubscription(clientRule.getDefaultTopic())
            .taskType("foo")
            .lockOwner("test")
            .lockTime(Duration.ofMinutes(5))
            .handler((c, t) ->  c.complete(t).payload("{\"foo\":2}").execute())
            .open();

        // then
        waitUntil(() -> eventRecorder.getTaskEvents(taskEvent("COMPLETED")).size() == instances);
        waitUntil(() -> eventRecorder.getWorkflowInstanceEvents(wfInstanceEvent("WORKFLOW_INSTANCE_COMPLETED")).size() == instances);
    }

}
