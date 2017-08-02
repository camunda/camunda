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
package io.zeebe.broker.task.processor;

import static io.zeebe.protocol.clientapi.EventType.TASK_EVENT;
import static io.zeebe.util.StringUtil.getBytes;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.*;

import io.zeebe.broker.task.CreditsRequest;
import io.zeebe.broker.task.data.TaskEvent;
import io.zeebe.broker.task.data.TaskState;
import io.zeebe.broker.test.MockStreamProcessorController;
import io.zeebe.broker.test.WrittenEvent;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.processor.EventFilter;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.BrokerEventMetadata;
import io.zeebe.util.time.ClockUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class LockTaskStreamProcessorTest
{
    private static final byte[] TASK_TYPE = getBytes("test-task");
    private static final DirectBuffer TASK_TYPE_BUFFER = new UnsafeBuffer(TASK_TYPE);

    private static final byte[] ANOTHER_TASK_TYPE = getBytes("another-task");
    private static final DirectBuffer ANOTHER_TASK_TYPE_BUFFER = new UnsafeBuffer(ANOTHER_TASK_TYPE);

    private static final int TERM = 3;

    private TaskSubscription subscription;
    private TaskSubscription anotherSubscription;

    private LockTaskStreamProcessor streamProcessor;

    @Mock
    private LoggedEvent mockLoggedEvent;

    @Mock
    private LogStream mockLogStream;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule
    public MockStreamProcessorController<TaskEvent> mockController = new MockStreamProcessorController<>(TaskEvent.class, event -> event.setRetries(3), TASK_EVENT, 1L);

    @Before
    public void setup() throws InterruptedException, ExecutionException
    {
        MockitoAnnotations.initMocks(this);

        when(mockLogStream.getTerm()).thenReturn(TERM);

        // fix the current time to calculate lock time
        ClockUtil.setCurrentTime(Instant.now());

        streamProcessor = new LockTaskStreamProcessor(TASK_TYPE_BUFFER);

        subscription = new TaskSubscription(wrapString("topic"), 0, TASK_TYPE_BUFFER, Duration.ofMinutes(5).toMillis(), wrapString("owner-1"), 11);
        subscription.setSubscriberKey(1L);
        subscription.setCredits(3);

        anotherSubscription = new TaskSubscription(wrapString("topic"), 0, TASK_TYPE_BUFFER, Duration.ofMinutes(10).toMillis(), wrapString("owner-2"), 12);
        anotherSubscription.setSubscriberKey(2L);
        anotherSubscription.setCredits(2);

        final StreamProcessorContext context = new StreamProcessorContext();
        context.setSourceStream(mockLogStream);
        context.setTargetStream(mockLogStream);

        mockController.initStreamProcessor(streamProcessor, context);
    }

    @After
    public void cleanUp()
    {
        ClockUtil.reset();
    }

    @Test
    public void shouldLockCreatedTask()
    {
        // given
        streamProcessor.addSubscription(subscription);

        // when
        mockController.processEvent(2L, event -> event
                .setState(TaskState.CREATED)
                .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

        // then
        final WrittenEvent<TaskEvent> lastWrittenEvent = mockController.getLastWrittenEvent();

        final TaskEvent taskEvent = lastWrittenEvent.getValue();
        assertThat(taskEvent.getState()).isEqualTo(TaskState.LOCK);
        assertThat(taskEvent.getLockTime()).isEqualTo(lockTimeOf(subscription));
        assertThat(taskEvent.getLockOwner()).isEqualTo(wrapString("owner-1"));

        final BrokerEventMetadata metadata = lastWrittenEvent.getMetadata();
        assertThat(metadata.getSubscriberKey()).isEqualTo(subscription.getSubscriberKey());
        assertThat(metadata.getRequestStreamId()).isEqualTo(subscription.getStreamId());
        assertThat(metadata.getProtocolVersion()).isEqualTo(Protocol.PROTOCOL_VERSION);
        assertThat(metadata.getEventType()).isEqualTo(TASK_EVENT);
        assertThat(metadata.getRaftTermId()).isEqualTo(TERM);
    }

    @Test
    public void shouldLockExpiredTask()
    {
        // given
        streamProcessor.addSubscription(subscription);

        // when
        mockController.processEvent(2L, event -> event
                .setState(TaskState.LOCK_EXPIRED)
                .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

        // then
        final WrittenEvent<TaskEvent> lastWrittenEvent = mockController.getLastWrittenEvent();

        final TaskEvent taskEvent = lastWrittenEvent.getValue();
        assertThat(taskEvent.getState()).isEqualTo(TaskState.LOCK);
        assertThat(taskEvent.getLockTime()).isEqualTo(lockTimeOf(subscription));

        final BrokerEventMetadata metadata = lastWrittenEvent.getMetadata();
        assertThat(metadata.getSubscriberKey()).isEqualTo(subscription.getSubscriberKey());
        assertThat(metadata.getRequestStreamId()).isEqualTo(subscription.getStreamId());
        assertThat(metadata.getEventType()).isEqualTo(TASK_EVENT);
        assertThat(metadata.getRaftTermId()).isEqualTo(TERM);
    }

    @Test
    public void shouldLockFailedTask()
    {
        // given
        streamProcessor.addSubscription(subscription);

        // when
        mockController.processEvent(2L, event -> event
                .setState(TaskState.FAILED)
                .setRetries(2)
                .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

        // then
        final WrittenEvent<TaskEvent> lastWrittenEvent = mockController.getLastWrittenEvent();

        final TaskEvent taskEvent = lastWrittenEvent.getValue();
        assertThat(taskEvent.getState()).isEqualTo(TaskState.LOCK);
        assertThat(taskEvent.getLockTime()).isEqualTo(lockTimeOf(subscription));

        final BrokerEventMetadata metadata = lastWrittenEvent.getMetadata();
        assertThat(metadata.getSubscriberKey()).isEqualTo(subscription.getSubscriberKey());
        assertThat(metadata.getRequestStreamId()).isEqualTo(subscription.getStreamId());
        assertThat(metadata.getEventType()).isEqualTo(TASK_EVENT);
        assertThat(metadata.getRaftTermId()).isEqualTo(TERM);
    }

    @Test
    public void shouldLockTaskWithUpdatedRetries()
    {
        // given
        streamProcessor.addSubscription(subscription);

        // when
        mockController.processEvent(2L, event -> event
                .setState(TaskState.RETRIES_UPDATED)
                .setRetries(3)
                .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

        // then
        final WrittenEvent<TaskEvent> lastWrittenEvent = mockController.getLastWrittenEvent();

        final TaskEvent taskEvent = lastWrittenEvent.getValue();
        assertThat(taskEvent.getState()).isEqualTo(TaskState.LOCK);
        assertThat(taskEvent.getLockTime()).isEqualTo(lockTimeOf(subscription));
        assertThat(taskEvent.getRetries()).isEqualTo(3);

        final BrokerEventMetadata metadata = lastWrittenEvent.getMetadata();
        assertThat(metadata.getSubscriberKey()).isEqualTo(subscription.getSubscriberKey());
        assertThat(metadata.getRequestStreamId()).isEqualTo(subscription.getStreamId());
        assertThat(metadata.getEventType()).isEqualTo(TASK_EVENT);
        assertThat(metadata.getRaftTermId()).isEqualTo(TERM);
    }

    @Test
    public void shouldIgnoreFailedTaskWithNoRetries()
    {
        // given
        streamProcessor.addSubscription(subscription);

        // when
        mockController.processEvent(2L, event -> event
                .setState(TaskState.FAILED)
                .setRetries(0)
                .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

        // then
        assertThat(mockController.getWrittenEvents()).hasSize(0);
    }

    @Test
    public void shouldIgnoreTaskWithOtherType()
    {
        // given
        streamProcessor.addSubscription(subscription);

        // when
        mockController.processEvent(2L, event -> event
                .setState(TaskState.CREATED)
                .setType(ANOTHER_TASK_TYPE_BUFFER, 0, ANOTHER_TASK_TYPE_BUFFER.capacity()));

        // then
        assertThat(mockController.getWrittenEvents()).hasSize(0);
    }

    @Test
    public void shouldLockTasksFairToAllSubscriptions()
    {
        final AtomicInteger lockedTasksSubscription1 = new AtomicInteger(0);
        final AtomicInteger lockedTasksSubscritpion2 = new AtomicInteger(0);

        // given
        streamProcessor.addSubscription(subscription);
        streamProcessor.addSubscription(anotherSubscription);

        // when process 4 task events
        Stream.of(1, 2, 3, 4).forEach(key ->
        {
            mockController.processEvent(key, event -> event
                    .setState(TaskState.CREATED)
                    .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

            final WrittenEvent<TaskEvent> lastWrittenEvent = mockController.getLastWrittenEvent();
            assertThat(lastWrittenEvent.getValue().getState()).isEqualTo(TaskState.LOCK);

            final long subscriptionId = lastWrittenEvent.getMetadata().getSubscriberKey();
            if (subscriptionId == subscription.getSubscriberKey())
            {
                lockedTasksSubscription1.incrementAndGet();
            }
            else
            {
                lockedTasksSubscritpion2.incrementAndGet();
            }
        });

        // then each subscription lock two task events (round-robin-like)
        assertThat(lockedTasksSubscription1.get()).isEqualTo(2);
        assertThat(lockedTasksSubscritpion2.get()).isEqualTo(2);
    }

    @Test
    public void shouldLockTasksUntilSubscriptionHasNoMoreCredits()
    {
        // given
        streamProcessor.addSubscription(subscription);

        // when process as much events as available credits then they should be locked
        Stream.of(1, 2, 3).forEach(key ->
        {
            mockController.processEvent(key, event -> event
                    .setState(TaskState.CREATED)
                    .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

            assertThat(mockController.getLastWrittenEventValue().getState()).isEqualTo(TaskState.LOCK);
        });

        // when process one more event then it should not be locked
        mockController.processEvent(4L, event -> event
                .setState(TaskState.CREATED)
                .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

        assertThat(mockController.getWrittenEvents()).hasSize(3);
    }

    @Test
    public void shouldIncreaseSubscriptionCredits()
    {
        // given subscription with 3 credits
        streamProcessor.addSubscription(subscription);

        Stream.of(1, 2).forEach(key ->
        {
            mockController.processEvent(key, event -> event
                    .setState(TaskState.CREATED)
                    .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));
        });

        // when increase credits by 1
        streamProcessor.increaseSubscriptionCreditsAsync(new CreditsRequest(subscription.getSubscriberKey(), 1));

        Stream.of(3, 4, 5).forEach(key ->
        {
            mockController.processEvent(key, event -> event
                    .setState(TaskState.CREATED)
                    .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));
        });

        // then
        assertThat(mockController.getWrittenEvents()).hasSize(4);

        final WrittenEvent<TaskEvent> lastWrittenEvent = mockController.getLastWrittenEvent();
        assertThat(lastWrittenEvent.getKey()).isEqualTo(4L);
        assertThat(lastWrittenEvent.getValue().getState()).isEqualTo(TaskState.LOCK);
    }

    @Test
    public void shouldRemoveSubscription()
    {
        // given
        streamProcessor.addSubscription(subscription);
        streamProcessor.addSubscription(anotherSubscription);

        // when remove the first subscription
        CompletableFuture<Boolean> future = streamProcessor.removeSubscription(subscription.getSubscriberKey());

        // then an event is locked by the other subscription
        mockController.processEvent(2L, event -> event
                .setState(TaskState.CREATED)
                .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

        assertThat(mockController.getWrittenEvents()).hasSize(1);

        assertThat(future).isCompletedWithValue(true);
        assertThat(streamProcessor.isSuspended()).isFalse();

        // when remove the last subscription
        future = streamProcessor.removeSubscription(anotherSubscription.getSubscriberKey());

        mockController.processEvent(3L, event -> event
                .setState(TaskState.CREATED)
                .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

        assertThat(mockController.getWrittenEvents()).hasSize(1);

        // then the stream processor is suspended
        assertThat(future).isCompletedWithValue(false);
        assertThat(streamProcessor.isSuspended()).isTrue();
    }

    @Test
    public void shouldContinueProcessingIfIncreaseSubscriptionCredits()
    {
        // given
        streamProcessor.addSubscription(subscription);

        // process as much events as available credits
        Stream.of(1, 2, 3).forEach(key ->
        {
            mockController.processEvent(key, event -> event
                    .setState(TaskState.CREATED)
                    .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));
        });

        assertThat(streamProcessor.isSuspended()).isTrue();

        // when
        streamProcessor.increaseSubscriptionCreditsAsync(new CreditsRequest(subscription.getSubscriberKey(), 2));

        // then
        mockController.processEvent(4L, event -> event
                .setState(TaskState.CREATED)
                .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

        final WrittenEvent<TaskEvent> lastWrittenEvent = mockController.getLastWrittenEvent();
        assertThat(lastWrittenEvent.getKey()).isEqualTo(4L);
        assertThat(lastWrittenEvent.getValue().getState()).isEqualTo(TaskState.LOCK);

        assertThat(streamProcessor.isSuspended()).isFalse();
    }

    @Test
    public void shouldContinueProcessingIfAddSubscription()
    {
        // given
        assertThat(streamProcessor.isSuspended()).isTrue();

        // when
        streamProcessor.addSubscription(subscription);

        // then
        mockController.processEvent(2L, event -> event
                .setState(TaskState.CREATED)
                .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));

        assertThat(mockController.getLastWrittenEventValue().getState()).isEqualTo(TaskState.LOCK);

        assertThat(streamProcessor.isSuspended()).isFalse();
    }

    @Test
    public void shouldFailToAddSubscriptionIfNull()
    {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("subscription must not be null");

        streamProcessor.addSubscription(null);
    }

    @Test
    public void shouldFailToAddSubscriptionIfZeroCredits()
    {
        anotherSubscription.setCredits(0);

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("subscription credits must be greater than 0");

        streamProcessor.addSubscription(anotherSubscription);
    }

    @Test
    public void shouldFailToAddSubscriptionIfEmptyLockOwner()
    {
        final TaskSubscription subscription = new TaskSubscription(wrapString("topic"), 0, TASK_TYPE_BUFFER, Duration.ofMinutes(5).toMillis(), wrapString(""),
                11);

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("length of lock owner must be greater than 0");

        streamProcessor.addSubscription(subscription);
    }

    @Test
    public void shouldFailToAddSubscriptionIfLockOwnerTooLong()
    {
        final String lockOwner = IntStream.range(0, TaskSubscription.LOCK_OWNER_MAX_LENGTH + 1).mapToObj(i -> "a").collect(Collectors.joining());
        final TaskSubscription subscription = new TaskSubscription(wrapString("topic"), 0, TASK_TYPE_BUFFER, Duration.ofMinutes(5).toMillis(),
                wrapString(lockOwner), 11);

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("length of lock owner must be less than or equal to 64");

        streamProcessor.addSubscription(subscription);
    }

    @Test
    public void shouldFailToAddSubscriptionIfWrongType()
    {
        final TaskSubscription subscription = new TaskSubscription(wrapString("topic"), 0, ANOTHER_TASK_TYPE_BUFFER, Duration.ofMinutes(5).toMillis(),
                wrapString("owner-1"), 11);
        subscription.setCredits(5);

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Subscription task type is not equal to 'test-task'.");

        streamProcessor.addSubscription(subscription);
    }

    @Test
    public void shouldFailToAddSubscriptionIfZeroLockDuration()
    {
        final TaskSubscription subscription = new TaskSubscription(wrapString("topic"), 0, TASK_TYPE_BUFFER, 0, wrapString("owner-1"), 11);

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("lock duration must be greater than 0");

        streamProcessor.addSubscription(subscription);
    }

    @Test
    public void shouldIgnoreCreditsIfNotExist()
    {
        // given
        streamProcessor.increaseSubscriptionCreditsAsync(new CreditsRequest(123L, 5));

        try
        {
            // when
            mockController.processEvent(2L, event -> event
                    .setState(TaskState.CREATED)
                    .setType(TASK_TYPE_BUFFER, 0, TASK_TYPE_BUFFER.capacity()));
        }
        // then
        catch (Exception e)
        {
            fail("nothing bad should happen");
        }
    }

    @Test
    public void shouldAcceptEventForReprocessingWithSubscribedType()
    {
        final LoggedEvent loggedEvent = mockController.buildLoggedEvent(2L, event -> event
                .setState(TaskState.CREATED)
                .setType(TASK_TYPE_BUFFER));

        final EventFilter eventFilter = LockTaskStreamProcessor.reprocessingEventFilter(TASK_TYPE_BUFFER);

        assertThat(eventFilter.applies(loggedEvent)).isTrue();
    }

    @Test
    public void shouldRejectEventForReprocessingWithDifferentType()
    {
        final LoggedEvent loggedEvent = mockController.buildLoggedEvent(2L, event -> event
                .setState(TaskState.CREATED)
                .setType(ANOTHER_TASK_TYPE_BUFFER));

        final EventFilter eventFilter = LockTaskStreamProcessor.reprocessingEventFilter(TASK_TYPE_BUFFER);

        assertThat(eventFilter.applies(loggedEvent)).isFalse();
    }

    protected long lockTimeOf(TaskSubscription subscription)
    {
        return ClockUtil.getCurrentTime().plusMillis(subscription.getLockDuration()).toEpochMilli();
    }

}
