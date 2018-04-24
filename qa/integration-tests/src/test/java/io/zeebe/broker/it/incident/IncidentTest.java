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
package io.zeebe.broker.it.incident;

import static io.zeebe.broker.it.util.TopicEventRecorder.incidentEvent;
import static io.zeebe.broker.it.util.TopicEventRecorder.taskEvent;
import static io.zeebe.test.util.TestUtil.waitUntil;

import java.time.Duration;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.client.TasksClient;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.event.WorkflowInstanceEvent;
import io.zeebe.client.impl.job.JobHandler;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class IncidentTest
{
    private static final WorkflowDefinition WORKFLOW = Bpmn.createExecutableWorkflow("process")
            .startEvent()
            .serviceTask("failingTask", t -> t.taskType("test")
                         .input("$.foo", "$.foo"))
            .done();

    private static final String PAYLOAD = "{\"foo\": \"bar\"}";

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientRule clientRule = new ClientRule();
    public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule);

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule)
        .around(eventRecorder);

    @Test
    public void shouldCreateAndResolveInputMappingIncident()
    {
        // given
        clientRule.workflows().deploy(clientRule.getDefaultTopic())
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .execute();

        clientRule.workflows().create(clientRule.getDefaultTopic())
            .bpmnProcessId("process")
            .execute();

        waitUntil(() -> eventRecorder.hasIncidentEvent(incidentEvent("CREATED")));

        final WorkflowInstanceEvent activityInstanceEvent =
                eventRecorder.getSingleWorkflowInstanceEvent(w -> "ACTIVITY_READY".equals(w.getState()));

        // when
        clientRule.workflows().updatePayload(activityInstanceEvent)
            .payload(PAYLOAD)
            .execute();

        // then
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("CREATED")));
        waitUntil(() -> eventRecorder.hasIncidentEvent(incidentEvent("RESOLVED")));
    }

    @Test
    public void shouldDeleteIncidentWhenWorkflowInstanceIsCanceled()
    {
        // given
        clientRule.workflows().deploy(clientRule.getDefaultTopic())
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .execute();

        final WorkflowInstanceEvent workflowInstance = clientRule.workflows().create(clientRule.getDefaultTopic())
            .bpmnProcessId("process")
            .execute();

        waitUntil(() -> eventRecorder.hasIncidentEvent(incidentEvent("CREATED")));

        // when
        clientRule.workflows().cancel(workflowInstance).execute();

        // then
        waitUntil(() -> eventRecorder.hasIncidentEvent(incidentEvent("DELETED")));
    }

    @Test
    public void shouldCreateAndResolveTaskIncident()
    {
        // given a workflow instance with an open task
        clientRule.workflows().deploy(clientRule.getDefaultTopic())
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .execute();

        clientRule.workflows().create(clientRule.getDefaultTopic())
            .bpmnProcessId("process")
            .payload(PAYLOAD)
            .execute();

        // when the task fails until it has no more retries left
        final ControllableTaskHandler taskHandler = new ControllableTaskHandler();
        taskHandler.failTask = true;

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopic())
            .taskType("test")
            .lockOwner("owner")
            .lockTime(Duration.ofMinutes(5))
            .handler(taskHandler)
            .open();

        // then an incident is created
        waitUntil(() -> eventRecorder.hasIncidentEvent(incidentEvent("CREATED")));

        // when the task retries are increased
        taskHandler.failTask = false;

        final TaskEvent task = taskHandler.task;

        clientRule.tasks().updateRetries(task)
            .retries(3)
            .execute();

        // then the incident is deleted
        waitUntil(() -> eventRecorder.hasIncidentEvent(incidentEvent("DELETED")));
    }

    private static final class ControllableTaskHandler implements JobHandler
    {
        boolean failTask = false;
        TaskEvent task;

        @Override
        public void handle(TasksClient client, TaskEvent task)
        {
            this.task = task;

            if (failTask)
            {
                throw new RuntimeException("expected failure");
            }
            else
            {
                client.complete(task).withoutPayload().execute();
            }
        }
    }

}
