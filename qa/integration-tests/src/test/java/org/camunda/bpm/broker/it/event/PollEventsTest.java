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
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;

import org.agrona.DirectBuffer;
import org.camunda.bpm.broker.it.ClientRule;
import org.camunda.bpm.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.broker.log.Template;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.taskqueue.TaskInstanceReader;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.event.Event;
import org.camunda.tngp.client.event.EventsBatch;
import org.camunda.tngp.taskqueue.data.MessageHeaderDecoder;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class PollEventsTest
{
    private static final int TASK_QUEUE_TOPIC_ID = 0;
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

        final Long taskId = client.tasks().create()
            .taskQueueId(0)
            .taskType("test")
            .payload("foo")
            .execute();

        final EventsBatch eventsBatch = client.events().poll()
            .startPosition(0)
            .maxEvents(1)
            .topicId(TASK_QUEUE_TOPIC_ID)
            .execute();

        assertThat(eventsBatch).isNotNull();
        assertThat(eventsBatch.getEvents()).hasSize(1);

        final Event event = eventsBatch.getEvents().get(0);

        assertThat(event.getPosition()).isEqualTo(INITIAL_LOG_POSITION);
        assertThat(event.getEventLength()).isGreaterThan(0);

        final DirectBuffer eventBuffer = event.getEventBuffer();
        final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
        messageHeaderDecoder.wrap(eventBuffer, 0);

        assertThat(messageHeaderDecoder.templateId()).isEqualTo(Templates.TASK_INSTANCE.id());

        final Template<TaskInstanceReader> template = Templates.getTemplate(messageHeaderDecoder.templateId());
        assertThat(template).isNotNull();

        final TaskInstanceReader reader = template.newReader();
        reader.wrap(eventBuffer, 0, eventBuffer.capacity());

        assertThat(reader.id()).isEqualTo(taskId);
        assertThatBuffer(reader.getTaskType()).hasBytes("test".getBytes());
        assertThatBuffer(reader.getPayload()).hasBytes("foo".getBytes());
    }

    @Test
    public void shouldPollEvents()
    {
        final TngpClient client = clientRule.getClient();

        final Long taskId1 = client.tasks().create()
            .taskQueueId(0)
            .taskType("test")
            .payload("foo")
            .execute();

        final Long taskId2 = client.tasks().create()
                .taskQueueId(0)
                .taskType("test")
                .payload("bar")
                .execute();

        final EventsBatch eventsBatch = client.events().poll()
            .startPosition(INITIAL_LOG_POSITION)
            .maxEvents(2)
            .topicId(TASK_QUEUE_TOPIC_ID)
            .execute();

        assertThat(eventsBatch.getEvents()).hasSize(2);

        final Event event1 = eventsBatch.getEvents().get(0);
        final Event event2 = eventsBatch.getEvents().get(1);

        final Template<TaskInstanceReader> template = Templates.getTemplate(Templates.TASK_INSTANCE.id());
        final TaskInstanceReader reader = template.newReader();

        DirectBuffer buffer = event1.getEventBuffer();
        reader.wrap(buffer, 0, buffer.capacity());
        assertThat(reader.id()).isEqualTo(taskId1);

        buffer = event2.getEventBuffer();
        reader.wrap(buffer, 0, buffer.capacity());
        assertThat(reader.id()).isEqualTo(taskId2);
    }

    @Test
    public void shouldPollEventWithStartPosition()
    {
        final TngpClient client = clientRule.getClient();

        client.tasks().create()
            .taskQueueId(0)
            .taskType("test")
            .payload("foo")
            .execute();

        final Long taskId2 = client.tasks().create()
                .taskQueueId(0)
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

        assertThat(eventsBatch.getEvents()).hasSize(1);

        final Event event = eventsBatch.getEvents().get(0);
        final DirectBuffer buffer = event.getEventBuffer();

        final Template<TaskInstanceReader> template = Templates.getTemplate(Templates.TASK_INSTANCE.id());
        final TaskInstanceReader reader = template.newReader();

        reader.wrap(buffer, 0, buffer.capacity());
        assertThat(reader.id()).isEqualTo(taskId2);
    }

    @Test
    public void shouldPollNotMoreThanMaxEvents()
    {
        final TngpClient client = clientRule.getClient();

        client.tasks().create()
            .taskQueueId(0)
            .taskType("test")
            .payload("foo")
            .execute();

        client.tasks().create()
                .taskQueueId(0)
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
    @Ignore("implement with new log structure")
    public void shouldPollEventWithStartPositionBetweenEvents()
    {
        final TngpClient client = clientRule.getClient();

        client.tasks().create()
            .taskQueueId(0)
            .taskType("test")
            .payload("foo")
            .execute();

        final Long taskId2 = client.tasks().create()
                .taskQueueId(0)
                .taskType("test")
                .payload("bar")
                .execute();

        final EventsBatch eventsBatch = client.events().poll()
            .startPosition(INITIAL_LOG_POSITION + 10)
            .maxEvents(2)
            .topicId(TASK_QUEUE_TOPIC_ID)
            .execute();

        assertThat(eventsBatch.getEvents()).hasSize(1);

        final Template<TaskInstanceReader> template = Templates.getTemplate(Templates.TASK_INSTANCE.id());
        final TaskInstanceReader reader = template.newReader();

        final Event event1 = eventsBatch.getEvents().get(0);
        final DirectBuffer buffer = event1.getEventBuffer();
        reader.wrap(buffer, 0, buffer.capacity());

        assertThat(reader.id()).isEqualTo(taskId2);
    }

}
