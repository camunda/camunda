/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.broker.it.incident;

import static org.camunda.tngp.broker.it.util.TopicEventRecorder.taskEvent;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.wrap;
import static org.camunda.tngp.test.util.TestUtil.waitUntil;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.broker.it.util.TopicEventRecorder;
import org.camunda.tngp.client.event.EventMetadata;
import org.camunda.tngp.client.event.IncidentEvent;
import org.camunda.tngp.client.event.IncidentEventHandler;
import org.camunda.tngp.client.event.TopicSubscription;
import org.camunda.tngp.client.task.Task;
import org.camunda.tngp.client.task.TaskHandler;
import org.camunda.tngp.client.workflow.cmd.WorkflowInstance;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class IncidentTest
{
    private static final BpmnModelInstance WORKFLOW = wrap(
            Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("failingTask")
            .done())
            .taskDefinition("failingTask", "test", 3)
            .ioMapping("failingTask")
                .input("$.foo", "$.foo")
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

    private IncidentEventRecoder incidentEventRecorder;
    private TopicSubscription incidentTopicSubscription;

    @Before
    public void init()
    {
        incidentEventRecorder = new IncidentEventRecoder();

        incidentTopicSubscription = clientRule.topic().newSubscription()
            .name("incident")
            .startAtHeadOfTopic()
            .incidentEventHandler(incidentEventRecorder)
            .open();
    }

    @After
    public void cleanUp()
    {
        incidentTopicSubscription.close();
    }

    @Test
    public void shouldCreateAndResolveInputMappingIncident()
    {
        // given
        clientRule.workflowTopic().deploy()
            .bpmnModelInstance(WORKFLOW)
            .execute();

        clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        waitUntil(() -> incidentEventRecorder.hasIncident(eventType("CREATED")));

        final IncidentEvent incident = incidentEventRecorder.getSingleIncidentEvent(eventType("CREATED"));

        // when
        clientRule.workflowTopic().updatePayload()
            .workflowInstanceKey(incident.getWorkflowInstanceKey())
            .activityInstanceKey(incident.getActivityInstanceKey())
            .payload(PAYLOAD)
            .execute();

        // then
        waitUntil(() -> eventRecorder.hasTaskEvent(taskEvent("CREATED")));
        waitUntil(() -> incidentEventRecorder.hasIncident(eventType("RESOLVED")));
    }

    @Test
    public void shouldDeleteIncidentWhenWorkflowInstanceIsCanceled()
    {
        // given
        clientRule.workflowTopic().deploy()
            .bpmnModelInstance(WORKFLOW)
            .execute();

        final WorkflowInstance workflowInstance = clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        waitUntil(() -> incidentEventRecorder.hasIncident(eventType("CREATED")));

        // when
        clientRule.workflowTopic().cancel()
            .workflowInstanceKey(workflowInstance.getWorkflowInstanceKey())
            .execute();

        // then
        waitUntil(() -> incidentEventRecorder.hasIncident(eventType("DELETED")));
    }

    @Test
    public void shouldCreateAndResolveTaskIncident()
    {
        // given a workflow instance with an open task
        clientRule.workflowTopic().deploy()
            .bpmnModelInstance(WORKFLOW)
            .execute();

        clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .payload(PAYLOAD)
            .execute();

        // when the task fails until it has no more retries left
        final ControllableTaskHandler taskHandler = new ControllableTaskHandler();
        taskHandler.failTask = true;

        clientRule.taskTopic().newTaskSubscription()
            .taskType("test")
            .lockOwner("owner")
            .lockTime(Duration.ofMinutes(5))
            .handler(taskHandler)
            .open();

        // then an incident is created
        waitUntil(() -> incidentEventRecorder.hasIncident(eventType("CREATED")));

        // when the task retries are increased
        taskHandler.failTask = false;

        clientRule.taskTopic().updateRetries()
            .taskKey(taskHandler.task.getKey())
            .taskType(taskHandler.task.getType())
            .headers(taskHandler.task.getHeaders())
            .payload(taskHandler.task.getPayload())
            .retries(3)
            .execute();

        // then the incident is deleted
        waitUntil(() -> incidentEventRecorder.hasIncident(eventType("DELETED")));
    }

    private static final class IncidentEventRecoder implements IncidentEventHandler
    {
        private final List<IncidentEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void handle(EventMetadata metadata, IncidentEvent event) throws Exception
        {
            events.add(event);
        }

        public IncidentEvent getSingleIncidentEvent(Predicate<IncidentEvent> filter)
        {
            return events.stream().filter(filter).findFirst().orElseThrow(() -> new AssertionError("no event found"));
        }

        public boolean hasIncident(Predicate<IncidentEvent> filter)
        {
            return events.stream().anyMatch(filter);
        }
    }

    private static Predicate<IncidentEvent> eventType(String type)
    {
        return incident -> incident.getEventType().equals(type);
    }

    private static final class ControllableTaskHandler implements TaskHandler
    {
        boolean failTask = false;
        Task task;

        @Override
        public void handle(Task task)
        {
            this.task = task;

            if (failTask)
            {
                throw new RuntimeException("expected failure");
            }
            else
            {
                task.complete();
            }
        }
    }

}
