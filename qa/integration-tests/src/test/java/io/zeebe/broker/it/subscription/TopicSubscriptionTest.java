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
import io.zeebe.client.api.clients.TopicClient;
import io.zeebe.client.api.commands.JobCommand;
import io.zeebe.client.api.commands.Topic;
import io.zeebe.client.api.commands.Topics;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.record.Record;
import io.zeebe.client.api.record.RecordMetadata;
import io.zeebe.client.api.subscription.RecordHandler;
import io.zeebe.client.api.subscription.TopicSubscription;
import io.zeebe.client.impl.job.CreateJobCommandImpl;

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

    protected TopicClient client;
    protected RecordingEventHandler recordingHandler;
    protected ObjectMapper objectMapper;

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient().topicClient();
        this.recordingHandler = new RecordingEventHandler();
        this.objectMapper = new ObjectMapper();
    }

    @Test
    public void shouldOpenSubscription()
    {
        // when
        final TopicSubscription subscription = client.subscriptionClient().newTopicSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(recordingHandler)
            .open();

        // then
        assertThat(subscription.isOpen());

    }

    @Test
    public void shouldReceiveEventsCreatedAfterSubscription() throws IOException
    {
        // given
        client.subscriptionClient().newTopicSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(recordingHandler)
            .open();

        // when
        final JobEvent job = client.jobClient().newCreateCommand()
            .jobType("foo")
            .send()
            .join();

        // then
        waitUntil(() -> recordingHandler.numJobRecords() == 2);

        assertThat(recordingHandler.numJobRecords()).isEqualTo(2);

        final long taskKey = job.getKey();
        recordingHandler.assertJobRecord(0, taskKey, "CREATE");
        recordingHandler.assertJobRecord(1, taskKey, "CREATED");
    }

    @Test
    public void shouldReceiveEventsCreatedBeforeSubscription() throws IOException
    {
        // given
        final JobEvent job = client.jobClient().newCreateCommand()
                .jobType("foo")
                .send()
                .join();

        // when
        client.subscriptionClient().newTopicSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(recordingHandler)
            .startAtHeadOfTopic()
            .open();

        // then
        waitUntil(() -> recordingHandler.numJobRecords() == 2);

        assertThat(recordingHandler.numJobRecords()).isEqualTo(2);

        final long jobKey = job.getKey();
        recordingHandler.assertJobRecord(0, jobKey, "CREATE");
        recordingHandler.assertJobRecord(1, jobKey, "CREATED");
    }

    @Test
    public void shouldReceiveEventsFromTailOfLog() throws IOException
    {
        client.jobClient().newCreateCommand()
            .jobType("foo")
            .send()
            .join();

        client.subscriptionClient().newTopicSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(recordingHandler)
            .startAtTailOfTopic()
            .open();

        // when
        final JobEvent job2 = client.jobClient().newCreateCommand()
            .jobType("foo")
            .send()
            .join();

        // then

        waitUntil(() -> recordingHandler.numJobRecords() >= 2);

        assertThat(recordingHandler.numJobRecords()).isEqualTo(2);

        // task 1 has not been received
        final long job2Key = job2.getKey();
        recordingHandler.assertJobRecord(0, job2Key, "CREATE");
        recordingHandler.assertJobRecord(1, job2Key, "CREATED");
    }

    @Test
    public void shouldReceiveEventsFromPosition() throws IOException
    {
        client.jobClient().newCreateCommand()
            .jobType("foo")
            .send()
            .join();

        client.subscriptionClient().newTopicSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(recordingHandler)
            .startAtHeadOfTopic()
            .open();

        waitUntil(() -> recordingHandler.numJobRecords() == 2);

        final List<Record> recordedTaskEvents = recordingHandler.getRecords().stream()
                .filter((re) -> re.getMetadata().getValueType() == RecordMetadata.ValueType.JOB)
                .collect(Collectors.toList());

        final RecordingEventHandler subscription2Handler = new RecordingEventHandler();
        final long secondTaskEventPosition = recordedTaskEvents.get(1).getMetadata().getPosition();

        // when
        client.subscriptionClient().newTopicSubscription()
            .name("another" + SUBSCRIPTION_NAME)
            .recordHandler(subscription2Handler)
            .startAtPosition(clientRule.getDefaultPartition(), secondTaskEventPosition)
            .open();

        // then
        waitUntil(() -> subscription2Handler.numRecords() > 0);

        // only the second event is pushed to the second subscription
        final Record firstEvent = subscription2Handler.getRecords().get(0);
        assertThat(firstEvent.getMetadata().getPosition()).isEqualTo(secondTaskEventPosition);

    }

    @Test
    public void shouldReceiveEventsFromPositionBeyondTail()
    {
        // given
        client.subscriptionClient().newTopicSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(recordingHandler)
            .startAtPosition(clientRule.getDefaultPartition(), Long.MAX_VALUE)
            .open();

        client.jobClient().newCreateCommand()
            .jobType("foo")
            .send()
            .join();

        // then
        waitUntil(() -> recordingHandler.numJobRecords() == 2);

        // the events are nevertheless received, although they have a lower position
        assertThat(recordingHandler.numJobRecords() == 2);
    }

    @Test
    public void shouldCloseSubscription() throws InterruptedException
    {
        // given
        final TopicSubscription subscription = client.subscriptionClient().newTopicSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(recordingHandler)
            .open();

        // when
        subscription.close();

        // then
        assertThat(subscription.isOpen()).isFalse();

        client.jobClient().newCreateCommand()
            .jobType("foo")
            .send()
            .join();

        Thread.sleep(1000L);
        assertThat(recordingHandler.numJobRecords()).isEqualTo(0);
    }

    @Test
    public void shouldRepeatedlyRecoverSubscription() throws InterruptedException
    {
        for (int i = 0; i < 100; i++)
        {
            // given
            final TopicSubscription subscription = client.subscriptionClient().newTopicSubscription()
                    .name(SUBSCRIPTION_NAME)
                    .recordHandler(recordingHandler)
                    .open();

            client.jobClient().newCreateCommand()
                .jobType("foo")
                .send()
                .join();

            final int eventCount = i;
            waitUntil(() -> recordingHandler.numJobRecords() >= eventCount);

            // when
            subscription.close();

            // then
            assertThat(subscription.isOpen()).isFalse();
        }

        assertThat(recordingHandler.numRecords()).isGreaterThan(100);
    }

    @Test
    public void shouldOpenMultipleSubscriptionsOnSameTopic() throws IOException
    {
        // given
        final JobEvent job = client.jobClient().newCreateCommand()
            .jobType("foo")
            .send()
            .join();

        client.subscriptionClient().newTopicSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(recordingHandler)
            .startAtHeadOfTopic()
            .open();

        final RecordingEventHandler secondEventHandler = new RecordingEventHandler();

        client.subscriptionClient().newTopicSubscription()
            .name("another" + SUBSCRIPTION_NAME)
            .recordHandler(secondEventHandler)
            .startAtHeadOfTopic()
            .open();

        // when
        waitUntil(() -> recordingHandler.numJobRecords() == 2);
        waitUntil(() -> secondEventHandler.numJobRecords() == 2);

        // then
        final long jobKey = job.getKey();
        recordingHandler.assertJobRecord(0, jobKey, "CREATE");
        recordingHandler.assertJobRecord(1, jobKey, "CREATED");
        secondEventHandler.assertJobRecord(0, jobKey, "CREATE");
        secondEventHandler.assertJobRecord(1, jobKey, "CREATED");
    }

    @Test
    public void shouldHandleOneEventAtATime() throws InterruptedException
    {
        client.jobClient().newCreateCommand()
            .jobType("foo")
            .send()
            .join();

        final Duration handlingIntervalLength = Duration.ofSeconds(1);
        final ParallelismDetectionHandler handler = new ParallelismDetectionHandler(handlingIntervalLength);

        // when
        client.subscriptionClient().newTopicSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(handler)
            .startAtHeadOfTopic()
            .open();

        // then
        final int numExpectedEvents = 2;
        Thread.sleep(handlingIntervalLength.toMillis() * numExpectedEvents);

        // at least CREATE and CREATED of the task, but we may have already handled a third event (e.g. raft)
        waitUntil(() -> handler.numInvocations() >= numExpectedEvents);
        assertThat(handler.hasDetectedParallelism()).isFalse();
    }

    @Test
    public void shouldResumeSubscription()
    {
        client.jobClient().newCreateCommand()
            .jobType("foo")
            .send()
            .join();

        final TopicSubscription subscription = client.subscriptionClient().newTopicSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(recordingHandler)
            .startAtHeadOfTopic()
            .open();

        // that was received by the subscription
        waitUntil(() -> recordingHandler.numJobRecords() == 2);

        subscription.close();

        final long lastEventPosition = recordingHandler.getRecords()
                .get(recordingHandler.numRecords() - 1)
                .getMetadata()
                .getPosition();

        recordingHandler.reset();

        // and a second not-yet-received job
        client.jobClient().newCreateCommand()
            .jobType("foo")
            .send()
            .join();

        // when
        client.subscriptionClient().newTopicSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(recordingHandler)
            .startAtHeadOfTopic()
            .open();

        // then
        waitUntil(() -> recordingHandler.numRecords() > 0);

        final long firstEventPositionAfterReopen = recordingHandler.getRecords()
                .get(0)
                .getMetadata()
                .getPosition();

        assertThat(firstEventPositionAfterReopen).isGreaterThan(lastEventPosition);
    }

    protected static class ParallelismDetectionHandler implements RecordHandler
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
        public void onRecord(Record record) throws Exception
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
        client.subscriptionClient().newTopicSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(recordingHandler)
            .open();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Could not open subscriber group");

        // when
        client.subscriptionClient().newTopicSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(recordingHandler)
            .open();
    }

    @Test
    public void testSubscriptionsWithSameNameOnDifferentTopic()
    {
        // given
        final ZeebeClient zeebeClient = clientRule.getClient();

        final String anotherTopicName = "another-topic";
        zeebeClient.newCreateTopicCommand()
            .name(anotherTopicName)
            .partitions(1)
            .replicationFactor(1)
            .send()
            .join();

        clientRule.waitUntilTopicsExists(anotherTopicName);

        zeebeClient.topicClient(clientRule.getDefaultTopic()).subscriptionClient()
            .newTopicSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(recordingHandler)
            .open();

        // when
        final TopicSubscription topic2Subscription = zeebeClient.topicClient(anotherTopicName).subscriptionClient()
            .newTopicSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(recordingHandler)
            .open();

        // then
        assertThat(topic2Subscription.isOpen()).isTrue();
    }

    @Test
    public void testSubscriptionsWithSameNameOnDifferentTopicShouldReceiveRespectiveEvents()
    {
        // given
        final ZeebeClient zeebeClient = clientRule.getClient();
        final String anotherTopicName = "another-topic";
        zeebeClient.newCreateTopicCommand()
            .name(anotherTopicName)
            .partitions(1)
            .replicationFactor(1)
            .send()
            .join();
        clientRule.waitUntilTopicsExists(anotherTopicName);
        final int anotherPartitionId = 2;

        final TopicClient topicClient0 = zeebeClient.topicClient(clientRule.getDefaultTopic());
        final TopicClient topicClient1 = zeebeClient.topicClient(anotherTopicName);

        final TopicSubscription topic0Subscription = topicClient0.subscriptionClient()
            .newTopicSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(recordingHandler)
            .startAtHeadOfTopic()
            .open();

        final RecordingEventHandler anotherRecordingHandler = new RecordingEventHandler();

        final TopicSubscription topic1Subscription = topicClient1.subscriptionClient()
            .newTopicSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(anotherRecordingHandler)
            .startAtHeadOfTopic()
            .open();

        // when
        topicClient0.jobClient()
            .newCreateCommand()
            .jobType("foo")
            .send()
            .join();

        topicClient1.jobClient()
            .newCreateCommand()
            .jobType("bar")
            .send()
            .join();

        // then
        waitUntil(() -> recordingHandler.numJobRecords() >= 2);
        waitUntil(() -> anotherRecordingHandler.numJobRecords() >= 2);

        topic0Subscription.close();
        topic1Subscription.close();

        Set<String> receivedTopicNamesSubscription = recordingHandler.getRecords().stream()
            .filter((re) -> re.getMetadata().getValueType() == RecordMetadata.ValueType.JOB)
            .map((re) -> re.getMetadata().getTopicName())
            .collect(Collectors.toSet());

        Set<Integer> receivedPartitionIdsSubscription = recordingHandler.getRecords().stream()
            .filter((re) -> re.getMetadata().getValueType() == RecordMetadata.ValueType.JOB)
            .map((re) -> re.getMetadata().getPartitionId())
            .collect(Collectors.toSet());

        assertThat(receivedTopicNamesSubscription).containsExactly(clientRule.getDefaultTopic());
        assertThat(receivedPartitionIdsSubscription).containsExactly(clientRule.getDefaultPartition());

        receivedTopicNamesSubscription = anotherRecordingHandler.getRecords().stream()
            .filter((re) -> re.getMetadata().getValueType() == RecordMetadata.ValueType.JOB)
            .map((re) -> re.getMetadata().getTopicName())
            .collect(Collectors.toSet());

        receivedPartitionIdsSubscription = anotherRecordingHandler.getRecords().stream()
            .filter((re) -> re.getMetadata().getValueType() == RecordMetadata.ValueType.JOB)
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
        client.subscriptionClient().newTopicSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(recordingHandler)
            .open();

        client.jobClient().newCreateCommand()
            .jobType("foo")
            .send()
            .join();

        // then
        waitUntil(() -> recordingHandler.numJobRecords() == 2);

        assertThat(recordingHandler.getRecords())
            .filteredOn((re) -> re.getMetadata().getValueType() == RecordMetadata.ValueType.UNKNOWN)
            .isEmpty();
    }

    @Test
    public void shouldReceiveMoreEventsThanSubscriptionCapacity()
    {
        // given
        final int subscriptionCapacity = clientRule.getClient().getConfiguration().getTopicSubscriptionPrefetchCapacity();

        for (int i = 0; i < subscriptionCapacity + 1; i++)
        {
            client.jobClient().newCreateCommand()
                .jobType("foo")
                .send()
                .join();
        }

        // when
        client.subscriptionClient().newTopicSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(recordingHandler)
            .startAtHeadOfTopic()
            .open();

        // then
        waitUntil(() -> recordingHandler.numRecords() > subscriptionCapacity);
    }

    @Test
    public void shouldReceiveEventsFromMultiplePartitions()
    {
        // given
        final ZeebeClient zeebeClient = clientRule.getClient();

        final String topicName = "pasta al forno";
        final int numPartitions = 2;
        zeebeClient.newCreateTopicCommand()
            .name(topicName)
            .partitions(numPartitions)
            .replicationFactor(1)
            .send()
            .join();

        clientRule.waitUntilTopicsExists(topicName);

        final Topics topics = zeebeClient.newTopicsRequest().send().join();
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
        zeebeClient.topicClient(topicName).subscriptionClient()
            .newTopicSubscription()
            .name(SUBSCRIPTION_NAME)
            .recordHandler(recordingHandler)
            .jobCommandHandler(c ->
            {
                if (c.getName() == JobCommand.JobCommandName.CREATE)
                {
                    receivedPartitionIds.add(c.getMetadata().getPartitionId());
                }
            })
            .startAtHeadOfTopic()
            .open();

        // then
        waitUntil(() -> receivedPartitionIds.size() == numPartitions);

        assertThat(receivedPartitionIds).containsExactlyInAnyOrder(partitionIds);
    }

    protected void createTaskOnPartition(String topic, int partition)
    {
        final CreateJobCommandImpl createCommand = (CreateJobCommandImpl) clientRule.getClient().topicClient(topic)
            .jobClient()
            .newCreateCommand()
            .jobType("baz");

        createCommand.getCommand().setPartitionId(partition);
        createCommand.send().join();
    }

}
