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
package io.zeebe.client.task.subscription;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.zeebe.client.TasksClient;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientConfiguration;
import io.zeebe.client.event.TaskEvent;
import io.zeebe.client.impl.TasksClientImpl;
import io.zeebe.client.impl.data.MsgPackConverter;
import io.zeebe.client.task.PollableTaskSubscription;
import io.zeebe.client.task.TaskHandler;
import io.zeebe.client.task.TaskSubscription;
import io.zeebe.client.task.impl.subscription.Subscriber;
import io.zeebe.client.util.ClientRule;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.EventType;
import io.zeebe.protocol.clientapi.SubscriptionType;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.test.broker.protocol.brokerapi.ControlMessageRequest;
import io.zeebe.test.broker.protocol.brokerapi.ExecuteCommandRequest;
import io.zeebe.test.broker.protocol.brokerapi.StubBrokerRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.RemoteAddress;

public class TaskSubscriptionTest
{
    private static final int NUM_EXECUTION_THREADS = 2;

    private static final TaskHandler DO_NOTHING = (c, t) ->
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
        continueTaskHandlingThreads();
    }

    @Test
    public void shouldOpenSubscription()
    {
        // given
        broker.stubTaskSubscriptionApi(123L);

        // when
        final TaskSubscription subscription = clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
            .handler(DO_NOTHING)
            .lockOwner("foo")
            .lockTime(10000L)
            .taskType("bar")
            .taskFetchSize(456)
            .open();

        // then
        assertThat(subscription.isOpen()).isTrue();
        assertThat(subscription.isClosed()).isFalse();

        final ControlMessageRequest subscriptionRequest = getSubscribeRequests().findFirst().get();
        assertThat(subscriptionRequest.messageType()).isEqualByComparingTo(ControlMessageType.ADD_TASK_SUBSCRIPTION);
        assertThat(subscriptionRequest.partitionId()).isEqualTo(clientRule.getDefaultPartitionId());

        assertThat(subscriptionRequest.getData()).contains(
                entry("lockOwner", "foo"),
                entry("lockDuration", 10000),
                entry("taskType", "bar"),
                entry("credits", 456));
    }

    @Test
    public void shouldCloseSubscription()
    {
        // given
        broker.stubTaskSubscriptionApi(123L);

        final TaskSubscription subscription = clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
            .handler(DO_NOTHING)
            .lockOwner("foo")
            .lockTime(10000L)
            .taskType("bar")
            .open();

        // when
        subscription.close();

        // then
        assertThat(subscription.isClosed()).isTrue();
        assertThat(subscription.isOpen()).isFalse();

        final ControlMessageRequest subscriptionRequest = getUnsubscribeRequests().findFirst().get();
        assertThat(subscriptionRequest.messageType()).isEqualByComparingTo(ControlMessageType.REMOVE_TASK_SUBSCRIPTION);

        assertThat(subscriptionRequest.partitionId()).isEqualTo(clientRule.getDefaultPartitionId());
        assertThat(subscriptionRequest.getData()).contains(entry("subscriberKey", 123));
    }

    @Test
    public void shouldOpenPollableSubscription()
    {
        // given
        broker.stubTaskSubscriptionApi(123L);

        // when
        final PollableTaskSubscription subscription = clientRule.tasks().newPollableTaskSubscription(clientRule.getDefaultTopicName())
            .lockOwner("foo")
            .lockTime(10000L)
            .taskType("bar")
            .taskFetchSize(456)
            .open();

        // then
        assertThat(subscription.isOpen()).isTrue();

        final ControlMessageRequest subscriptionRequest = getSubscribeRequests().findFirst().get();
        assertThat(subscriptionRequest.messageType()).isEqualByComparingTo(ControlMessageType.ADD_TASK_SUBSCRIPTION);

        assertThat(subscriptionRequest.getData()).contains(
                entry("lockOwner", "foo"),
                entry("lockDuration", 10000),
                entry("taskType", "bar"),
                entry("credits", 456));
    }

    @Test
    public void shouldValidateMissingTaskType()
    {
        // given
        broker.stubTaskSubscriptionApi(123L);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("taskType must not be null");

        // when
        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
            .handler(DO_NOTHING)
            .lockOwner("foo")
            .lockTime(10000L)
            .open();
    }

    @Test
    public void shouldValidateMissingTaskHandler()
    {
        // given
        broker.stubTaskSubscriptionApi(123L);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("taskHandler must not be null");

        // when
        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
            .lockOwner("foo")
            .lockTime(10000L)
            .taskType("bar")
            .open();
    }

    @Test
    public void shouldUseDefaultTaskFetchSize()
    {
        // given
        broker.stubTaskSubscriptionApi(123L);

        // when
        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
            .handler(DO_NOTHING)
            .lockOwner("foo")
            .lockTime(10000L)
            .taskType("bar")
            .open();

        // then
        final ControlMessageRequest subscriptionRequest = getSubscribeRequests().findFirst().get();
        assertThat(subscriptionRequest.messageType()).isEqualByComparingTo(ControlMessageType.ADD_TASK_SUBSCRIPTION);

        assertThat(subscriptionRequest.getData()).containsEntry("credits", 32);
    }

    @Test
    public void shouldValidateMissingLockTime()
    {
        // given
        broker.stubTaskSubscriptionApi(123L);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("lockTime must be greater than 0");

        // when
        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
            .handler(DO_NOTHING)
            .lockOwner("foo")
            .taskType("bar")
            .open();
    }

    @Test
    public void shouldValidateLockTimePositive()
    {
        // given
        broker.stubTaskSubscriptionApi(123L);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("lockTime must be greater than 0");

        // when
        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
            .handler(DO_NOTHING)
            .lockOwner("foo")
            .lockTime(0L)
            .taskType("bar")
            .open();
    }

    @Test
    public void shouldOpenSubscriptionWithLockTimeAsDuration()
    {
        // given
        broker.stubTaskSubscriptionApi(123L);

        // when
        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
            .handler(DO_NOTHING)
            .lockOwner("foo")
            .lockTime(Duration.ofDays(10))
            .taskType("bar")
            .open();

        // then
        final ControlMessageRequest subscriptionRequest = getSubscribeRequests().findFirst().get();

        assertThat(subscriptionRequest.getData()).contains(
                entry("lockDuration", (int) TimeUnit.DAYS.toMillis(10L)));
    }

    @Test
    public void shouldOpenPollableSubscriptionWithLockTimeAsDuration()
    {
        // given
        broker.stubTaskSubscriptionApi(123L);

        // when
        clientRule.tasks().newPollableTaskSubscription(clientRule.getDefaultTopicName())
            .lockOwner("foo")
            .lockTime(Duration.ofDays(10))
            .taskType("bar")
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
        broker.onControlMessageRequest(r -> r.messageType() == ControlMessageType.ADD_TASK_SUBSCRIPTION)
            .respondWithError()
            .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
            .errorData("does not compute")
            .register();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Could not open subscription");

        // when
        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
            .handler(DO_NOTHING)
            .lockOwner("foo")
            .lockTime(10000L)
            .taskType("bar")
            .open();
    }

    @Test
    public void shouldInvokeTaskHandler() throws JsonParseException, JsonMappingException, IOException
    {
        // given
        broker.stubTaskSubscriptionApi(123L);
        stubTaskCompleteRequest();

        final RecordingTaskHandler handler = new RecordingTaskHandler();
        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
                .handler(handler)
                .lockOwner("owner")
                .lockTime(10000L)
                .taskType("type")
                .open();

        final RemoteAddress clientAddress = getSubscribeRequests().findFirst().get().getSource();

        final MsgPackHelper msgPackHelper = new MsgPackHelper();
        final Map<String, Object> taskPayload = new HashMap<>();
        taskPayload.put("payloadKey", "payloadValue");

        final Map<String, Object> taskHeaders = new HashMap<>();
        taskPayload.put("headerKey", "headerValue");
        final long lockTime = System.currentTimeMillis();

        // when
        broker.newSubscribedEvent()
            .partitionId(StubBrokerRule.TEST_PARTITION_ID)
            .key(4L)
            .position(5L)
            .eventType(EventType.TASK_EVENT)
            .subscriberKey(123L)
            .subscriptionType(SubscriptionType.TASK_SUBSCRIPTION)
            .value()
                .put("type", "type")
                .put("lockTime", lockTime)
                .put("retries", 3)
                .put("payload", msgPackHelper.encodeAsMsgPack(taskPayload))
                .put("headers", taskHeaders)
                .done()
            .push(clientAddress);

        // then
        TestUtil.waitUntil(() -> !handler.getHandledTasks().isEmpty());

        assertThat(handler.getHandledTasks()).hasSize(1);

        final TaskEvent task = handler.getHandledTasks().get(0);

        assertThat(task.getMetadata().getKey()).isEqualTo(4L);
        assertThat(task.getType()).isEqualTo("type");
        assertThat(task.getHeaders()).isEqualTo(taskHeaders);
        assertThat(task.getLockExpirationTime()).isEqualTo(Instant.ofEpochMilli(lockTime));

        final ObjectMapper objectMapper = new ObjectMapper();
        @SuppressWarnings("unchecked")
        final Map<String, Object> receivedPayload = objectMapper.readValue(task.getPayload(), Map.class);
        assertThat(receivedPayload).isEqualTo(taskPayload);
    }

    @Test
    public void shouldInvokeTaskHandlerWithTwoSubscriptions()
    {
        // given
        broker.stubTaskSubscriptionApi(123L);
        stubTaskCompleteRequest();

        final RecordingTaskHandler handler1 = new RecordingTaskHandler();
        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
                .handler(handler1)
                .lockOwner("foo")
                .lockTime(10000L)
                .taskType("type1")
                .open();

        final RecordingTaskHandler handler2 = new RecordingTaskHandler();
        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
            .handler(handler2)
            .lockOwner("bar")
            .lockTime(10000L)
            .taskType("type2")
            .open();

        final RemoteAddress clientAddress = getSubscribeRequests().findFirst().get().getSource();

        // when
        broker.pushLockedTask(clientAddress, 123L, 4L, 5L, "foo", "type1");
        broker.pushLockedTask(clientAddress, 124L, 5L, 6L, "bar", "type2");

        // then
        TestUtil.waitUntil(() -> !handler1.getHandledTasks().isEmpty());
        TestUtil.waitUntil(() -> !handler2.getHandledTasks().isEmpty());

        assertThat(handler1.getHandledTasks()).hasSize(1);
        assertThat(handler2.getHandledTasks()).hasSize(1);

        final TaskEvent task1 = handler1.getHandledTasks().get(0);

        assertThat(task1.getMetadata().getKey()).isEqualTo(4L);
        assertThat(task1.getType()).isEqualTo("type1");

        final TaskEvent task2 = handler2.getHandledTasks().get(0);

        assertThat(task2.getMetadata().getKey()).isEqualTo(5L);
        assertThat(task2.getType()).isEqualTo("type2");
    }

    @Test
    public void shouldInvokeTaskHandlerForPollableSubscription()
    {
        // given
        broker.stubTaskSubscriptionApi(123L);
        stubTaskCompleteRequest();

        final RecordingTaskHandler handler = new RecordingTaskHandler();
        final PollableTaskSubscription subscription = clientRule.tasks().newPollableTaskSubscription(clientRule.getDefaultTopicName())
                .lockOwner("foo")
                .lockTime(10000L)
                .taskType("bar")
                .open();

        final RemoteAddress clientAddress = getSubscribeRequests().findFirst().get().getSource();

        broker.pushLockedTask(clientAddress, 123L, 4L, 5L, "foo", "type");

        // when
        final Integer handledTasks = TestUtil.doRepeatedly(() -> subscription.poll(handler))
                .until(numTasks -> numTasks > 0);

        // then
        assertThat(handledTasks).isEqualTo(1);
        assertThat(handler.getHandledTasks()).hasSize(1);

        final TaskEvent task = handler.getHandledTasks().get(0);

        assertThat(task.getMetadata().getKey()).isEqualTo(4L);
        assertThat(task.getType()).isEqualTo("type");
    }

    @Test
    public void shouldNotAutocompleteTask() throws InterruptedException
    {
        // given
        broker.stubTaskSubscriptionApi(123L);
        stubTaskCompleteRequest();

        final RecordingTaskHandler handler = new RecordingTaskHandler();
        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
                .handler(handler)
                .lockOwner("foo")
                .lockTime(10000L)
                .taskType("bar")
                .open();

        final RemoteAddress clientAddress = getSubscribeRequests().findFirst().get().getSource();

        // when
        broker.pushLockedTask(clientAddress, 123L, 4L, 5L, "foo", "bar");

        // then
        Thread.sleep(1000L);

        assertThat(broker.getReceivedCommandRequests().stream()
                .filter(r -> r.eventType() == EventType.TASK_EVENT)
                .count()).isEqualTo(0);
    }

    @Test
    public void shouldCompleteTaskWithPayload()
    {
        // given
        broker.stubTaskSubscriptionApi(123L);
        stubTaskCompleteRequest();

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
                .handler((c, t) -> c.complete(t).payload("{\"a\": 1}").execute())
                .lockOwner("foo")
                .lockTime(10000L)
                .taskType("bar")
                .open();

        final RemoteAddress eventSource = getSubscribeRequests().findFirst().get().getSource();

        // when
        broker.pushLockedTask(eventSource, 123L, 4L, 5L, "foo", "bar");

        // then
        final ExecuteCommandRequest taskRequest = TestUtil.doRepeatedly(() -> broker.getReceivedCommandRequests().stream()
                .filter(r -> r.eventType() == EventType.TASK_EVENT)
                .findFirst())
            .until(r -> r.isPresent())
            .get();

        assertThat(taskRequest.partitionId()).isEqualTo(clientRule.getDefaultPartitionId());
        assertThat(taskRequest.key()).isEqualTo(4L);
        assertThat(taskRequest.getCommand())
            .containsEntry("state", "COMPLETE")
            .containsEntry("type", "bar")
            .containsEntry("lockOwner", "foo")
            .containsEntry("payload", msgPackConverter.convertToMsgPack("{\"a\": 1}"));
    }

    @Test
    public void shouldSetPayloadAndCompleteTask()
    {
        // given
        broker.stubTaskSubscriptionApi(123L);
        stubTaskCompleteRequest();

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
                .handler((c, t) -> c.complete(t).payload("{\"a\": 1}").execute())
                .lockOwner("foo")
                .lockTime(10000L)
                .taskType("bar")
                .open();

        final RemoteAddress eventSource = getSubscribeRequests().findFirst().get().getSource();

        // when
        broker.pushLockedTask(eventSource, 123L, 4L, 5L, "foo", "bar");

        // then
        final ExecuteCommandRequest taskRequest = TestUtil.doRepeatedly(() -> broker.getReceivedCommandRequests().stream()
                .filter(r -> r.eventType() == EventType.TASK_EVENT)
                .findFirst())
            .until(r -> r.isPresent())
            .get();

        assertThat(taskRequest.partitionId()).isEqualTo(clientRule.getDefaultPartitionId());
        assertThat(taskRequest.key()).isEqualTo(4L);
        assertThat(taskRequest.getCommand())
            .containsEntry("state", "COMPLETE")
            .containsEntry("type", "bar")
            .containsEntry("lockOwner", "foo")
            .containsEntry("payload", msgPackConverter.convertToMsgPack("{\"a\": 1}"));
    }

    @Test
    public void shouldCompleteTaskWithoutPayload()
    {
        // given
        broker.stubTaskSubscriptionApi(123L);
        stubTaskCompleteRequest();

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
                .handler((c, t) -> c.complete(t).withoutPayload().execute())
                .lockOwner("foo")
                .lockTime(10000L)
                .taskType("bar")
                .open();

        final RemoteAddress eventSource = getSubscribeRequests().findFirst().get().getSource();

        // when
        broker.pushLockedTask(eventSource, 123L, 4L, 5L, "foo", "bar");

        // then
        final ExecuteCommandRequest taskRequest = TestUtil.doRepeatedly(() -> broker.getReceivedCommandRequests().stream()
                .filter(r -> r.eventType() == EventType.TASK_EVENT)
                .findFirst())
            .until(r -> r.isPresent())
            .get();

        assertThat(taskRequest.partitionId()).isEqualTo(clientRule.getDefaultPartitionId());
        assertThat(taskRequest.key()).isEqualTo(4L);
        assertThat(taskRequest.getCommand())
            .containsEntry("state", "COMPLETE")
            .containsEntry("type", "bar")
            .containsEntry("lockOwner", "foo")
            .doesNotContainKey("payload");
    }

    @Test
    public void shouldMarkTaskAsFailedOnExpcetion()
    {
        // given
        broker.stubTaskSubscriptionApi(123L);
        broker.onExecuteCommandRequest(isTaskFailCommand())
            .respondWith()
            .value()
                .allOf(r -> r.getCommand())
                .put("state", "FAILED")
                .done()
            .register();

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
                .handler((c, t) ->
                {
                    throw new RuntimeException("expected failure");
                })
                .lockOwner("foo")
                .lockTime(10000L)
                .taskType("bar")
                .open();

        final RemoteAddress clientAddress = getSubscribeRequests().findFirst().get().getSource();

        // when
        broker.pushLockedTask(clientAddress, 123L, 4L, 5L, "foo", "bar");

        // then
        final ExecuteCommandRequest taskRequest = TestUtil.doRepeatedly(() -> broker.getReceivedCommandRequests().stream()
                .filter(r -> r.eventType() == EventType.TASK_EVENT)
                .findFirst())
            .until(r -> r.isPresent())
            .get();

        assertThat(taskRequest.partitionId()).isEqualTo(clientRule.getDefaultPartitionId());
        assertThat(taskRequest.key()).isEqualTo(4L);
        assertThat(taskRequest.getCommand())
            .containsEntry("state", "FAIL")
            .containsEntry("type", "bar")
            .containsEntry("lockOwner", "foo");
    }

    @Test
    public void shouldCloseSubscriptionOnChannelClose() throws InterruptedException
    {
        // given
        broker.stubTaskSubscriptionApi(123L);

        final TaskSubscription subscription = clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
            .handler((c, t) -> c.complete(t).withoutPayload().execute())
            .lockOwner("owner")
            .lockTime(1000L)
            .taskFetchSize(5)
            .taskType("foo")
            .open();

        // when
        broker.closeTransport();
        Thread.sleep(500L); // let subscriber attempt reopening
        clientRule.getClock().addTime(Duration.ofSeconds(60)); // make request time out immediately

        // then
        TestUtil.waitUntil(() -> subscription.isClosed());
        assertThat(subscription.isClosed()).isTrue();
    }


    protected void continueTaskHandlingThreads()
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
    public void shouldRetryWithMoreTasksThanSubscriptionCapacity() throws InterruptedException
    {
        // given
        broker.stubTaskSubscriptionApi(123L);
        broker.onExecuteCommandRequest(EventType.TASK_EVENT, "COMPLETE")
            .respondWith()
            .key((r) -> r.key())
            .value()
                .allOf((r) -> r.getCommand())
                .put("state", "COMPLETED")
                .done()
            .register();

        final WaitingTaskHandler handler = new WaitingTaskHandler();
        final ZeebeClientConfiguration clientConfig = client.getConfiguration();
        final int numExecutionThreads = clientConfig.getNumSubscriptionExecutionThreads();
        final int taskCapacity = 4;

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
            .handler(handler)
            .lockOwner("owner")
            .lockTime(1000L)
            .taskFetchSize(taskCapacity)
            .taskType("foo")
            .open();

        final RemoteAddress clientAddress = broker.getReceivedControlMessageRequests().get(0).getSource();

        for (int i = 0; i < taskCapacity + numExecutionThreads; i++)
        {
            broker.pushLockedTask(clientAddress, 123L, i, i, "owner", "foo");
        }

        TestUtil.waitUntil(() -> handler.numWaitingThreads.get() > 0);

        // pushing one more event, exceeding client capacity
        broker.pushLockedTask(clientAddress, 123L, Integer.MAX_VALUE, Integer.MAX_VALUE, "owner", "foo");

        // waiting for the client to receive all pending tasks
        Thread.sleep(500L);

        // when
        handler.shouldWait = false;
        continueTaskHandlingThreads();

        // then the additional event is handled nevertheless (i.e. client applies backpressure)
        TestUtil.waitUntil(() -> handler.numHandledEvents.get() == taskCapacity + numExecutionThreads + 1);
    }

    /**
     * i.e. if signalling task failure itself fails
     */
    @Test
    public void shouldNotLoseCreditsOnFailureToReportTaskFailure() throws InterruptedException
    {
        // given
        broker.stubTaskSubscriptionApi(123L);
        failTaskFailure();

        final int subscriptionCapacity = 8;
        final AtomicInteger failedTasks = new AtomicInteger(0);

        final TaskHandler taskHandler = (c, t) ->
        {
            failedTasks.incrementAndGet();
            throw new RuntimeException("foo");
        };

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
            .handler(taskHandler)
            .lockOwner("owner")
            .lockTime(1000L)
            .taskFetchSize(subscriptionCapacity)
            .taskType("foo")
            .open();

        final RemoteAddress clientAddress = broker.getReceivedControlMessageRequests().get(0).getSource();

        for (int i = 0; i < subscriptionCapacity; i++)
        {
            broker.pushLockedTask(clientAddress, 123L, i, i, "owner", "foo");
        }


        // when
        TestUtil.waitUntil(() -> failedTasks.get() == 8);
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
        broker.stubTaskSubscriptionApi(123L);

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
            .handler(DO_NOTHING)
            .lockOwner("foo")
            .lockTime(10000L)
            .taskType("bar")
            .open();

        // when
        broker.interruptAllServerChannels();

        // then
        TestUtil.waitUntil(() -> getSubscribeRequests().count() == 2);

        final ControlMessageRequest reopenRequest = getSubscribeRequests().skip(1).findFirst().get();
        assertThat(reopenRequest.getData()).contains(
            entry("lockOwner", "foo"),
            entry("lockDuration", 10000),
            entry("taskType", "bar"));
    }

    @Test
    public void shouldSendCorrectCreditsRequest()
    {
        // given
        broker.stubTaskSubscriptionApi(123L);
        final TasksClientImpl taskClient = (TasksClientImpl) clientRule.tasks();

        // when
        taskClient.increaseSubscriptionCredits(StubBrokerRule.TEST_PARTITION_ID)
            .credits(123)
            .subscriberKey(456L)
            .execute();

        // then
        final List<ControlMessageRequest> controlMessageRequests = broker.getReceivedControlMessageRequests()
                .stream()
                .filter(r -> r.messageType() == ControlMessageType.INCREASE_TASK_SUBSCRIPTION_CREDITS)
                .collect(Collectors.toList());

        assertThat(controlMessageRequests).hasSize(1);

        final ControlMessageRequest request = controlMessageRequests.get(0);
        assertThat(request.messageType()).isEqualTo(ControlMessageType.INCREASE_TASK_SUBSCRIPTION_CREDITS);
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
        final int tasksToHandleBeforeReplenishment = subscriptionCapacity - replenishmentThreshold;

        broker.stubTaskSubscriptionApi(123L);

        final WaitingTaskHandler handler = new WaitingTaskHandler();
        handler.shouldWait = false;

        clientRule.tasks().newTaskSubscription(clientRule.getDefaultTopicName())
                .handler(handler)
                .lockOwner("owner")
                .lockTime(10000L)
                .taskFetchSize(subscriptionCapacity)
                .taskType("type")
                .open();

        final RemoteAddress clientAddress = getSubscribeRequests().findFirst().get().getSource();

        // handling these tasks should not yet trigger replenishment; the next handled task would
        for (int i = 0; i < tasksToHandleBeforeReplenishment; i++)
        {
            broker.pushLockedTask(clientAddress, 123L, 4L + i, 5L + i, "foo", "type");
        }
        waitUntil(() -> handler.numHandledEvents.get() == tasksToHandleBeforeReplenishment);

        handler.shouldWait = true;
        for (int i = 0; i < NUM_EXECUTION_THREADS; i++)
        {
            broker.pushLockedTask(clientAddress, 123L, 4L + i, 5L + i, "foo", "type");
        }
        waitUntil(() -> handler.numWaitingThreads.get() == NUM_EXECUTION_THREADS);

        // when all task handling threads trigger credit replenishment
        continueTaskHandlingThreads();

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

        assertThat(totalReplenishedCredits).isGreaterThanOrEqualTo(tasksToHandleBeforeReplenishment + 1);
    }

    protected void failTaskFailure()
    {
        broker.onExecuteCommandRequest(EventType.TASK_EVENT, "FAIL")
            .respondWithError()
            .errorCode(ErrorCode.REQUEST_PROCESSING_FAILURE)
            .errorData("failed to fail task")
            .register();
    }

    protected void stubTaskCompleteRequest()
    {
        broker.onExecuteCommandRequest(isTaskCompleteCommand())
            .respondWith()
            .value()
                .allOf(r -> r.getCommand())
                .put("state", "COMPLETED")
                .done()
            .register();
    }

    protected Stream<ControlMessageRequest> getSubscribeRequests()
    {
        return broker.getReceivedControlMessageRequests().stream()
                .filter((r) -> r.messageType() == ControlMessageType.ADD_TASK_SUBSCRIPTION);
    }

    protected Stream<ControlMessageRequest> getUnsubscribeRequests()
    {
        return broker.getReceivedControlMessageRequests().stream()
                .filter((r) -> r.messageType() == ControlMessageType.REMOVE_TASK_SUBSCRIPTION);
    }

    private Stream<ControlMessageRequest> getCreditRequests()
    {
        return broker.getReceivedControlMessageRequests().stream()
            .filter((r) -> r.messageType() == ControlMessageType.INCREASE_TASK_SUBSCRIPTION_CREDITS);
    }

    protected Predicate<ExecuteCommandRequest> isTaskCompleteCommand()
    {
        return r -> r.eventType() == EventType.TASK_EVENT && "COMPLETE".equals(r.getCommand().get("state"));
    }

    protected Predicate<ExecuteCommandRequest> isTaskFailCommand()
    {
        return r -> r.eventType() == EventType.TASK_EVENT && "FAIL".equals(r.getCommand().get("state"));
    }

    protected class WaitingTaskHandler implements TaskHandler
    {
        protected AtomicInteger numHandledEvents = new AtomicInteger(0);
        protected AtomicInteger numWaitingThreads = new AtomicInteger(0);
        protected boolean shouldWait = true;

        @Override
        public void handle(TasksClient client, TaskEvent task)
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
