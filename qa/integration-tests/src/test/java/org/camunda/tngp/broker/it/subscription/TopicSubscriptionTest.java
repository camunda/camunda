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
import java.util.Set;
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

    public static final int EXPECTED_HANDLER_INVOCATIONS = 3;
    public static final String SUBSCRIPTION_NAME = "foo";

    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

    public ClientRule clientRule = new ClientRule();

    @Rule
    public RuleChain ruleChain = RuleChain
        .outerRule(brokerRule)
        .around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public Timeout timeout = Timeout.seconds(10);

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
            .name(SUBSCRIPTION_NAME)
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
            .name(SUBSCRIPTION_NAME)
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
            .name(SUBSCRIPTION_NAME)
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
            .name(SUBSCRIPTION_NAME)
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
            .name(SUBSCRIPTION_NAME)
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
            .name("another" + SUBSCRIPTION_NAME)
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
            .name(SUBSCRIPTION_NAME)
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
            .name(SUBSCRIPTION_NAME)
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
            .name(SUBSCRIPTION_NAME)
            .open();


        final RecordingEventHandler secondEventHandler = new RecordingEventHandler();
        client.topic(0).newSubscription()
            .startAtHeadOfTopic()
            .handler(secondEventHandler)
            .name("another" + SUBSCRIPTION_NAME)
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
            .name(SUBSCRIPTION_NAME)
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
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        TestUtil.doRepeatedly(() -> subscription.poll(recordingHandler)).until((i) -> recordingHandler.numRecordedTaskEvents() == 2);

        // then
        assertThat(recordingHandler.numRecordedTaskEvents()).isEqualTo(2);

        recordingHandler.assertTaskEvent(0, taskKey, "CREATE");
        recordingHandler.assertTaskEvent(1, taskKey, "CREATED");
    }

    @Test
    public void shouldResumeSubscription()
    {
        // given a first task
        client.taskTopic(0).create()
                .addHeader("key", "value")
                .payload("{}")
                .taskType("foo")
                .execute();

        final String subscriptionName = "foo";

        final TopicSubscription subscription = client.topic(0)
            .newSubscription()
            .handler(recordingHandler)
            .name(subscriptionName)
            .startAtHeadOfTopic()
            .open();

        // that was received by the subscription
        TestUtil.waitUntil(() -> recordingHandler.numRecordedTaskEvents() == 2);

        subscription.close();

        final long lastEventPosition = recordingHandler.getRecordedEvents()
                .get(recordingHandler.numRecordedEvents() - 1)
                .getMetadata()
                .getEventPosition();

        recordingHandler.reset();

        // and a second not-yet-received task
        client.taskTopic(0).create()
            .addHeader("key", "value")
            .payload("{}")
            .taskType("bar")
            .execute();

        // when
        client.topic(0)
                .newSubscription()
                .handler(recordingHandler)
                .name(subscriptionName)
                .startAtHeadOfTopic()
                .open();

        // then
        TestUtil.waitUntil(() -> recordingHandler.numRecordedEvents() > 0);

        final long firstEventPositionAfterReopen = recordingHandler.getRecordedEvents()
                .get(0)
                .getMetadata()
                .getEventPosition();

        assertThat(firstEventPositionAfterReopen).isGreaterThan(lastEventPosition);
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

    @Test
    public void testNameUniqueness()
    {
        // given
        client.topic(0).newSubscription()
                .handler(recordingHandler)
                .name(SUBSCRIPTION_NAME)
                .open();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Exception while opening subscription");

        // when
        client.topic(0).newSubscription()
            .handler(recordingHandler)
            .name(SUBSCRIPTION_NAME)
            .open();
    }

    @Test
    public void testSubscriptionsWithSameNameOnDifferentTopic()
    {
        // given
        client.topic(0).newSubscription()
            .handler(recordingHandler)
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        final TopicSubscription topic2Subscription = client.topic(1).newSubscription()
            .handler(recordingHandler)
            .name(SUBSCRIPTION_NAME)
            .open();

        // then
        assertThat(topic2Subscription.isOpen()).isTrue();
    }

    @Test
    public void testSubscriptionsWithSameNameOnDifferentTopicShouldReceiveRespectiveEvents()
    {
        // given
        final TopicSubscription topic0subscription = client.topic(0).newSubscription()
                .startAtHeadOfTopic()
                .handler(recordingHandler)
                .name(SUBSCRIPTION_NAME)
                .open();

        final RecordingEventHandler topic1Handler = new RecordingEventHandler();
        final TopicSubscription topic1Subscription = client.topic(1).newSubscription()
                .startAtHeadOfTopic()
                .handler(topic1Handler)
                .name(SUBSCRIPTION_NAME)
                .open();


        // when
        client.taskTopic(0).create()
            .taskType("foo")
            .execute();

        client.taskTopic(1).create()
            .taskType("bar")
            .execute();

        // then
        TestUtil.waitUntil(() -> recordingHandler.numRecordedTaskEvents() >= 2);
        TestUtil.waitUntil(() -> topic1Handler.numRecordedTaskEvents() >= 2);

        topic0subscription.close();
        topic1Subscription.close();

        final Set<Integer> receivedTopicIdsSubscription0 = recordingHandler.getRecordedEvents().stream()
            .filter((re) -> re.getMetadata().getEventType() == TopicEventType.TASK)
            .map((re) -> re.getMetadata().getTopicId())
            .collect(Collectors.toSet());

        final Set<Integer> receivedTopicIdsSubscription1 = topic1Handler.getRecordedEvents().stream()
            .filter((re) -> re.getMetadata().getEventType() == TopicEventType.TASK)
            .map((re) -> re.getMetadata().getTopicId())
            .collect(Collectors.toSet());

        assertThat(receivedTopicIdsSubscription0).containsExactly(0);
        assertThat(receivedTopicIdsSubscription1).containsExactly(1);
    }

    /**
     * E.g. subscription ACKs should not be pushed to the client
     */
    @Test
    public void shouldNotPushAnySubscriptionEvents()
    {
        // given
        client.topic(0).newSubscription()
            .handler(recordingHandler)
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        client.taskTopic(0).create()
            .addHeader("key", "value")
            .payload("{}")
            .taskType("foo")
            .execute();

        // then
        TestUtil.waitUntil(() -> recordingHandler.numRecordedTaskEvents() == 2);

        assertThat(recordingHandler.getRecordedEvents())
            .filteredOn((re) -> re.getMetadata().getEventType() == TopicEventType.UNKNOWN)
            .isEmpty();
    }

}
