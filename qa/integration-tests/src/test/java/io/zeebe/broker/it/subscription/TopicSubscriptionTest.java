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
package io.zeebe.broker.it.subscription;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.event.GeneralEvent;
import io.zeebe.client.event.PollableTopicSubscription;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.event.TopicEventType;
import io.zeebe.client.event.TopicSubscription;
import io.zeebe.client.event.UniversalEventHandler;
import io.zeebe.client.job.impl.CreateTaskCommandImpl;
import io.zeebe.client.topic.Topic;
import io.zeebe.client.topic.Topics;
import io.zeebe.client.impl.job.impl.CreateTaskCommandImpl;
import io.zeebe.client.impl.topic.Topic;
import io.zeebe.client.impl.topic.Topics;
import io.zeebe.client.impl.topic.impl.TopicEventImpl;
import io.zeebe.test.util.TestUtil;

public class TopicSubscriptionTest
{

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
    public Timeout timeout = Timeout.seconds(30);

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
        final TopicSubscription subscription = clientRule.topics().newSubscription(clientRule.getDefaultTopic())
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
        clientRule.topics().newSubscription(clientRule.getDefaultTopic())
            .handler(recordingHandler)
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        final TaskEvent task = clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
            .addCustomHeader("key", "value")
            .payload("{}")
            .execute();

        // then
        waitUntil(() -> recordingHandler.numRecordedTaskEvents() == 2);

        assertThat(recordingHandler.numRecordedTaskEvents()).isEqualTo(2);

        final long taskKey = task.getMetadata().getKey();
        recordingHandler.assertTaskEvent(0, taskKey, "CREATE");
        recordingHandler.assertTaskEvent(1, taskKey, "CREATED");
    }

    @Test
    public void shouldReceiveEventsCreatedBeforeSubscription() throws IOException
    {
        // given
        final TaskEvent task = clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
                .addCustomHeader("key", "value")
                .payload("{}")
                .execute();

        // when
        clientRule.topics().newSubscription(clientRule.getDefaultTopic())
            .handler(recordingHandler)
            .startAtHeadOfTopic()
            .name(SUBSCRIPTION_NAME)
            .open();

        // then
        waitUntil(() -> recordingHandler.numRecordedTaskEvents() == 2);

        assertThat(recordingHandler.numRecordedTaskEvents()).isEqualTo(2);

        final long taskKey = task.getMetadata().getKey();
        recordingHandler.assertTaskEvent(0, taskKey, "CREATE");
        recordingHandler.assertTaskEvent(1, taskKey, "CREATED");
    }

    @Test
    public void shouldReceiveEventsFromTailOfLog() throws IOException
    {
        // given
        clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
                .addCustomHeader("key", "value")
                .payload("{}")
                .execute();

        clientRule.topics().newSubscription(clientRule.getDefaultTopic())
            .handler(recordingHandler)
            .startAtTailOfTopic()
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        final TaskEvent task2 = clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
                .addCustomHeader("key", "value")
                .payload("{}")
                .execute();

        // then

        waitUntil(() -> recordingHandler.numRecordedTaskEvents() >= 2);

        assertThat(recordingHandler.numRecordedTaskEvents()).isEqualTo(2);

        // task 1 has not been received
        final long task2Key = task2.getMetadata().getKey();
        recordingHandler.assertTaskEvent(0, task2Key, "CREATE");
        recordingHandler.assertTaskEvent(1, task2Key, "CREATED");
    }

    @Test
    public void shouldReceiveEventsFromPosition() throws IOException
    {
        // given
        clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
                .addCustomHeader("key", "value")
                .payload("{}")
                .execute();

        clientRule.topics().newSubscription(clientRule.getDefaultTopic())
            .handler(recordingHandler)
            .startAtHeadOfTopic()
            .name(SUBSCRIPTION_NAME)
            .open();

        waitUntil(() -> recordingHandler.numRecordedTaskEvents() == 2);

        final List<GeneralEvent> recordedTaskEvents = recordingHandler.getRecordedEvents().stream()
                .filter((re) -> re.getMetadata().getType() == TopicEventType.TASK)
                .collect(Collectors.toList());

        final RecordingEventHandler subscription2Handler = new RecordingEventHandler();
        final long secondTaskEventPosition = recordedTaskEvents.get(1).getMetadata().getPosition();

        // when
        clientRule.topics().newSubscription(clientRule.getDefaultTopic())
            .handler(subscription2Handler)
            .startAtPosition(clientRule.getDefaultPartition(), secondTaskEventPosition)
            .name("another" + SUBSCRIPTION_NAME)
            .open();

        // then
        waitUntil(() -> subscription2Handler.numRecordedEvents() > 0);

        // only the second event is pushed to the second subscription
        final GeneralEvent firstEvent = subscription2Handler.getRecordedEvents().get(0);
        assertThat(firstEvent.getMetadata().getPosition()).isEqualTo(secondTaskEventPosition);

    }

    @Test
    public void shouldReceiveEventsFromPositionBeyondTail()
    {
        // given
        clientRule.topics().newSubscription(clientRule.getDefaultTopic())
            .handler(recordingHandler)
            .startAtPosition(clientRule.getDefaultPartition(), Long.MAX_VALUE)
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
            .addCustomHeader("key", "value")
            .payload("{}")
            .execute();

        // then
        waitUntil(() -> recordingHandler.numRecordedTaskEvents() == 2);

        // the events are nevertheless received, although they have a lower position
        assertThat(recordingHandler.numRecordedTaskEvents() == 2);
    }

    @Test
    public void shouldCloseSubscription() throws InterruptedException
    {
        // given
        final TopicSubscription subscription = clientRule.topics().newSubscription(clientRule.getDefaultTopic())
            .handler(recordingHandler)
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        subscription.close();

        // then
        assertThat(subscription.isOpen()).isFalse();

        clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
            .addCustomHeader("key", "value")
            .payload("{}")
            .execute();

        Thread.sleep(1000L);
        assertThat(recordingHandler.numRecordedTaskEvents()).isEqualTo(0);
    }

    @Test
    public void shouldRepeatedlyRecoverSubscription() throws InterruptedException
    {
        for (int i = 0; i < 100; i++)
        {
            // given
            final TopicSubscription subscription = clientRule.topics().newSubscription(clientRule.getDefaultTopic())
                                                             .handler(recordingHandler)
                                                             .name(SUBSCRIPTION_NAME)
                                                             .open();

            clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
                      .addCustomHeader("key", "value")
                      .payload("{}")
                      .execute();

            final int eventCount = i;
            waitUntil(() -> recordingHandler.numRecordedTaskEvents() >= eventCount);

            // when
            subscription.close();

            // then
            assertThat(subscription.isOpen()).isFalse();
        }

        assertThat(recordingHandler.numRecordedEvents()).isGreaterThan(100);
    }

    @Test
    public void shouldOpenMultipleSubscriptionsOnSameTopic() throws IOException
    {
        // given
        final TaskEvent task = clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
            .addCustomHeader("key", "value")
            .payload("{}")
            .execute();

        clientRule.topics().newSubscription(clientRule.getDefaultTopic())
            .startAtHeadOfTopic()
            .handler(recordingHandler)
            .name(SUBSCRIPTION_NAME)
            .open();


        final RecordingEventHandler secondEventHandler = new RecordingEventHandler();
        clientRule.topics().newSubscription(clientRule.getDefaultTopic())
            .startAtHeadOfTopic()
            .handler(secondEventHandler)
            .name("another" + SUBSCRIPTION_NAME)
            .open();

        // when
        waitUntil(() -> recordingHandler.numRecordedTaskEvents() == 2);
        waitUntil(() -> secondEventHandler.numRecordedTaskEvents() == 2);

        // then
        final long taskKey = task.getMetadata().getKey();
        recordingHandler.assertTaskEvent(0, taskKey, "CREATE");
        recordingHandler.assertTaskEvent(1, taskKey, "CREATED");
        secondEventHandler.assertTaskEvent(0, taskKey, "CREATE");
        secondEventHandler.assertTaskEvent(1, taskKey, "CREATED");
    }

    @Test
    public void shouldHandleOneEventAtATime() throws InterruptedException
    {
        // given
        clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
            .addCustomHeader("key", "value")
            .payload("{}")
            .execute();

        final Duration handlingIntervalLength = Duration.ofSeconds(1);
        final ParallelismDetectionHandler handler = new ParallelismDetectionHandler(handlingIntervalLength);

        // when
        clientRule.topics().newSubscription(clientRule.getDefaultTopic())
            .startAtHeadOfTopic()
            .handler(handler)
            .name(SUBSCRIPTION_NAME)
            .open();

        // then
        final int numExpectedEvents = 2;
        Thread.sleep(handlingIntervalLength.toMillis() * numExpectedEvents);

        // at least CREATE and CREATED of the task, but we may have already handled a third event (e.g. raft)
        waitUntil(() -> handler.numInvocations() >= numExpectedEvents);
        assertThat(handler.hasDetectedParallelism()).isFalse();
    }

    @Test
    public void shouldCreatePollableSubscription() throws IOException
    {
        // given
        final TaskEvent task = clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
                .addCustomHeader("key", "value")
                .payload("{}")
                .execute();

        final PollableTopicSubscription subscription = clientRule.topics()
            .newPollableSubscription(clientRule.getDefaultTopic())
            .startAtHeadOfTopic()
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        TestUtil.doRepeatedly(() -> subscription.poll(recordingHandler)).until((i) -> recordingHandler.numRecordedTaskEvents() == 2);

        // then
        assertThat(recordingHandler.numRecordedTaskEvents()).isEqualTo(2);

        final long taskKey = task.getMetadata().getKey();
        recordingHandler.assertTaskEvent(0, taskKey, "CREATE");
        recordingHandler.assertTaskEvent(1, taskKey, "CREATED");
    }

    @Test
    public void shouldResumeSubscription()
    {
        // given a first task
        clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
                .addCustomHeader("key", "value")
                .payload("{}")
                .execute();

        final String subscriptionName = "foo";

        final TopicSubscription subscription = clientRule.topics()
            .newSubscription(clientRule.getDefaultTopic())
            .handler(recordingHandler)
            .name(subscriptionName)
            .startAtHeadOfTopic()
            .open();

        // that was received by the subscription
        waitUntil(() -> recordingHandler.numRecordedTaskEvents() == 2);

        subscription.close();

        final long lastEventPosition = recordingHandler.getRecordedEvents()
                .get(recordingHandler.numRecordedEvents() - 1)
                .getMetadata()
                .getPosition();

        recordingHandler.reset();

        // and a second not-yet-received task
        clientRule.tasks().create(clientRule.getDefaultTopic(), "bar")
            .addCustomHeader("key", "value")
            .payload("{}")
            .execute();

        // when
        clientRule.topics()
                .newSubscription(clientRule.getDefaultTopic())
                .handler(recordingHandler)
                .name(subscriptionName)
                .startAtHeadOfTopic()
                .open();

        // then
        waitUntil(() -> recordingHandler.numRecordedEvents() > 0);

        final long firstEventPositionAfterReopen = recordingHandler.getRecordedEvents()
                .get(0)
                .getMetadata()
                .getPosition();

        assertThat(firstEventPositionAfterReopen).isGreaterThan(lastEventPosition);
    }

    protected static class ParallelismDetectionHandler implements UniversalEventHandler
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
        public void handle(GeneralEvent event) throws Exception
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
    public void testNameUniqueness()
    {
        // given
        clientRule.topics().newSubscription(clientRule.getDefaultTopic())
                .handler(recordingHandler)
                .name(SUBSCRIPTION_NAME)
                .open();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Could not open subscriber group");

        // when
        clientRule.topics().newSubscription(clientRule.getDefaultTopic())
            .handler(recordingHandler)
            .name(SUBSCRIPTION_NAME)
            .open();
    }

    @Test
    public void testSubscriptionsWithSameNameOnDifferentTopic()
    {
        // given
        final String anotherTopicName = "another-topic";
        client.topics().create(anotherTopicName, 1).execute();
        clientRule.waitUntilTopicsExists(anotherTopicName);

        client.topics().newSubscription(clientRule.getDefaultTopic())
            .handler(recordingHandler)
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        final TopicSubscription topic2Subscription = client.topics().newSubscription(anotherTopicName)
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
        final String anotherTopicName = "another-topic";
        client.topics().create(anotherTopicName, 1).execute();
        clientRule.waitUntilTopicsExists(anotherTopicName);
        final int anotherPartitionId = 2;

        final TopicSubscription topic0subscription = client.topics().newSubscription(clientRule.getDefaultTopic())
                .startAtHeadOfTopic()
                .handler(recordingHandler)
                .name(SUBSCRIPTION_NAME)
                .open();

        final RecordingEventHandler anotherRecordingHandler = new RecordingEventHandler();
        final TopicSubscription topic1Subscription = client.topics().newSubscription(anotherTopicName)
                .startAtHeadOfTopic()
                .handler(anotherRecordingHandler)
                .name(SUBSCRIPTION_NAME)
                .open();


        // when
        clientRule.tasks().create(clientRule.getDefaultTopic(), "foo").execute();
        clientRule.tasks().create(anotherTopicName, "bar").execute();

        // then
        waitUntil(() -> recordingHandler.numRecordedTaskEvents() >= 2);
        waitUntil(() -> anotherRecordingHandler.numRecordedTaskEvents() >= 2);

        topic0subscription.close();
        topic1Subscription.close();

        Set<String> receivedTopicNamesSubscription = recordingHandler.getRecordedEvents().stream()
            .filter((re) -> re.getMetadata().getType() == TopicEventType.TASK)
            .map((re) -> re.getMetadata().getTopicName())
            .collect(Collectors.toSet());

        Set<Integer> receivedPartitionIdsSubscription = recordingHandler.getRecordedEvents().stream()
            .filter((re) -> re.getMetadata().getType() == TopicEventType.TASK)
            .map((re) -> re.getMetadata().getPartitionId())
            .collect(Collectors.toSet());

        assertThat(receivedTopicNamesSubscription).containsExactly(clientRule.getDefaultTopic());
        assertThat(receivedPartitionIdsSubscription).containsExactly(clientRule.getDefaultPartition());

        receivedTopicNamesSubscription = anotherRecordingHandler.getRecordedEvents().stream()
            .filter((re) -> re.getMetadata().getType() == TopicEventType.TASK)
            .map((re) -> re.getMetadata().getTopicName())
            .collect(Collectors.toSet());

        receivedPartitionIdsSubscription = anotherRecordingHandler.getRecordedEvents().stream()
            .filter((re) -> re.getMetadata().getType() == TopicEventType.TASK)
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
        clientRule.topics().newSubscription(clientRule.getDefaultTopic())
            .handler(recordingHandler)
            .name(SUBSCRIPTION_NAME)
            .open();

        // when
        clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
            .addCustomHeader("key", "value")
            .payload("{}")
            .execute();

        // then
        waitUntil(() -> recordingHandler.numRecordedTaskEvents() == 2);

        assertThat(recordingHandler.getRecordedEvents())
            .filteredOn((re) -> re.getMetadata().getType() == TopicEventType.UNKNOWN)
            .isEmpty();
    }

    @Test
    public void shouldReceiveMoreEventsThanSubscriptionCapacity()
    {
        // given
        final int subscriptionCapacity = client.getConfiguration().getTopicSubscriptionPrefetchCapacity();

        for (int i = 0; i < subscriptionCapacity + 1; i++)
        {
            clientRule.tasks().create(clientRule.getDefaultTopic(), "foo")
                .addCustomHeader("key", "value")
                .payload("{}")
                .execute();
        }

        // when
        clientRule.topics().newSubscription(clientRule.getDefaultTopic())
            .handler(recordingHandler)
            .startAtHeadOfTopic()
            .name(SUBSCRIPTION_NAME)
            .open();

        // then
        waitUntil(() -> recordingHandler.numRecordedEvents() > subscriptionCapacity);
    }

    @Test
    public void shouldReceiveEventsFromMultiplePartitions()
    {
        // given
        final String topicName = "pasta al forno";
        final int numPartitions = 2;
        client.topics().create(topicName, numPartitions).execute();
        clientRule.waitUntilTopicsExists(topicName);

        final Topics topics = client.topics().getTopics().execute();
        final Topic topic = topics.getTopics().stream()
            .filter(t -> t.getName().equals(topicName))
            .findFirst()
            .get();

        final Integer[] partitionIds = topic.getPartitions().stream()
                .mapToInt(p -> p.getId())
                .boxed()
                .toArray(Integer[]::new);

        createTaskOnPartition(topicName, partitionIds[0]);
        createTaskOnPartition(topicName, partitionIds[1]);

        final List<Integer> receivedPartitionIds = new ArrayList<>();

        // when
        client.topics().newSubscription(topicName)
            .handler(recordingHandler)
            .taskEventHandler(e ->
            {
                if ("CREATE".equals(e.getState()))
                {
                    receivedPartitionIds.add(e.getMetadata().getPartitionId());
                }
            })
            .startAtHeadOfTopic()
            .name("foo")
            .open();

        // then
        waitUntil(() -> receivedPartitionIds.size() == numPartitions);

        assertThat(receivedPartitionIds).containsExactlyInAnyOrder(partitionIds);
    }

    protected void createTaskOnPartition(String topic, int partition)
    {
        final CreateTaskCommandImpl createTaskCommand = (CreateTaskCommandImpl) client.tasks().create(topic, "baz");
        createTaskCommand.getCommand().setPartitionId(partition);
        createTaskCommand.execute();
    }

}
