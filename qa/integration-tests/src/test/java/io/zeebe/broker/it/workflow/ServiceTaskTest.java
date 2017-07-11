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
import static io.zeebe.broker.it.util.TopicEventRecorder.wfEvent;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.wrap;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.RecordingTaskHandler;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.broker.it.util.TopicEventRecorder.ReceivedWorkflowEvent;
import io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.ZeebeModelInstance;
import io.zeebe.client.TaskTopicClient;
import io.zeebe.client.WorkflowTopicClient;
import io.zeebe.client.task.Task;
import io.zeebe.client.workflow.cmd.WorkflowInstance;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.junit.Before;
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

    private WorkflowTopicClient workflowClient;
    private TaskTopicClient taskClient;

    @Before
    public void init()
    {
        workflowClient = clientRule.workflowTopic();
        taskClient = clientRule.taskTopic();
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
        workflowClient.deploy()
            .bpmnModelInstance(oneTaskProcess("foo"))
            .execute();

        // when
        final WorkflowInstance workflowInstance = workflowClient.create()
                .bpmnProcessId("process")
                .execute();

        // then
        assertThat(workflowInstance.getWorkflowInstanceKey()).isGreaterThan(0);
        waitUntil(() -> eventRecorder.hasWorkflowEvent(wfEvent("WORKFLOW_INSTANCE_CREATED")));
    }

    @Test
    public void shouldLockServiceTask()
    {
        // given
        final Map<String, String> taskHeaders = new HashMap<>();
        taskHeaders.put("cust1", "a");
        taskHeaders.put("cust2", "b");

        workflowClient.deploy()
            .bpmnModelInstance(
                    oneTaskProcess("foo")
                        .taskHeaders("task", taskHeaders))
            .execute();

        final WorkflowInstance workflowInstance = workflowClient.create()
                .bpmnProcessId("process")
                .execute();

        // when
        final RecordingTaskHandler recordingTaskHandler = new RecordingTaskHandler();

        taskClient.newTaskSubscription()
            .taskType("foo")
            .lockOwner("owner")
            .lockTime(Duration.ofMinutes(5))
            .handler(recordingTaskHandler)
            .open();

        // then
        waitUntil(() -> recordingTaskHandler.getHandledTasks().size() >= 1);

        assertThat(recordingTaskHandler.getHandledTasks()).hasSize(1);

        final Task taskLockedEvent = recordingTaskHandler.getHandledTasks().get(0);
        assertThat(taskLockedEvent.getHeaders())
            .containsEntry("bpmnProcessId", "process")
            .containsEntry("workflowDefinitionVersion", 1)
            .containsEntry("workflowInstanceKey", workflowInstance.getWorkflowInstanceKey())
            .containsEntry("activityId", "task")
            .containsEntry("cust1", "a")
            .containsEntry("cust2", "b")
            .containsKey("activityInstanceKey")
            .containsKey("customHeaders");
    }

    @Test
    public void shouldCompleteServiceTask()
    {
        // given
        workflowClient.deploy()
            .bpmnModelInstance(oneTaskProcess("foo"))
            .execute();

        workflowClient.create()
                .bpmnProcessId("process")
                .execute();

        // when
        taskClient.newTaskSubscription()
            .taskType("foo")
            .lockOwner("owner")
            .lockTime(Duration.ofMinutes(5))
            .handler(Task::complete)
            .open();

        // then
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("COMPLETED")));
        waitUntil(() -> eventRecorder.hasWorkflowEvent(wfEvent("WORKFLOW_INSTANCE_COMPLETED")));
    }

    @Test
    public void shouldMapPayloadIntoTask()
    {
        // given
        workflowClient.deploy()
            .bpmnModelInstance(
                    oneTaskProcess("foo")
                        .ioMapping("task")
                            .input("$.foo", "$.bar")
                            .done())
            .execute();

        workflowClient.create()
                .bpmnProcessId("process")
                .payload("{\"foo\":1}")
                .execute();

        // when
        final RecordingTaskHandler recordingTaskHandler = new RecordingTaskHandler();

        taskClient.newTaskSubscription()
            .taskType("foo")
            .lockOwner("owner")
            .lockTime(Duration.ofMinutes(5))
            .handler(recordingTaskHandler)
            .open();

        // then
        waitUntil(() -> recordingTaskHandler.getHandledTasks().size() >= 1);

        final Task taskLockedEvent = recordingTaskHandler.getHandledTasks().get(0);
        assertThat(taskLockedEvent.getPayload()).isEqualTo("{\"bar\":1}");
    }

    @Test
    public void shouldMapPayloadFromTask()
    {
        // given
        workflowClient.deploy()
            .bpmnModelInstance(
                    oneTaskProcess("foo")
                        .ioMapping("task")
                            .output("$.foo", "$.bar")
                            .done())
            .execute();

        workflowClient.create()
                .bpmnProcessId("process")
                .execute();

        // when
        taskClient.newTaskSubscription()
            .taskType("foo")
            .lockOwner("owner")
            .lockTime(Duration.ofMinutes(5))
            .handler(task ->
            {
                task.setPayload("{\"foo\":2}");
                task.complete();
            })
            .open();

        // then
        waitUntil(() -> eventRecorder.hasWorkflowEvent(wfEvent("ACTIVITY_COMPLETED")));

        final ReceivedWorkflowEvent workflowEvent = eventRecorder.getSingleWorkflowEvent(wfEvent("ACTIVITY_COMPLETED"));
        assertThat(workflowEvent.getEvent().getPayload()).isEqualTo("{\"bar\":2}");
    }

    @Test
    public void shouldModifyPayloadInTask()
    {
        // given
        workflowClient.deploy()
            .bpmnModelInstance(
                    oneTaskProcess("foo")
                        .ioMapping("task")
                            .input("$.foo", "$.foo")
                            .output("$.foo", "$.foo")
                            .done())
            .execute();

        workflowClient.create()
                .bpmnProcessId("process")
                .payload("{\"foo\":1}")
                .execute();

        // when
        taskClient.newTaskSubscription()
            .taskType("foo")
            .lockOwner("owner")
            .lockTime(Duration.ofMinutes(5))
            .handler(task ->
            {
                final String modifiedPayload = task.getPayload().replaceAll("1", "2");
                task.setPayload(modifiedPayload);
                task.complete();
            })
            .open();

        // then
        waitUntil(() -> eventRecorder.hasWorkflowEvent(wfEvent("ACTIVITY_COMPLETED")));

        final ReceivedWorkflowEvent workflowEvent = eventRecorder.getSingleWorkflowEvent(wfEvent("ACTIVITY_COMPLETED"));
        assertThat(workflowEvent.getEvent().getPayload()).isEqualTo("{\"foo\":2}");
    }

    @Test
    public void shouldCompleteTasksFromMultipleProcesses()
    {
        // given
        workflowClient.deploy()
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
            workflowClient.create()
                .bpmnProcessId("process")
                .payload("{\"foo\":1}")
                .execute();
        }

        taskClient.newTaskSubscription()
            .taskType("foo")
            .lockOwner("test")
            .lockTime(Duration.ofMinutes(5))
            .handler(task ->
            {
                task.setPayload("{\"foo\":2}");
                task.complete();
            })
            .open();

        // then
        waitUntil(() -> eventRecorder.getTaskEvents(taskEvent("COMPLETED")).size() == instances);
        waitUntil(() -> eventRecorder.getWorkflowEvents(wfEvent("WORKFLOW_INSTANCE_COMPLETED")).size() == instances);
    }

}
