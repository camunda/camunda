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
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.RecordingTaskHandler;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.client.TasksClient;
import io.zeebe.client.WorkflowsClient;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.event.WorkflowInstanceEvent;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class YamlWorkflowTest
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

    @Test
    public void shouldCreateWorkflowInstance()
    {
        // given
        workflowClient.deploy(clientRule.getDefaultTopic())
            .resourceFromClasspath("workflows/simple-workflow.yaml")
            .execute();

        // when
        final WorkflowInstanceEvent workflowInstance = workflowClient.create(clientRule.getDefaultTopic())
                .bpmnProcessId("yaml-workflow")
                .execute();

        // then
        assertThat(workflowInstance.getWorkflowInstanceKey()).isGreaterThan(0);
        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("WORKFLOW_INSTANCE_CREATED")));
    }

    @Test
    public void shouldCompleteWorkflowInstanceWithTask()
    {
        // given
        workflowClient.deploy(clientRule.getDefaultTopic())
            .resourceFromClasspath("workflows/simple-workflow.yaml")
            .execute();

        workflowClient.create(clientRule.getDefaultTopic())
                .bpmnProcessId("yaml-workflow")
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
    public void shouldGetTaskWithHeaders()
    {
        // given
        workflowClient.deploy(clientRule.getDefaultTopic())
            .resourceFromClasspath("workflows/workflow-with-headers.yaml")
            .execute();

        workflowClient.create(clientRule.getDefaultTopic())
                .bpmnProcessId("workflow-headers")
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
        assertThat(taskLockedEvent.getCustomHeaders())
            .containsEntry("foo", "f")
            .containsEntry("bar", "b");
    }

    @Test
    public void shouldCompleteTaskWithPayload()
    {
        // given
        workflowClient.deploy(clientRule.getDefaultTopic())
            .resourceFromClasspath("workflows/workflow-with-mappings.yaml")
            .execute();

        workflowClient.create(clientRule.getDefaultTopic())
                .bpmnProcessId("workflow-mappings")
                .payload("{\"foo\":1}")
                .execute();

        // when
        final RecordingTaskHandler recordingTaskHandler = new RecordingTaskHandler((c, task) -> c
            .complete(task)
            .payload("{\"result\":3}")
            .execute());

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

        waitUntil(() -> eventRecorder.hasWorkflowInstanceEvent(wfInstanceEvent("ACTIVITY_COMPLETED")));

        final WorkflowInstanceEvent workflowEvent = eventRecorder.getSingleWorkflowInstanceEvent(wfInstanceEvent("ACTIVITY_COMPLETED"));
        assertThat(workflowEvent.getPayload()).isEqualTo("{\"result\":3}");
    }

}
