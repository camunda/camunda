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

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.it.util.TopicEventRecorder.taskEvent;
import static org.camunda.tngp.broker.workflow.graph.transformer.TngpExtensions.wrap;
import static org.camunda.tngp.test.util.TestUtil.waitUntil;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.broker.it.util.TopicEventRecorder;
import org.camunda.tngp.client.event.*;
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
            .handler(incidentEventRecorder)
            .open();
    }

    @After
    public void cleanUp()
    {
        incidentTopicSubscription.close();
    }

    @Test
    public void shouldResolveInputMappingIncident()
    {
        // given
        clientRule.workflowTopic().deploy()
            .bpmnModelInstance(WORKFLOW)
            .execute();

        final WorkflowInstance workflowInstance = clientRule.workflowTopic().create()
            .bpmnProcessId("process")
            .execute();

        waitUntil(() -> incidentEventRecorder.getIncidentKey() > 0);
        waitUntil(() -> incidentEventRecorder.getEventTypes().contains("CREATED"));

        // when
        clientRule.workflowTopic().updatePayload()
            .workflowInstanceKey(workflowInstance.getWorkflowInstanceKey())
            .activityInstanceKey(incidentEventRecorder.getActivityInstanceKey())
            .payload(PAYLOAD)
            .execute();

        // then
        assertThat(eventRecorder.hasTaskEvent(taskEvent("CREATED")));
        waitUntil(() -> incidentEventRecorder.getEventTypes().contains("RESOLVED"));
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

        waitUntil(() -> incidentEventRecorder.getIncidentKey() > 0);
        waitUntil(() -> incidentEventRecorder.getEventTypes().contains("CREATED"));

        // when
        clientRule.workflowTopic().cancel()
            .workflowInstanceKey(workflowInstance.getWorkflowInstanceKey())
            .execute();

        // then
        waitUntil(() -> incidentEventRecorder.getEventTypes().contains("DELETED"));
    }

    @Test
    public void shouldCreateIncidentWhenTaskHasNoRetriesLeft()
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
        waitUntil(() -> incidentEventRecorder.getIncidentKey() > 0);
        waitUntil(() -> incidentEventRecorder.getEventTypes().contains("CREATED"));

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
        waitUntil(() -> incidentEventRecorder.getEventTypes().contains("DELETED"));
    }

    private static final class IncidentEventRecoder implements TopicEventHandler
    {
        private static final Pattern EVENT_TYPE_PATTERN = Pattern.compile("\"eventType\":\"(\\w+)\"");
        private static final Pattern ACTIVITY_INSTANCE_KEY_PATTERN = Pattern.compile("\"activityInstanceKey\":(\\d+)");

        private long incidentKey = -1;
        private long activityInstanceKey = -1;
        private List<String> eventTypes = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void handle(EventMetadata metadata, TopicEvent event) throws Exception
        {
            if (metadata.getEventType() == TopicEventType.INCIDENT)
            {
                incidentKey = metadata.getEventKey();

                final Matcher typeMatcher = EVENT_TYPE_PATTERN.matcher(event.getJson());
                if (typeMatcher.find())
                {
                    final String eventType = typeMatcher.group(1);
                    eventTypes.add(eventType);
                }

                final Matcher activityMatcher = ACTIVITY_INSTANCE_KEY_PATTERN.matcher(event.getJson());
                if (activityMatcher.find())
                {
                    final String key = activityMatcher.group(1);
                    activityInstanceKey = Long.valueOf(key);
                }
            }
        }

        public long getIncidentKey()
        {
            return incidentKey;
        }

        public List<String> getEventTypes()
        {
            return Collections.unmodifiableList(eventTypes);
        }

        public long getActivityInstanceKey()
        {
            return activityInstanceKey;
        }
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
