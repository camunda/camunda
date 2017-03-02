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
package org.camunda.tngp.broker.it.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.camunda.tngp.broker.it.ClientRule;
import org.camunda.tngp.broker.it.EmbeddedBrokerRule;
import org.camunda.tngp.broker.it.subscription.RecordingEventHandler.RecordedEvent;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.event.EventMetadata;
import org.camunda.tngp.client.event.PollableTopicSubscription;
import org.camunda.tngp.client.event.TopicEvent;
import org.camunda.tngp.client.event.TopicEventHandler;
import org.camunda.tngp.client.event.TopicEventType;
import org.camunda.tngp.client.event.TopicSubscription;
import org.camunda.tngp.test.util.TestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TopicSubscriptionTest
{

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public Timeout timeout = Timeout.seconds(10 * 100);

    protected TngpClient client;
    protected RecordingEventHandler recordingHandler;
    protected ObjectMapper objectMapper;

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();
        this.recordingHandler = new RecordingEventHandler();
        this.objectMapper = new ObjectMapper();
    }

    @Test
    public void shouldOpenSubscription()
    {
        // when
        final TopicSubscription subscription = client.topic(0).newSubscription()
            .handler(recordingHandler)
            .open();

        // then
        assertThat(subscription.isOpen());

    }

    @Test
    public void shouldReceiveEventsCreatedAfterSubscription() throws IOException
    {
        // given
        client.topic(0).newSubscription()
            .handler(recordingHandler)
            .open();

        // when
        final Long taskKey = client.taskTopic(0).create()
            .addHeader("key", "value")
            .payload("{}")
            .taskType("foo")
            .execute();

        // then
        TestUtil.waitUntil(() -> recordingHandler.numRecordedTaskEvents() == 2);

        assertThat(recordingHandler.numRecordedTaskEvents()).isEqualTo(2);

        recordingHandler.assertTaskEvent(0, taskKey, "CREATE");
        recordingHandler.assertTaskEvent(1, taskKey, "CREATED");
    }

    @Test
    public void shouldReceiveEventsCreatedBeforeSubscription() throws IOException
    {
        // given
        final Long taskKey = client.taskTopic(0).create()
                .addHeader("key", "value")
                .payload("{}")
                .taskType("foo")
                .execute();

        // when
        client.topic(0).newSubscription()
            .handler(recordingHandler)
            .startAtHeadOfTopic()
            .open();

        // then
        TestUtil.waitUntil(() -> recordingHandler.numRecordedTaskEvents() == 2);

        assertThat(recordingHandler.numRecordedTaskEvents()).isEqualTo(2);

        recordingHandler.assertTaskEvent(0, taskKey, "CREATE");
        recordingHandler.assertTaskEvent(1, taskKey, "CREATED");
    }

    @Test
    public void shouldReceiveEventsFromTailOfLog() throws IOException
    {
        // given
        client.taskTopic(0).create()
                .addHeader("key", "value")
                .payload("{}")
                .taskType("foo")
                .execute();

        client.topic(0).newSubscription()
            .handler(recordingHandler)
            .startAtTailOfTopic()
            .open();

        // when
        final Long task2Key = client.taskTopic(0).create()
                .addHeader("key", "value")
                .payload("{}")
                .taskType("foo")
                .execute();

        // then

        TestUtil.waitUntil(() -> recordingHandler.numRecordedTaskEvents() >= 2);

        assertThat(recordingHandler.numRecordedTaskEvents()).isEqualTo(2);

        // task 1 has not been received
        recordingHandler.assertTaskEvent(0, task2Key, "CREATE");
        recordingHandler.assertTaskEvent(1, task2Key, "CREATED");
    }

    @Test
    public void shouldReceiveEventsFromPosition() throws IOException
    {
        // given
        client.taskTopic(0).create()
                .addHeader("key", "value")
                .payload("{}")
                .taskType("foo")
                .execute();

        client.topic(0).newSubscription()
            .handler(recordingHandler)
            .startAtHeadOfTopic()
            .open();

        TestUtil.waitUntil(() -> recordingHandler.numRecordedTaskEvents() == 2);

        final List<RecordedEvent> recordedTaskEvents = recordingHandler.getRecordedEvents().stream()
                .filter((re) -> re.getMetadata().getEventType() == TopicEventType.TASK)
                .collect(Collectors.toList());

        final RecordingEventHandler subscription2Handler = new RecordingEventHandler();
        final long secondTaskEventPosition = recordedTaskEvents.get(1).getMetadata().getEventPosition();

        // when
        client.topic(0).newSubscription()
            .handler(subscription2Handler)
            .startAtPosition(secondTaskEventPosition)
            .open();

        // then
        TestUtil.waitUntil(() -> subscription2Handler.numRecordedEvents() > 0);

        // only the second event is pushed to the second subscription
        final RecordedEvent firstEvent = subscription2Handler.getRecordedEvents().get(0);
        assertThat(firstEvent.getMetadata().getEventPosition()).isEqualTo(secondTaskEventPosition);

    }

    @Test
    public void shouldReceiveEventsFromPositionBeyondTail()
    {
        // given
        client.topic(0).newSubscription()
            .handler(recordingHandler)
            .startAtPosition(Long.MAX_VALUE)
            .open();

        // when
        client.taskTopic(0).create()
            .addHeader("key", "value")
            .payload("{}")
            .taskType("foo")
            .execute();

        // then
        TestUtil.waitUntil(() -> recordingHandler.numRecordedTaskEvents() == 2);

        // the events are nevertheless received, although they have a lower position
        assertThat(recordingHandler.numRecordedTaskEvents() == 2);
    }

    @Test
    public void shouldCloseSubscription() throws InterruptedException
    {
        // given
        final TopicSubscription subscription = client.topic(0).newSubscription()
            .handler(recordingHandler)
            .open();

        // when
        subscription.close();

        // then
        assertThat(subscription.isOpen()).isFalse();

        client.taskTopic(0).create()
            .addHeader("key", "value")
            .payload("{}")
            .taskType("foo")
            .execute();

        Thread.sleep(1000L);
        assertThat(recordingHandler.numRecordedTaskEvents()).isEqualTo(0);
    }

    @Test
    public void shouldOpenMultipleSubscriptionsOnSameTopic() throws IOException
    {
        // given
        final Long taskKey = client.taskTopic(0).create()
            .addHeader("key", "value")
            .payload("{}")
            .taskType("foo")
            .execute();

        client.topic(0).newSubscription()
            .startAtHeadOfTopic()
            .handler(recordingHandler)
            .open();


        final RecordingEventHandler secondEventHandler = new RecordingEventHandler();
        client.topic(0).newSubscription()
            .startAtHeadOfTopic()
            .handler(secondEventHandler)
            .open();

        // when
        TestUtil.waitUntil(() -> recordingHandler.numRecordedTaskEvents() == 2);
        TestUtil.waitUntil(() -> secondEventHandler.numRecordedTaskEvents() == 2);

        // then
        recordingHandler.assertTaskEvent(0, taskKey, "CREATE");
        recordingHandler.assertTaskEvent(1, taskKey, "CREATED");
        secondEventHandler.assertTaskEvent(0, taskKey, "CREATE");
        secondEventHandler.assertTaskEvent(1, taskKey, "CREATED");
    }

    @Test
    public void shouldHandleOneEventAtATime() throws InterruptedException
    {
        // given
        client.taskTopic(0).create()
            .addHeader("key", "value")
            .payload("{}")
            .taskType("foo")
            .execute();

        final Duration handlingIntervalLength = Duration.ofSeconds(1);
        final ParallelismDetectionHandler handler = new ParallelismDetectionHandler(handlingIntervalLength);

        // when
        client.topic(0).newSubscription()
            .startAtHeadOfTopic()
            .handler(handler)
            .open();

        // then
        final int numExpectedEvents = 2;
        Thread.sleep(handlingIntervalLength.toMillis() * numExpectedEvents);

        TestUtil.waitUntil(() -> handler.numInvocations() == numExpectedEvents);
        assertThat(handler.hasDetectedParallelism()).isFalse();
    }

    @Test
    public void shouldCreatePollableSubscription() throws IOException
    {
        // given
        final Long taskKey = client.taskTopic(0).create()
                .addHeader("key", "value")
                .payload("{}")
                .taskType("foo")
                .execute();

        final PollableTopicSubscription subscription = client.topic(0)
            .newPollableSubscription()
            .startAtHeadOfTopic()
            .open();

        // when
        TestUtil.doRepeatedly(() -> subscription.poll(recordingHandler)).until((i) -> recordingHandler.numRecordedTaskEvents() == 2);

        // then
        assertThat(recordingHandler.numRecordedTaskEvents()).isEqualTo(2);

        recordingHandler.assertTaskEvent(0, taskKey, "CREATE");
        recordingHandler.assertTaskEvent(1, taskKey, "CREATED");
    }

    protected static class ParallelismDetectionHandler implements TopicEventHandler
    {

        protected AtomicBoolean executing = new AtomicBoolean(false);
        protected AtomicBoolean parallelInvocationDetected = new AtomicBoolean(false);
        protected AtomicInteger numInvocations = new AtomicInteger(0);
        protected long timeout;

        public ParallelismDetectionHandler(Duration duration)
        {
            this.timeout = duration.toMillis();
        }

        @Override
        public void handle(EventMetadata metadata, TopicEvent event) throws Exception
        {
            numInvocations.incrementAndGet();
            if (executing.compareAndSet(false, true))
            {
                try
                {
                    Thread.sleep(timeout);
                }
                finally
                {
                    executing.set(false);
                }
            }
            else
            {
                parallelInvocationDetected.set(true);
            }
        }

        public boolean hasDetectedParallelism()
        {
            return parallelInvocationDetected.get();
        }

        public int numInvocations()
        {
            return numInvocations.get();
        }

    }

    @Test
    public void testValidateTopicId()
    {
        // given
        final TngpClient client = clientRule.getClient();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("id must be greater than or equal to 0");

        // when
        client.topic(-1);
    }

}
