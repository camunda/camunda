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
package io.zeebe.broker.it.incident;

import static io.zeebe.broker.it.util.TopicEventRecorder.incidentEvent;
import static io.zeebe.broker.it.util.TopicEventRecorder.taskEvent;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.wrap;
import static io.zeebe.test.util.TestUtil.waitUntil;

import java.time.Duration;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.client.event.IncidentEvent;
import io.zeebe.client.task.Task;
import io.zeebe.client.task.TaskHandler;
import io.zeebe.client.workflow.cmd.WorkflowInstance;
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

        waitUntil(() -> eventRecorder.hasIncidentEvent(incidentEvent("CREATED")));

        final IncidentEvent incident = eventRecorder.getSingleIncidentEvent(incidentEvent("CREATED")).getEvent();

        // when
        clientRule.workflowTopic().updatePayload()
            .workflowInstanceKey(incident.getWorkflowInstanceKey())
            .activityInstanceKey(incident.getActivityInstanceKey())
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
        clientRule.workflowTopic().deploy()
            .bpmnModelInstance(WORKFLOW)
            .execute();

        final WorkflowInstance workflowInstance = clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        waitUntil(() -> eventRecorder.hasIncidentEvent(incidentEvent("CREATED")));

        // when
        clientRule.workflowTopic().cancel()
            .workflowInstanceKey(workflowInstance.getWorkflowInstanceKey())
            .execute();

        // then
        waitUntil(() -> eventRecorder.hasIncidentEvent(incidentEvent("DELETED")));
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
        waitUntil(() -> eventRecorder.hasIncidentEvent(incidentEvent("CREATED")));

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
        waitUntil(() -> eventRecorder.hasIncidentEvent(incidentEvent("DELETED")));
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
