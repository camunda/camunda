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
package org.camunda.bpm.broker.it.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.camunda.bpm.broker.it.ClientRule;
import org.camunda.bpm.broker.it.EmbeddedBrokerRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.cmd.WorkflowDefinition;
import org.camunda.tngp.client.event.Event;
import org.camunda.tngp.client.event.EventsBatch;
import org.camunda.tngp.client.event.TaskInstanceEvent;
import org.camunda.tngp.client.event.WorkflowDefinitionEvent;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class PollEventsTest
{
    private static final int TASK_QUEUE_TOPIC_ID = 0;
    private static final int WORKFLOW_TOPIC_ID = 1;

    private static final int INITIAL_LOG_POSITION = 0;

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldPollNoEventsIfNoneAvailable()
    {
        final TngpClient client = clientRule.getClient();

        final EventsBatch eventsBatch = client.events().poll()
            .startPosition(INITIAL_LOG_POSITION)
            .maxEvents(1)
            .topicId(TASK_QUEUE_TOPIC_ID)
            .execute();

        assertThat(eventsBatch).isNotNull();
        assertThat(eventsBatch.getEvents()).hasSize(0);
    }

    @Test
    public void shouldPollEvent()
    {
        final TngpClient client = clientRule.getClient();

        client.tasks().create()
            .topicId(0)
            .taskType("test")
            .payload("foo")
            .execute();

        final EventsBatch eventsBatch = client.events().poll()
            .startPosition(INITIAL_LOG_POSITION)
            .maxEvents(1)
            .topicId(TASK_QUEUE_TOPIC_ID)
            .execute();

        assertThat(eventsBatch).isNotNull();
        assertThat(eventsBatch.getEvents()).hasSize(1);

        final Event event = eventsBatch.getEvents().get(0);

        assertThat(event.getPosition()).isEqualTo(INITIAL_LOG_POSITION);
        assertThat(event.getRawBuffer()).isNotNull();
    }

    @Test
    public void shouldPollEvents()
    {
        final TngpClient client = clientRule.getClient();

        final Long taskId1 = client.tasks().create()
            .topicId(0)
            .taskType("test")
            .payload("foo")
            .execute();

        final Long taskId2 = client.tasks().create()
                .topicId(0)
                .taskType("test")
                .payload("bar")
                .execute();

        final EventsBatch eventsBatch = client.events().poll()
            .startPosition(INITIAL_LOG_POSITION)
            .maxEvents(2)
            .topicId(TASK_QUEUE_TOPIC_ID)
            .execute();

        final List<TaskInstanceEvent> events = eventsBatch.getTaskInstanceEvents();
        assertThat(events).hasSize(2);

        final TaskInstanceEvent event1 = events.get(0);
        final TaskInstanceEvent event2 = events.get(1);

        assertThat(event1.getPosition()).isEqualTo(INITIAL_LOG_POSITION);
        assertThat(event1.getId()).isEqualTo(taskId1);

        assertThat(event2.getPosition()).isGreaterThan(INITIAL_LOG_POSITION);
        assertThat(event2.getId()).isEqualTo(taskId2);
    }

    @Test
    public void shouldPollEventWithStartPosition()
    {
        final TngpClient client = clientRule.getClient();

        client.tasks().create()
            .topicId(0)
            .taskType("test")
            .payload("foo")
            .execute();

        final Long taskId2 = client.tasks().create()
                .topicId(0)
                .taskType("test")
                .payload("bar")
                .execute();

        EventsBatch eventsBatch = client.events().poll()
            .startPosition(INITIAL_LOG_POSITION)
            .maxEvents(2)
            .topicId(TASK_QUEUE_TOPIC_ID)
            .execute();

        assertThat(eventsBatch.getEvents()).hasSize(2);

        final Event event2 = eventsBatch.getEvents().get(1);

        eventsBatch = client.events().poll()
                .startPosition(event2.getPosition())
                .maxEvents(1)
                .topicId(TASK_QUEUE_TOPIC_ID)
                .execute();

        final List<TaskInstanceEvent> events = eventsBatch.getTaskInstanceEvents();
        assertThat(events).hasSize(1);

        final TaskInstanceEvent event = events.get(0);
        assertThat(event.getId()).isEqualTo(taskId2);
    }

    @Test
    public void shouldPollNotMoreThanMaxEvents()
    {
        final TngpClient client = clientRule.getClient();

        client.tasks().create()
            .topicId(0)
            .taskType("test")
            .payload("foo")
            .execute();

        client.tasks().create()
                .topicId(0)
                .taskType("test")
                .payload("bar")
                .execute();

        final EventsBatch eventsBatch = client.events().poll()
            .startPosition(INITIAL_LOG_POSITION)
            .maxEvents(1)
            .topicId(TASK_QUEUE_TOPIC_ID)
            .execute();

        assertThat(eventsBatch.getEvents()).hasSize(1);
    }

    @Test
    public void shouldPollEventWithStartPositionBetweenEvents()
    {
        final TngpClient client = clientRule.getClient();

        client.tasks().create()
            .topicId(0)
            .taskType("test")
            .payload("foo")
            .execute();

        final Long taskId2 = client.tasks().create()
                .topicId(0)
                .taskType("test")
                .payload("bar")
                .execute();

        final EventsBatch eventsBatch = client.events().poll()
            .startPosition(INITIAL_LOG_POSITION + 1)
            .maxEvents(2)
            .topicId(TASK_QUEUE_TOPIC_ID)
            .execute();

        final List<TaskInstanceEvent> events = eventsBatch.getTaskInstanceEvents();
        assertThat(events).hasSize(1);

        final TaskInstanceEvent event = events.get(0);
        assertThat(event.getId()).isEqualTo(taskId2);
    }

    @Test
    public void shouldPollTaskInstanceEvent()
    {
        final TngpClient client = clientRule.getClient();

        final Long taskId = client.tasks().create()
            .topicId(0)
            .taskType("test")
            .execute();

        final EventsBatch eventsBatch = client.events().poll()
            .startPosition(INITIAL_LOG_POSITION)
            .maxEvents(10)
            .topicId(TASK_QUEUE_TOPIC_ID)
            .execute();

        final List<TaskInstanceEvent> events = eventsBatch.getTaskInstanceEvents();
        assertThat(events).hasSize(1);

        final TaskInstanceEvent event = events.get(0);

        assertThat(event.getPosition()).isEqualTo(INITIAL_LOG_POSITION);
        assertThat(event.getRawBuffer()).isNotNull();

        assertThat(event.getId()).isEqualTo(taskId);
        assertThat(event.getType()).isEqualTo("test");

        assertThat(event.isNew()).isTrue();
        assertThat(event.isLocked()).isFalse();
        assertThat(event.isCompleted()).isFalse();
        assertThat(event.getState()).isEqualTo(TaskInstanceEvent.STATE_NEW);

        assertThat(event.getWorkflowInstanceId()).isNull();
        assertThat(event.getLockOwnerId()).isNull();
        assertThat(event.getLockExpirationTime()).isNull();
    }

    @Test
    public void shouldPollWorkflowDefinitionEvent()
    {
        final TngpClient client = clientRule.getClient();

        final WorkflowDefinition workflowDefinition = client.workflows().deploy()
            .bpmnModelInstance(Bpmn.createExecutableProcess("process-id").startEvent().endEvent().done())
            .execute();

        // this ensures that the workflow definition event is already persistent - see #68
        client.workflows().start()
            .workflowDefinitionId(workflowDefinition.getId())
            .execute();

        final EventsBatch eventsBatch = client.events().poll()
            .startPosition(INITIAL_LOG_POSITION)
            .maxEvents(10)
            .topicId(WORKFLOW_TOPIC_ID)
            .execute();

        final List<WorkflowDefinitionEvent> events = eventsBatch.getWorkflowDefinitionEvents();
        assertThat(events).hasSize(1);

        final WorkflowDefinitionEvent event = events.get(0);

        assertThat(event.getPosition()).isGreaterThanOrEqualTo(INITIAL_LOG_POSITION);
        assertThat(event.getRawBuffer()).isNotNull();

        assertThat(event.getId()).isEqualTo(workflowDefinition.getId());
        assertThat(event.getKey()).isEqualTo("process-id");

        assertThat(event.getResource()).isNotEmpty();
    }


}
