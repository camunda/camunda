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
package io.zeebe.broker.it.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static io.zeebe.logstreams.log.LogStream.DEFAULT_PARTITION_ID;
import static io.zeebe.logstreams.log.LogStream.DEFAULT_TOPIC_NAME;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.subscription.RecordingEventHandler.RecordedEvent;
import io.zeebe.client.ClientProperties;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.EventMetadata;
import io.zeebe.client.event.PollableTopicSubscription;
import io.zeebe.client.event.TopicEvent;
import io.zeebe.client.event.TopicEventHandler;
import io.zeebe.client.event.TopicEventType;
import io.zeebe.client.event.TopicSubscription;
import io.zeebe.test.util.TestUtil;
import org.junit.Before;
import org.junit.Ignore;
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
    public Timeout timeout = Timeout.seconds(20);

    protected ZeebeClient client;
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
        final TopicSubscription subscription = clientRule.topic().newSubscription()
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
        clientRule.topic().newSubscription()
            .handler(recordingHandler)
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        final Long taskKey = clientRule.taskTopic().create()
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
        final Long taskKey = clientRule.taskTopic().create()
                .addHeader("key", "value")
                .payload("{}")
                .taskType("foo")
                .execute();

        // when
        clientRule.topic().newSubscription()
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
        clientRule.taskTopic().create()
                .addHeader("key", "value")
                .payload("{}")
                .taskType("foo")
                .execute();

        clientRule.topic().newSubscription()
            .handler(recordingHandler)
            .startAtTailOfTopic()
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        final Long task2Key = clientRule.taskTopic().create()
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
        clientRule.taskTopic().create()
                .addHeader("key", "value")
                .payload("{}")
                .taskType("foo")
                .execute();

        clientRule.topic().newSubscription()
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
        clientRule.topic().newSubscription()
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
        clientRule.topic().newSubscription()
            .handler(recordingHandler)
            .startAtPosition(Long.MAX_VALUE)
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        clientRule.taskTopic().create()
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
        final TopicSubscription subscription = clientRule.topic().newSubscription()
            .handler(recordingHandler)
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        subscription.close();

        // then
        assertThat(subscription.isOpen()).isFalse();

        clientRule.taskTopic().create()
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
        final Long taskKey = clientRule.taskTopic().create()
            .addHeader("key", "value")
            .payload("{}")
            .taskType("foo")
            .execute();

        clientRule.topic().newSubscription()
            .startAtHeadOfTopic()
            .handler(recordingHandler)
            .name(SUBSCRIPTION_NAME)
            .open();


        final RecordingEventHandler secondEventHandler = new RecordingEventHandler();
        clientRule.topic().newSubscription()
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
        clientRule.taskTopic().create()
            .addHeader("key", "value")
            .payload("{}")
            .taskType("foo")
            .execute();

        final Duration handlingIntervalLength = Duration.ofSeconds(1);
        final ParallelismDetectionHandler handler = new ParallelismDetectionHandler(handlingIntervalLength);

        // when
        clientRule.topic().newSubscription()
            .startAtHeadOfTopic()
            .handler(handler)
            .name(SUBSCRIPTION_NAME)
            .open();

        // then
        final int numExpectedEvents = 2;
        Thread.sleep(handlingIntervalLength.toMillis() * numExpectedEvents);

        // at least CREATE and CREATED of the task, but we may have already handled a third event (e.g. raft)
        TestUtil.waitUntil(() -> handler.numInvocations() >= numExpectedEvents);
        assertThat(handler.hasDetectedParallelism()).isFalse();
    }

    @Test
    public void shouldCreatePollableSubscription() throws IOException
    {
        // given
        final Long taskKey = clientRule.taskTopic().create()
                .addHeader("key", "value")
                .payload("{}")
                .taskType("foo")
                .execute();

        final PollableTopicSubscription subscription = clientRule.topic()
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
        clientRule.taskTopic().create()
                .addHeader("key", "value")
                .payload("{}")
                .taskType("foo")
                .execute();

        final String subscriptionName = "foo";

        final TopicSubscription subscription = clientRule.topic()
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
        clientRule.taskTopic().create()
            .addHeader("key", "value")
            .payload("{}")
            .taskType("bar")
            .execute();

        // when
        clientRule.topic()
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
    public void testValidateTopicNameNotNull()
    {
        // given
        final ZeebeClient client = clientRule.getClient();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("topic name must not be null");

        // when
        client.topic(null, DEFAULT_PARTITION_ID);
    }

    @Test
    public void testValidateTopicNameNotEmpty()
    {
        // given
        final ZeebeClient client = clientRule.getClient();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("topic name must not be empty");

        // when
        client.topic("", DEFAULT_PARTITION_ID);
    }

    @Test
    public void testValidatePartitionId()
    {
        // given
        final ZeebeClient client = clientRule.getClient();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("partition id must be greater than or equal to 0");

        // when
        client.topic(DEFAULT_TOPIC_NAME, -1);
    }

    @Test
    public void testNameUniqueness()
    {
        // given
        clientRule.topic().newSubscription()
                .handler(recordingHandler)
                .name(SUBSCRIPTION_NAME)
                .open();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Exception while opening subscription");

        // when
        clientRule.topic().newSubscription()
            .handler(recordingHandler)
            .name(SUBSCRIPTION_NAME)
            .open();
    }

    @Test
    @Ignore("Requires API to create multiple topics (CAM-222)")
    public void testSubscriptionsWithSameNameOnDifferentTopic()
    {
        // given
        client.topic(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID).newSubscription()
            .handler(recordingHandler)
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        final TopicSubscription topic2Subscription = client.topic("another-topic", DEFAULT_PARTITION_ID).newSubscription()
            .handler(recordingHandler)
            .name(SUBSCRIPTION_NAME)
            .open();

        // then
        assertThat(topic2Subscription.isOpen()).isTrue();
    }

    @Test
    @Ignore("Requires API to create multiple topics (#222, #235)")
    public void testSubscriptionsWithSameNameOnDifferentTopicShouldReceiveRespectiveEvents()
    {
        // given
        final String anotherTopicName = "another-topic";
        final int anotherPartitionId = 2;

        final TopicSubscription topic0subscription = client.topic(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID).newSubscription()
                .startAtHeadOfTopic()
                .handler(recordingHandler)
                .name(SUBSCRIPTION_NAME)
                .open();

        final RecordingEventHandler anotherRecordingHandler = new RecordingEventHandler();
        final TopicSubscription topic1Subscription = client.topic(anotherTopicName, anotherPartitionId).newSubscription()
                .startAtHeadOfTopic()
                .handler(anotherRecordingHandler)
                .name(SUBSCRIPTION_NAME)
                .open();


        // when
        client.taskTopic(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID).create()
            .taskType("foo")
            .execute();

        client.taskTopic(anotherTopicName, anotherPartitionId).create()
            .taskType("bar")
            .execute();

        // then
        TestUtil.waitUntil(() -> recordingHandler.numRecordedTaskEvents() >= 2);
        TestUtil.waitUntil(() -> anotherRecordingHandler.numRecordedTaskEvents() >= 2);

        topic0subscription.close();
        topic1Subscription.close();

        Set<String> receivedTopicNamesSubscription = recordingHandler.getRecordedEvents().stream()
            .filter((re) -> re.getMetadata().getEventType() == TopicEventType.TASK)
            .map((re) -> re.getMetadata().getTopicName())
            .collect(Collectors.toSet());

        Set<Integer> receivedPartitionIdsSubscription = recordingHandler.getRecordedEvents().stream()
            .filter((re) -> re.getMetadata().getEventType() == TopicEventType.TASK)
            .map((re) -> re.getMetadata().getPartitionId())
            .collect(Collectors.toSet());

        assertThat(receivedTopicNamesSubscription).containsExactly(DEFAULT_TOPIC_NAME);
        assertThat(receivedPartitionIdsSubscription).containsExactly(DEFAULT_PARTITION_ID);

        receivedTopicNamesSubscription = anotherRecordingHandler.getRecordedEvents().stream()
            .filter((re) -> re.getMetadata().getEventType() == TopicEventType.TASK)
            .map((re) -> re.getMetadata().getTopicName())
            .collect(Collectors.toSet());

        receivedPartitionIdsSubscription = anotherRecordingHandler.getRecordedEvents().stream()
            .filter((re) -> re.getMetadata().getEventType() == TopicEventType.TASK)
            .map((re) -> re.getMetadata().getPartitionId())
            .collect(Collectors.toSet());

        assertThat(receivedTopicNamesSubscription).containsExactly(anotherTopicName);
        assertThat(receivedPartitionIdsSubscription).containsExactly(anotherPartitionId);
    }

    /**
     * E.g. subscription ACKs should not be pushed to the client
     */
    @Test
    public void shouldNotPushAnySubscriptionEvents()
    {
        // given
        clientRule.topic().newSubscription()
            .handler(recordingHandler)
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        clientRule.taskTopic().create()
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

    @Test
    public void shouldReceiveMoreEventsThanSubscriptionCapacity()
    {
        // given
        final Properties properties = new Properties();
        ClientProperties.setDefaults(properties);
        final int subscriptionCapacity = Integer.parseInt(
                properties.getProperty(ClientProperties.CLIENT_TOPIC_SUBSCRIPTION_PREFETCH_CAPACITY));

        for (int i = 0; i < subscriptionCapacity + 1; i++)
        {
            clientRule.taskTopic().create()
                .addHeader("key", "value")
                .payload("{}")
                .taskType("foo")
                .execute();
        }

        // when
        clientRule.topic().newSubscription()
            .handler(recordingHandler)
            .startAtHeadOfTopic()
            .name(SUBSCRIPTION_NAME)
            .open();

        // then
        TestUtil.waitUntil(() -> recordingHandler.numRecordedEvents() > subscriptionCapacity);
    }

}
