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
package io.zeebe.client.job.subscription;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientConfiguration;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.subscription.JobHandler;
import io.zeebe.client.api.subscription.JobSubscription;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.impl.subscription.Subscriber;
import io.zeebe.client.impl.subscription.job.IncreaseJobSubscriptionCreditsCmdImpl;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.clientapi.*;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.test.broker.protocol.brokerapi.*;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.RemoteAddress;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class JobSubscriptionTest
{
    private static final int NUM_EXECUTION_THREADS = 2;

    private static final JobHandler DO_NOTHING = (c, t) ->
    { };

    public ClientRule clientRule = new ClientRule(b -> b.numSubscriptionExecutionThreads(NUM_EXECUTION_THREADS));
    public StubBrokerRule broker = new StubBrokerRule();

    protected final MsgPackConverter msgPackConverter = new MsgPackConverter();

    protected final Object monitor = new Object();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(broker).around(clientRule);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected ZeebeClient client;

    @Before
    public void setUp()
    {
        this.client = clientRule.getClient();
    }

    @After
    public void after()
    {
        continueJobHandlingThreads();
    }

    @Test
    public void shouldOpenSubscription()
    {
        // given
        broker.stubJobSubscriptionApi(123L);

        // when
        final JobSubscription subscription =
            clientRule.subscriptionClient()
                .newJobSubscription()
                .jobType("bar")
                .handler(DO_NOTHING)
                .lockOwner("foo")
                .lockTime(10000L)
                .fetchSize(456)
                .open();

        // then
        assertThat(subscription.isOpen()).isTrue();
        assertThat(subscription.isClosed()).isFalse();

        final ControlMessageRequest subscriptionRequest = getSubscribeRequests().findFirst().get();
        assertThat(subscriptionRequest.messageType()).isEqualByComparingTo(ControlMessageType.ADD_JOB_SUBSCRIPTION);
        assertThat(subscriptionRequest.partitionId()).isEqualTo(clientRule.getDefaultPartitionId());

        assertThat(subscriptionRequest.getData()).contains(
                entry("lockOwner", "foo"),
                entry("lockDuration", 10000),
                entry("jobType", "bar"),
                entry("credits", 456));
    }

    @Test
    public void shouldCloseSubscription()
    {
        // given
        broker.stubJobSubscriptionApi(123L);

        final JobSubscription subscription =
            clientRule.subscriptionClient()
                .newJobSubscription()
                .jobType("bar")
                .handler(DO_NOTHING)
                .lockOwner("foo")
                .lockTime(10000L)
                .open();

        // when
        subscription.close();

        // then
        assertThat(subscription.isClosed()).isTrue();
        assertThat(subscription.isOpen()).isFalse();

        final ControlMessageRequest subscriptionRequest = getUnsubscribeRequests().findFirst().get();
        assertThat(subscriptionRequest.messageType()).isEqualByComparingTo(ControlMessageType.REMOVE_JOB_SUBSCRIPTION);

        assertThat(subscriptionRequest.partitionId()).isEqualTo(clientRule.getDefaultPartitionId());
        assertThat(subscriptionRequest.getData()).contains(entry("subscriberKey", 123));
    }

    @Test
    public void shouldValidateNullJobType()
    {
        // given
        broker.stubJobSubscriptionApi(123L);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("jobType must not be null");

        // when
        clientRule.subscriptionClient()
            .newJobSubscription()
            .jobType(null)
            .handler(DO_NOTHING)
            .lockOwner("foo")
            .lockTime(10000L)
            .open();
    }

    @Test
    public void shouldValidateNullJobHandler()
    {
        // given
        broker.stubJobSubscriptionApi(123L);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("handler must not be null");

        // when
        clientRule.subscriptionClient()
            .newJobSubscription()
            .jobType("bar")
            .handler(null)
            .lockOwner("foo")
            .lockTime(10000L)
            .open();
    }

    @Test
    public void shouldUseDefaultJobFetchSize()
    {
        // given
        broker.stubJobSubscriptionApi(123L);

        // when
        clientRule.subscriptionClient()
            .newJobSubscription()
            .jobType("bar")
            .handler(DO_NOTHING)
            .lockOwner("foo")
            .lockTime(10000L)
            .open();

        // then
        final ControlMessageRequest subscriptionRequest = getSubscribeRequests().findFirst().get();
        assertThat(subscriptionRequest.messageType()).isEqualByComparingTo(ControlMessageType.ADD_JOB_SUBSCRIPTION);

        assertThat(subscriptionRequest.getData()).containsEntry("credits", 32);
    }

    @Test
    public void shouldValidateLockTimePositive()
    {
        // given
        broker.stubJobSubscriptionApi(123L);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("lockTime must be greater than 0");

        // when
        clientRule.subscriptionClient()
            .newJobSubscription()
            .jobType("bar")
            .handler(DO_NOTHING)
            .lockOwner("foo")
            .lockTime(-1L)
            .open();
    }

    @Test
    public void shouldOpenSubscriptionWithLockTimeAsDuration()
    {
        // given
        broker.stubJobSubscriptionApi(123L);

        // when
        clientRule.subscriptionClient()
            .newJobSubscription()
            .jobType("bar")
            .handler(DO_NOTHING)
            .lockOwner("foo")
            .lockTime(Duration.ofDays(10))
            .open();

        // then
        final ControlMessageRequest subscriptionRequest = getSubscribeRequests().findFirst().get();

        assertThat(subscriptionRequest.getData()).contains(
                entry("lockDuration", (int) TimeUnit.DAYS.toMillis(10L)));
    }

    @Test
    public void shouldThrowExceptionWhenSubscriptionCannotBeOpened()
    {
        // given
        broker.onControlMessageRequest(r -> r.messageType() == ControlMessageType.ADD_JOB_SUBSCRIPTION)
            .respondWithError()
            .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
            .errorData("does not compute")
            .register();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Could not open subscription");

        // when
        clientRule.subscriptionClient()
            .newJobSubscription()
            .jobType("bar")
            .handler(DO_NOTHING)
            .lockOwner("foo")
            .lockTime(Duration.ofDays(10))
            .open();
    }

    @Test
    public void shouldInvokeJobHandler() throws JsonParseException, JsonMappingException, IOException
    {
        // given
        broker.stubJobSubscriptionApi(123L);
        broker.jobs().registerCompleteCommand();

        final RecordingJobHandler handler = new RecordingJobHandler();
        clientRule.subscriptionClient()
            .newJobSubscription()
            .jobType("bar")
            .handler(handler)
            .lockOwner("foo")
            .lockTime(Duration.ofDays(10))
            .open();

        final RemoteAddress clientAddress = getSubscribeRequests().findFirst().get().getSource();

        final MsgPackHelper msgPackHelper = new MsgPackHelper();
        final Map<String, Object> jobPayload = new HashMap<>();
        jobPayload.put("payloadKey", "payloadValue");

        final Map<String, Object> jobHeaders = new HashMap<>();
        jobPayload.put("headerKey", "headerValue");
        final long lockTime = System.currentTimeMillis();

        // when
        broker.newSubscribedEvent()
            .partitionId(StubBrokerRule.TEST_PARTITION_ID)
            .key(4L)
            .position(5L)
            .recordType(RecordType.EVENT)
            .valueType(ValueType.JOB)
            .intent(JobIntent.LOCKED)
            .subscriberKey(123L)
            .subscriptionType(SubscriptionType.JOB_SUBSCRIPTION)
            .value()
                .put("type", "type")
                .put("lockTime", lockTime)
                .put("retries", 3)
                .put("payload", msgPackHelper.encodeAsMsgPack(jobPayload))
                .put("headers", jobHeaders)
                .done()
            .push(clientAddress);

        // then
        TestUtil.waitUntil(() -> !handler.getHandledJobs().isEmpty());

        assertThat(handler.getHandledJobs()).hasSize(1);

        final JobEvent job = handler.getHandledJobs().get(0);

        assertThat(job.getMetadata().getKey()).isEqualTo(4L);
        assertThat(job.getType()).isEqualTo("type");
        assertThat(job.getHeaders()).isEqualTo(jobHeaders);
        assertThat(job.getLockExpirationTime()).isEqualTo(Instant.ofEpochMilli(lockTime));

        final ObjectMapper objectMapper = new ObjectMapper();
        @SuppressWarnings("unchecked")
        final Map<String, Object> receivedPayload = objectMapper.readValue(job.getPayload(), Map.class);
        assertThat(receivedPayload).isEqualTo(jobPayload);
    }

    @Test
    public void shouldInvokeJobHandlerWithTwoSubscriptions()
    {
        // given
        broker.stubJobSubscriptionApi(123L);
        broker.jobs().registerCompleteCommand();

        final RecordingJobHandler handler1 = new RecordingJobHandler();
        clientRule.subscriptionClient()
            .newJobSubscription()
            .jobType("bar")
            .handler(handler1)
            .lockOwner("foo")
            .lockTime(Duration.ofDays(10))
            .open();

        final RecordingJobHandler handler2 = new RecordingJobHandler();

        clientRule.subscriptionClient()
            .newJobSubscription()
            .jobType("bar")
            .handler(handler2)
            .lockOwner("bar")
            .lockTime(Duration.ofDays(10))
            .open();

        final RemoteAddress clientAddress = getSubscribeRequests().findFirst().get().getSource();

        // when
        broker.pushLockedJob(clientAddress, 123L, 4L, 5L, "foo", "type1");
        broker.pushLockedJob(clientAddress, 124L, 5L, 6L, "bar", "type2");

        // then
        TestUtil.waitUntil(() -> !handler1.getHandledJobs().isEmpty());
        TestUtil.waitUntil(() -> !handler2.getHandledJobs().isEmpty());

        assertThat(handler1.getHandledJobs()).hasSize(1);
        assertThat(handler2.getHandledJobs()).hasSize(1);

        final JobEvent job1 = handler1.getHandledJobs().get(0);

        assertThat(job1.getMetadata().getKey()).isEqualTo(4L);
        assertThat(job1.getType()).isEqualTo("type1");

        final JobEvent job2 = handler2.getHandledJobs().get(0);

        assertThat(job2.getMetadata().getKey()).isEqualTo(5L);
        assertThat(job2.getType()).isEqualTo("type2");
    }

    @Test
    public void shouldNotAutocompleteJob() throws InterruptedException
    {
        // given
        broker.stubJobSubscriptionApi(123L);
        broker.jobs().registerCompleteCommand();

        final RecordingJobHandler handler = new RecordingJobHandler();
        clientRule.subscriptionClient()
            .newJobSubscription()
            .jobType("bar")
            .handler(handler)
            .lockOwner("foo")
            .lockTime(10000L)
            .open();

        final RemoteAddress clientAddress = getSubscribeRequests().findFirst().get().getSource();

        // when
        broker.pushLockedJob(clientAddress, 123L, 4L, 5L, "foo", "bar");

        // then
        Thread.sleep(1000L);

        assertThat(broker.getReceivedCommandRequests().stream()
                .filter(r -> r.valueType() == ValueType.JOB)
                .count()).isEqualTo(0);
    }

    @Test
    public void shouldCompleteJobWithPayload()
    {
        // given
        broker.stubJobSubscriptionApi(123L);
        broker.jobs().registerCompleteCommand();

        clientRule.subscriptionClient()
            .newJobSubscription()
            .jobType("bar")
            .handler((c, t) -> c.newCompleteCommand(t).payload("{\"a\": 1}").send().join())
            .lockOwner("foo")
            .lockTime(10000L)
            .open();

        final RemoteAddress eventSource = getSubscribeRequests().findFirst().get().getSource();

        // when
        broker.pushLockedJob(eventSource, 123L, 4L, 5L, "foo", "bar");

        // then
        final ExecuteCommandRequest jobRequest = TestUtil.doRepeatedly(() -> broker.getReceivedCommandRequests().stream()
                .filter(r -> r.valueType() == ValueType.JOB)
                .findFirst())
            .until(r -> r.isPresent())
            .get();

        assertThat(jobRequest.partitionId()).isEqualTo(clientRule.getDefaultPartitionId());
        assertThat(jobRequest.key()).isEqualTo(4L);
        assertThat(jobRequest.intent()).isEqualTo(JobIntent.COMPLETE);
        assertThat(jobRequest.getCommand())
            .containsEntry("type", "bar")
            .containsEntry("lockOwner", "foo")
            .containsEntry("payload", msgPackConverter.convertToMsgPack("{\"a\": 1}"));
    }

    @Test
    public void shouldCompleteJobWithoutPayload()
    {
        // given
        broker.stubJobSubscriptionApi(123L);
        broker.jobs().registerCompleteCommand();

        clientRule.subscriptionClient()
            .newJobSubscription()
            .jobType("bar")
            .handler((c, t) -> c.newCompleteCommand(t).withoutPayload().send().join())
            .lockOwner("foo")
            .lockTime(10000L)
            .open();

        final RemoteAddress eventSource = getSubscribeRequests().findFirst().get().getSource();

        // when
        broker.pushLockedJob(eventSource, 123L, 4L, 5L, "foo", "bar");

        // then
        final ExecuteCommandRequest jobRequest = TestUtil.doRepeatedly(() -> broker.getReceivedCommandRequests().stream()
                .filter(r -> r.valueType() == ValueType.JOB)
                .findFirst())
            .until(r -> r.isPresent())
            .get();

        assertThat(jobRequest.partitionId()).isEqualTo(clientRule.getDefaultPartitionId());
        assertThat(jobRequest.key()).isEqualTo(4L);
        assertThat(jobRequest.intent()).isEqualTo(JobIntent.COMPLETE);
        assertThat(jobRequest.getCommand())
            .containsEntry("type", "bar")
            .containsEntry("lockOwner", "foo")
            .doesNotContainKey("payload");
    }

    @Test
    public void shouldMarkJobAsFailedOnExpcetion()
    {
        // given
        broker.stubJobSubscriptionApi(123L);
        broker.jobs().registerFailCommand();

        clientRule.subscriptionClient()
            .newJobSubscription()
            .jobType("bar")
            .handler((c, t) ->
            {
                throw new RuntimeException("expected failure");
            })
            .lockOwner("foo")
            .lockTime(10000L)
            .open();

        final RemoteAddress clientAddress = getSubscribeRequests().findFirst().get().getSource();

        // when
        broker.pushLockedJob(clientAddress, 123L, 4L, 5L, "foo", "bar");

        // then
        final ExecuteCommandRequest jobRequest = TestUtil.doRepeatedly(() -> broker.getReceivedCommandRequests().stream()
                .filter(r -> r.valueType() == ValueType.JOB)
                .findFirst())
            .until(r -> r.isPresent())
            .get();

        assertThat(jobRequest.partitionId()).isEqualTo(clientRule.getDefaultPartitionId());
        assertThat(jobRequest.key()).isEqualTo(4L);
        assertThat(jobRequest.intent()).isEqualTo(JobIntent.FAIL);
        assertThat(jobRequest.getCommand())
            .containsEntry("type", "bar")
            .containsEntry("lockOwner", "foo");
    }

    @Test
    public void shouldCloseSubscriptionOnChannelClose() throws InterruptedException
    {
        // given
        broker.stubJobSubscriptionApi(123L);

        final JobSubscription subscription = clientRule.subscriptionClient()
            .newJobSubscription()
            .jobType("bar")
            .handler(DO_NOTHING)
            .lockOwner("foo")
            .lockTime(10000L)
            .open();

        // when
        broker.closeTransport();
        Thread.sleep(500L); // let subscriber attempt reopening
        clientRule.getClock().addTime(Duration.ofSeconds(60)); // make request time out immediately

        // then
        TestUtil.waitUntil(() -> subscription.isClosed());
        assertThat(subscription.isClosed()).isTrue();
    }


    protected void continueJobHandlingThreads()
    {
        synchronized (monitor)
        {
            monitor.notifyAll();
        }
    }


    /**
     * This tests a case that should not occur under normal circumstances, but might occur
     * in case of inconsistencies between broker and client state (e.g. due to bugs in either of them)
     */
    @Test
    public void shouldRetryWithMoreJobsThanSubscriptionCapacity() throws InterruptedException
    {
        // given
        broker.stubJobSubscriptionApi(123L);
        broker.jobs().registerCompleteCommand();

        final WaitingJobHandler handler = new WaitingJobHandler();
        final ZeebeClientConfiguration clientConfig = client.getConfiguration();
        final int numExecutionThreads = clientConfig.getNumSubscriptionExecutionThreads();
        final int jobCapacity = 4;

        clientRule.subscriptionClient()
            .newJobSubscription()
            .jobType("foo")
            .handler(handler)
            .lockOwner("owner")
            .lockTime(10000L)
            .open();

        final RemoteAddress clientAddress = broker.getReceivedControlMessageRequests().get(0).getSource();

        for (int i = 0; i < jobCapacity + numExecutionThreads; i++)
        {
            broker.pushLockedJob(clientAddress, 123L, i, i, "owner", "foo");
        }

        TestUtil.waitUntil(() -> handler.numWaitingThreads.get() > 0);

        // pushing one more event, exceeding client capacity
        broker.pushLockedJob(clientAddress, 123L, Integer.MAX_VALUE, Integer.MAX_VALUE, "owner", "foo");

        // waiting for the client to receive all pending jobs
        Thread.sleep(500L);

        // when
        handler.shouldWait = false;
        continueJobHandlingThreads();

        // then the additional event is handled nevertheless (i.e. client applies backpressure)
        TestUtil.waitUntil(() -> handler.numHandledEvents.get() == jobCapacity + numExecutionThreads + 1);
    }

    /**
     * i.e. if signalling job failure itself fails
     */
    @Test
    public void shouldNotLoseCreditsOnFailureToReportJobFailure() throws InterruptedException
    {
        // given
        broker.stubJobSubscriptionApi(123L);
        failJobFailure();

        final int subscriptionCapacity = 8;
        final AtomicInteger failedJobs = new AtomicInteger(0);

        final JobHandler jobHandler = (c, t) ->
        {
            failedJobs.incrementAndGet();
            throw new RuntimeException("foo");
        };

        clientRule.subscriptionClient()
            .newJobSubscription()
            .jobType("foo")
            .handler(jobHandler)
            .lockOwner("owner")
            .lockTime(10000L)
            .fetchSize(subscriptionCapacity)
            .open();

        final RemoteAddress clientAddress = broker.getReceivedControlMessageRequests().get(0).getSource();

        for (int i = 0; i < subscriptionCapacity; i++)
        {
            broker.pushLockedJob(clientAddress, 123L, i, i, "owner", "foo");
        }


        // when
        TestUtil.waitUntil(() -> failedJobs.get() == 8);
        // give the client a bit of time to submit credits; this is not coupled to any defined event, so we just sleep for a bit
        Thread.sleep(500L);

        // then
        final List<ControlMessageRequest> creditRequests = getCreditRequests().collect(Collectors.toList());

        assertThat(creditRequests).isNotEmpty();
        final int numSubmittedCredits = creditRequests.stream().mapToInt((r) -> (int) r.getData().get("credits")).sum();
        assertThat(numSubmittedCredits).isGreaterThan(0);
    }


    @Test
    public void shouldReopenSubscriptionAfterChannelInterruption()
    {
        // given
        broker.stubJobSubscriptionApi(123L);

        clientRule.subscriptionClient()
            .newJobSubscription()
            .jobType("foo")
            .handler(DO_NOTHING)
            .lockOwner("owner")
            .lockTime(10000L)
            .open();

        // when
        broker.interruptAllServerChannels();

        // then
        TestUtil.waitUntil(() -> getSubscribeRequests().count() == 2);

        final ControlMessageRequest reopenRequest = getSubscribeRequests().skip(1).findFirst().get();
        assertThat(reopenRequest.getData()).contains(
            entry("lockOwner", "owner"),
            entry("lockDuration", 10000),
            entry("jobType", "foo"));
    }

    @Test
    public void shouldSendCorrectCreditsRequest()
    {
        // given
        broker.stubJobSubscriptionApi(123L);

        // when
        new IncreaseJobSubscriptionCreditsCmdImpl(((ZeebeClientImpl) client).getCommandManager(), StubBrokerRule.TEST_PARTITION_ID)
            .subscriberKey(456L)
            .credits(123)
            .send()
            .join();

        // then
        final List<ControlMessageRequest> controlMessageRequests = broker.getReceivedControlMessageRequests()
                .stream()
                .filter(r -> r.messageType() == ControlMessageType.INCREASE_JOB_SUBSCRIPTION_CREDITS)
                .collect(Collectors.toList());

        assertThat(controlMessageRequests).hasSize(1);

        final ControlMessageRequest request = controlMessageRequests.get(0);
        assertThat(request.messageType()).isEqualTo(ControlMessageType.INCREASE_JOB_SUBSCRIPTION_CREDITS);
        assertThat(request.getData())
            .contains(
                    entry("credits", 123),
                    entry("subscriberKey", 456));
    }

    @Test
    public void shouldNotAttemptReplenishmentForZeroCredits() throws InterruptedException
    {
        // given
        final int subscriptionCapacity = 16;
        final int replenishmentThreshold = (int) (Math.ceil(subscriptionCapacity * Subscriber.REPLENISHMENT_THRESHOLD));
        final int jobsToHandleBeforeReplenishment = subscriptionCapacity - replenishmentThreshold;

        broker.stubJobSubscriptionApi(123L);

        final WaitingJobHandler handler = new WaitingJobHandler();
        handler.shouldWait = false;

        clientRule.subscriptionClient()
            .newJobSubscription()
            .jobType("type")
            .handler(handler)
            .lockOwner("owner")
            .lockTime(10000L)
            .fetchSize(subscriptionCapacity)
            .open();

        final RemoteAddress clientAddress = getSubscribeRequests().findFirst().get().getSource();

        // handling these jobs should not yet trigger replenishment; the next handled job would
        for (int i = 0; i < jobsToHandleBeforeReplenishment; i++)
        {
            broker.pushLockedJob(clientAddress, 123L, 4L + i, 5L + i, "foo", "type");
        }
        waitUntil(() -> handler.numHandledEvents.get() == jobsToHandleBeforeReplenishment);

        handler.shouldWait = true;
        for (int i = 0; i < NUM_EXECUTION_THREADS; i++)
        {
            broker.pushLockedJob(clientAddress, 123L, 4L + i, 5L + i, "foo", "type");
        }
        waitUntil(() -> handler.numWaitingThreads.get() == NUM_EXECUTION_THREADS);

        // when all job handling threads trigger credit replenishment
        continueJobHandlingThreads();

        // then
        waitUntil(() -> getCreditRequests().count() >= 1);

        Thread.sleep(500L); // waiting for potentially more credit requests
        final List<ControlMessageRequest> creditRequests = getCreditRequests().collect(Collectors.toList());
        assertThat(creditRequests.size()).isGreaterThanOrEqualTo(1);

        int totalReplenishedCredits = 0;
        for (ControlMessageRequest request : creditRequests)
        {
            final int replenishedCredits = (int) request.getData().get("credits");
            assertThat(replenishedCredits).isGreaterThan(0);
            totalReplenishedCredits += replenishedCredits;
        }

        assertThat(totalReplenishedCredits).isGreaterThanOrEqualTo(jobsToHandleBeforeReplenishment + 1);
    }

    @Test
    public void shouldApplyDefaultsToLockOwnerAndTime()
    {
        // given
        broker.stubJobSubscriptionApi(123L);

        // when
        clientRule.subscriptionClient()
            .newJobSubscription()
            .jobType("bar")
            .handler(DO_NOTHING)
            .open();

        // then
        final ControlMessageRequest subscriptionRequest = getSubscribeRequests().findFirst().get();
        assertThat(subscriptionRequest.messageType()).isEqualByComparingTo(ControlMessageType.ADD_JOB_SUBSCRIPTION);

        assertThat(subscriptionRequest.getData())
            .containsEntry("lockOwner", client.getConfiguration().getDefaultJobLockOwner())
            .containsEntry("lockDuration", (int) client.getConfiguration().getDefaultJobLockTime().toMillis());
    }

    protected void failJobFailure()
    {
        broker.onExecuteCommandRequest(ValueType.JOB, JobIntent.FAIL)
            .respondWithError()
            .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
            .errorData("failed to fail job")
            .register();
    }

    protected Stream<ControlMessageRequest> getSubscribeRequests()
    {
        return broker.getReceivedControlMessageRequests().stream()
                .filter((r) -> r.messageType() == ControlMessageType.ADD_JOB_SUBSCRIPTION);
    }

    protected Stream<ControlMessageRequest> getUnsubscribeRequests()
    {
        return broker.getReceivedControlMessageRequests().stream()
                .filter((r) -> r.messageType() == ControlMessageType.REMOVE_JOB_SUBSCRIPTION);
    }

    private Stream<ControlMessageRequest> getCreditRequests()
    {
        return broker.getReceivedControlMessageRequests().stream()
            .filter((r) -> r.messageType() == ControlMessageType.INCREASE_JOB_SUBSCRIPTION_CREDITS);
    }

    protected class WaitingJobHandler implements JobHandler
    {
        protected AtomicInteger numHandledEvents = new AtomicInteger(0);
        protected AtomicInteger numWaitingThreads = new AtomicInteger(0);
        protected boolean shouldWait = true;

        @Override
        public void handle(JobClient client, JobEvent workItemEvent)
        {
            try
            {
                if (shouldWait)
                {
                    synchronized (monitor)
                    {
                        numWaitingThreads.incrementAndGet();
                        monitor.wait();
                        numWaitingThreads.decrementAndGet();
                    }
                }

                numHandledEvents.incrementAndGet();
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }
        }

    }
}
