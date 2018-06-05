/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.job;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.broker.job.processor.JobSubscription;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ControlMessageRequestBuilder;
import io.zeebe.test.broker.protocol.clientapi.ControlMessageResponse;
import io.zeebe.test.broker.protocol.clientapi.ErrorResponse;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.StringUtil;


public class JobSubscriptionTest
{
    public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
    public ClientApiRule apiRule = new ClientApiRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

    private TestTopicClient testClient;

    @Before
    public void setUp()
    {
        testClient = apiRule.topic();
    }

    @Test
    public void shouldAddJobSubscription() throws InterruptedException
    {
        // given
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_JOB_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("jobType", "foo")
                .put("timeout", 10000L)
                .put("worker", "bar")
                .put("credits", 5)
                .done()
            .send();

        // when
        final ExecuteCommandResponse response = testClient.createJob("foo");

        // then
        final SubscribedRecord jobEvent = testClient.receiveEvents()
                .ofTypeJob()
                .withIntent(JobIntent.ACTIVATED)
                .getFirst();
        assertThat(jobEvent.key()).isEqualTo(response.key());
        assertThat(jobEvent.position()).isGreaterThan(response.position());
        assertThat(jobEvent.timestamp()).isGreaterThanOrEqualTo(response.timestamp());
        assertThat(jobEvent.value())
            .containsEntry("type", "foo")
            .containsEntry("retries", 3)
            .containsEntry("worker", "bar");

        final List<Intent> jobStates = testClient
            .receiveRecords()
            .ofTypeJob()
            .limit(4)
            .map(e -> e.intent())
            .collect(Collectors.toList());

        assertThat(jobStates).containsExactly(JobIntent.CREATE, JobIntent.CREATED, JobIntent.ACTIVATE, JobIntent.ACTIVATED);
    }

    @Test
    public void shouldRemoveJobSubscription()
    {
        // given
        final ControlMessageResponse openResponse = apiRule.openJobSubscription("foo").await();
        final int subscriberKey = (int) openResponse.getData().get("subscriberKey");

        // when
        final ControlMessageResponse closeResponse = apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.REMOVE_JOB_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("subscriberKey", subscriberKey)
                .done()
            .send()
            .await();

        assertThat(closeResponse.getData()).containsEntry("subscriberKey", subscriberKey);
    }

    @Test
    public void shouldNoLongerActivateJobsAfterRemoval() throws InterruptedException
    {
        // given
        final String jobType = "foo";
        final ControlMessageResponse openResponse = apiRule.openJobSubscription(jobType).await();
        final int subscriberKey = (int) openResponse.getData().get("subscriberKey");

        apiRule.closeJobSubscription(subscriberKey).await();

        // when
        testClient.createJob(jobType);

        // then
        apiRule.openTopicSubscription("test", 0).await();

        Thread.sleep(500L);

        final int eventsAvailable = apiRule.numSubscribedEventsAvailable();
        final List<SubscribedRecord> receivedEvents = apiRule.subscribedEvents().limit(eventsAvailable).collect(Collectors.toList());

        assertThat(receivedEvents).hasSize(2);
        assertThat(receivedEvents).allMatch(e -> e.subscriptionType() == SubscriptionType.TOPIC_SUBSCRIPTION);
        assertThat(receivedEvents).extracting(r -> r.intent())
            .containsExactly(JobIntent.CREATE, JobIntent.CREATED); // no more ACTIVATE etc.
    }

    @Test
    public void shouldRejectSubscriptionWithZeroCredits()
    {
        // given
        final ControlMessageRequestBuilder request = apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_JOB_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("jobType", "foo")
                .put("timeout", 10000L)
                .put("worker", "bar")
                .put("credits", 0)
                .done();
        // when
        final ErrorResponse errorResponse = request
            .send()
            .awaitError();

        // then
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorResponse.getErrorData()).isEqualTo("Cannot add job subscription. subscription credits must be greater than 0");
    }

    @Test
    public void shouldRejectSubscriptionWithNegativeCredits()
    {
        // given
        final ControlMessageRequestBuilder request = apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_JOB_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("jobType", "foo")
                .put("timeout", 10000L)
                .put("worker", "bar")
                .put("credits", -1)
                .done();
        // when
        final ErrorResponse errorResponse = request
            .send()
            .awaitError();

        // then
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorResponse.getErrorData()).isEqualTo("Cannot add job subscription. subscription credits must be greater than 0");
    }

    @Test
    public void shouldRejectSubscriptionWithoutWorker()
    {
        // given
        final ControlMessageRequestBuilder request = apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_JOB_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("jobType", "foo")
                .put("timeout", 10000L)
                .put("credits", 5)
                .done();
        // when
        final ErrorResponse errorResponse = request
            .send()
            .awaitError();

        // then
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorResponse.getErrorData()).isEqualTo("Cannot add job subscription. worker must not be empty");
    }

    @Test
    public void shouldRejectSubscriptionWithExcessiveWorkerName()
    {
        // given
        final String worker = StringUtil.stringOfLength(JobSubscription.WORKER_MAX_LENGTH + 1);

        final ControlMessageRequestBuilder request = apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_JOB_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("jobType", "foo")
                .put("timeout", 10000L)
                .put("worker", worker)
                .put("credits", 5)
                .done();
        // when
        final ErrorResponse errorResponse = request
            .send()
            .awaitError();

        // then
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorResponse.getErrorData()).isEqualTo("Cannot add job subscription. length of worker must be less than or equal to 64");
    }

    @Test
    public void shouldRejectSubscriptionWithZeroTimeout()
    {
        // given
        final ControlMessageRequestBuilder request = apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_JOB_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("jobType", "foo")
                .put("timeout", 0)
                .put("worker", "bar")
                .put("credits", 5)
                .done();
        // when
        final ErrorResponse errorResponse = request
            .send()
            .awaitError();

        // then
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorResponse.getErrorData()).isEqualTo("Cannot add job subscription. timeout must be greater than 0");
    }

    @Test
    public void shouldRejectSubscriptionWithNegativeTimeout()
    {
        // given
        final ControlMessageRequestBuilder request = apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_JOB_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("jobType", "foo")
                .put("timeout", -1)
                .put("worker", "bar")
                .put("credits", 5)
                .done();
        // when
        final ErrorResponse errorResponse = request
            .send()
            .awaitError();

        // then
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(errorResponse.getErrorData()).isEqualTo("Cannot add job subscription. timeout must be greater than 0");
    }

    @Test
    public void shouldDistributeJobsInRoundRobinFashion()
    {
        // given
        final String jobType = "foo";
        final int subscriber1 = (int) apiRule
                .openJobSubscription(jobType)
                .await()
                .getData()
                .get("subscriberKey");
        final int subscriber2 = (int) apiRule
                .openJobSubscription(jobType)
                .await()
                .getData()
                .get("subscriberKey");

        // when
        testClient.createJob(jobType);
        testClient.createJob(jobType);
        testClient.createJob(jobType);
        testClient.createJob(jobType);

        // then
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 4);

        final List<SubscribedRecord> receivedEvents = apiRule.subscribedEvents().limit(4)
                .collect(Collectors.toList());

        final long firstReceivingSubscriber = receivedEvents.get(0).subscriberKey();
        final long secondReceivingSubscriber = firstReceivingSubscriber == subscriber1 ? subscriber2 : subscriber1;

        assertThat(receivedEvents).extracting(e -> e.subscriberKey()).containsExactly(
                firstReceivingSubscriber,
                secondReceivingSubscriber,
                firstReceivingSubscriber,
                secondReceivingSubscriber);
    }

    @Test
    public void shouldCloseSubscriptionOnTransportChannelClose() throws InterruptedException
    {
        // given
        apiRule
            .openJobSubscription("foo")
            .await();

        // when the transport channel is closed
        apiRule.interruptAllChannels();

        // then the subscription has been closed, so we can create a new job and activate it for a new subscription
        Thread.sleep(1000L); // closing subscriptions happens asynchronously

        final ExecuteCommandResponse response = testClient.createJob("foo");

        final ControlMessageResponse subscriptionResponse = apiRule
            .openJobSubscription("foo")
            .await();
        final int secondSubscriberKey = (int) subscriptionResponse.getData().get("subscriberKey");

        final Optional<SubscribedRecord> jobEvent = apiRule.subscribedEvents()
            .filter((s) -> s.subscriptionType() == SubscriptionType.JOB_SUBSCRIPTION
                && s.key() == response.key())
            .findFirst();

        assertThat(jobEvent).isPresent();
        assertThat(jobEvent.get().subscriberKey()).isEqualTo(secondSubscriberKey);
    }

    /**
     * This tries to provoke a race condition in JobSubscriptionManager with respect to
     * stream processor management. The concurrent behavior is:
     *
     * <ul>
     * <li>Close subscription => closes job activation stream processor asynchronously because
     *   this is the last subscription for the job type
     * <li>Open subscription => Registers subscription with job activation stream processor
     *
     * <p>Expected behavior: There is a running stream processor for the opened subscription.
     *
     * <p>Current behavior: Due to a race condition, it is possible that the opened subscription
     *   is still registered with the closing stream processor. The result is a confirmed open subscription
     *   that receives no jobs.
     */
    @Test
    public void shouldOpenSubscriptionConcurrentWithRemoval() throws InterruptedException
    {
        // given
        final ControlMessageResponse openResponse = apiRule
            .openJobSubscription("foo")
            .await();
        final int subscriberKey = (int) openResponse.getData().get("subscriberKey");

        // when doing concurrent close and open
        apiRule.closeJobSubscription(subscriberKey); // async important to reproduce race condition
        final ControlMessageResponse reopenResponse = apiRule.openJobSubscription("foo").await();

        // then the open response should be success
        assertThat(reopenResponse.getData()).containsKey("subscriberKey");
        final int secondSubscriberKey = (int) reopenResponse.getData().get("subscriberKey");

        // and we should be able to receive jobs for the new subscription
        final ExecuteCommandResponse response = testClient.createJob("foo");
        final Optional<SubscribedRecord> jobEvent = apiRule.subscribedEvents()
            .filter((s) -> s.subscriptionType() == SubscriptionType.JOB_SUBSCRIPTION
                && s.key() == response.key())
            .findFirst();

        assertThat(jobEvent).isPresent();
        assertThat(jobEvent.get().subscriberKey()).isEqualTo(secondSubscriberKey);
    }

    @Test
    public void shouldContinueRoundRobinJobDistributionAfterClientChannelClose()
    {
        // given
        apiRule
            .openJobSubscription("foo")
            .await();

        apiRule
            .openJobSubscription("foo")
            .await();

        testClient.createJob("foo");
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 1);

        openAndCloseConnectionTo(apiRule.getBrokerAddress());

        // when
        testClient.createJob("foo");
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 2);

        // then
        final List<SubscribedRecord> events = apiRule.subscribedEvents().limit(2).collect(Collectors.toList());
        assertThat(events.get(0).subscriberKey()).isNotEqualTo(events.get(1).subscriberKey());

    }

    protected void openAndCloseConnectionTo(SocketAddress remote)
    {
        try
        {
            final SocketChannel media = SocketChannel.open();
            media.setOption(StandardSocketOptions.TCP_NODELAY, true);
            media.configureBlocking(true);
            media.connect(remote.toInetSocketAddress());
            media.close();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void shouldRejectCreditsEqualToZero()
    {
        // when
        final ErrorResponse error = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.INCREASE_JOB_SUBSCRIPTION_CREDITS)
            .data()
                .put("subscriberKey", 1)
                .put("credits", 0)
                .put("partitionId", apiRule.getDefaultPartitionId())
                .done()
            .send().awaitError();

        // then
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(error.getErrorData()).isEqualTo("Cannot increase job subscription credits. Credits must be positive.");
    }


    @Test
    public void shouldRejectNegativeCredits()
    {
        // when
        final ErrorResponse error = apiRule.createControlMessageRequest()
            .messageType(ControlMessageType.INCREASE_JOB_SUBSCRIPTION_CREDITS)
            .data()
                .put("subscriberKey", 1)
                .put("credits", -10)
                .put("partitionId", apiRule.getDefaultPartitionId())
                .done()
            .send().awaitError();

        // then
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PROCESSING_FAILURE);
        assertThat(error.getErrorData()).isEqualTo("Cannot increase job subscription credits. Credits must be positive.");
    }

    @Test
    public void shouldAddJobSubscriptionsForDifferentTypes()
    {
        // given
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_JOB_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("jobType", "foo")
                .put("timeout", 1000L)
                .put("worker", "owner1")
                .put("credits", 5)
                .done()
            .send();

        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_JOB_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("jobType", "bar")
                .put("timeout", 1000L)
                .put("worker", "owner2")
                .put("credits", 5)
                .done()
            .send();

        // when
        testClient.createJob("foo");
        testClient.createJob("bar");

        // then
        final List<SubscribedRecord> jobEvents = testClient.receiveEvents()
                .ofTypeJob()
                .withIntent(JobIntent.ACTIVATED)
                .limit(2)
                .collect(Collectors.toList());

        assertThat(jobEvents).hasSize(2);

        final Function<String, Optional<SubscribedRecord>> findJobRecordForType = (jobType) -> jobEvents.stream().filter(j -> jobType.equals(j.value().get("type"))).findFirst();

        assertThat(findJobRecordForType.apply("foo"))
            .hasValueSatisfying(record -> assertThat(record.value()).containsEntry("worker", "owner1"));

        assertThat(findJobRecordForType.apply("bar"))
            .hasValueSatisfying(record -> assertThat(record.value()).containsEntry("worker", "owner2"));
    }

    @Test
    public void shouldActivateJobsUntilCreditsAreExhausted() throws InterruptedException
    {
        // given
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_JOB_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("jobType", "foo")
                .put("timeout", 1000L)
                .put("worker", "bar")
                .put("credits", 2)
                .done()
            .send();

        // when
        testClient.createJob("foo");
        testClient.createJob("foo");
        testClient.createJob("foo");

        // then
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 2);

        final List<SubscribedRecord> jobEvents = testClient.receiveEvents()
                .ofTypeJob()
                .withIntent(JobIntent.ACTIVATED)
                .limit(2)
                .collect(Collectors.toList());

        assertThat(jobEvents)
            .extracting(s -> s.value().get("worker"))
            .contains("bar");
    }

    @Test
    public void shouldIncreaseSubscriptionCredits() throws InterruptedException
    {
        // given
        final ControlMessageResponse response = apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.ADD_JOB_SUBSCRIPTION)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("jobType", "foo")
                .put("timeout", 1000L)
                .put("worker", "bar")
                .put("credits", 2)
                .done()
            .sendAndAwait();

        assertThat(response.getData().get("subscriberKey")).isEqualTo(0);

        testClient.createJob("foo");
        testClient.createJob("foo");

        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 2);

        testClient.createJob("foo");
        testClient.createJob("foo");

        // when
        apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.INCREASE_JOB_SUBSCRIPTION_CREDITS)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("subscriberKey", 0)
                .put("credits", 2)
                .put("partitionId", apiRule.getDefaultPartitionId())
                .done()
            .send();

        // then
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 4);
    }

    @Test
    public void shouldIgnoreCreditsRequestIfSubscriptionDoesNotExist()
    {
        // given
        final int nonExistingSubscriberKey = 444;
        final ControlMessageRequestBuilder request = apiRule
            .createControlMessageRequest()
            .messageType(ControlMessageType.INCREASE_JOB_SUBSCRIPTION_CREDITS)
            .partitionId(apiRule.getDefaultPartitionId())
            .data()
                .put("subscriberKey", nonExistingSubscriberKey)
                .put("credits", 2)
                .put("partitionId", apiRule.getDefaultPartitionId())
                .done();

        // when
        final ControlMessageResponse response = request.sendAndAwait();

        // then
        assertThat(response.getData()).containsEntry("subscriberKey", nonExistingSubscriberKey);
    }

    @Test
    public void shouldNotPublishJobWithoutRetries() throws InterruptedException
    {
        // given
        final String jobType = "foo";
        apiRule.openJobSubscription(jobType).await();

        testClient.createJob(jobType);

        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 1);
        final SubscribedRecord job = apiRule.subscribedEvents().findFirst().get();

        // when
        final Map<String, Object> event = new HashMap<>(job.value());
        event.put("retries", 0);
        testClient.failJob(job.key(), event);

        // then
        Thread.sleep(500);

        assertThat(apiRule.numSubscribedEventsAvailable()).isEqualTo(0);
    }

    @Test
    public void shouldNotPublishJobOfDifferentType() throws InterruptedException
    {
        // given
        final String jobType = "foo";
        apiRule.openJobSubscription(jobType).await();

        // when
        testClient.createJob("bar");

        // then
        Thread.sleep(500);
        assertThat(apiRule.numSubscribedEventsAvailable()).isEqualTo(0);
    }

    @Test
    public void shouldPublishJobsToSecondSubscription()
    {
        // given
        final int credits = 2;
        final String jobType = "foo";
        apiRule.openJobSubscription(apiRule.getDefaultPartitionId(), jobType, 10000, credits).await();

        for (int i = 0; i < credits; i++)
        {
            testClient.createJob(jobType);
        }

        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == credits);

        apiRule.moveMessageStreamToTail();

        final int secondSubscriber = (int) apiRule
                .openJobSubscription(apiRule.getDefaultPartitionId(), jobType, 10000, credits)
                .await()
                .getData()
                .get("subscriberKey");

        // when
        testClient.createJob(jobType);

        // then
        waitUntil(() -> apiRule.numSubscribedEventsAvailable() == 1);

        final SubscribedRecord subscribedEvent = apiRule.subscribedEvents().findFirst().get();
        assertThat(subscribedEvent.subscriberKey()).isEqualTo(secondSubscriber);
    }

}
