package org.camunda.tngp.broker.taskqueue.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.protocol.clientapi.EventType.TASK_EVENT;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.taskqueue.TaskSubscriptionManager;
import org.camunda.tngp.broker.taskqueue.data.TaskEvent;
import org.camunda.tngp.broker.taskqueue.data.TaskEventType;
import org.camunda.tngp.broker.test.MockStreamProcessorController;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.broker.transport.clientapi.SubscribedEventWriter;
import org.camunda.tngp.broker.util.msgpack.MsgPackUtil;
import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.protocol.clientapi.SubscriptionType;
import org.camunda.tngp.test.util.FluentMock;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskInstanceStreamProcessorTest
{
    public static final DirectBuffer TASK_TYPE = MsgPackUtil.utf8("foo");

    private TaskInstanceStreamProcessor streamProcessor;

    @Mock
    private IndexStore mockIndexStore;

    @Mock
    private LogStream mockLogStream;

    @FluentMock
    private CommandResponseWriter mockResponseWriter;

    @FluentMock
    private SubscribedEventWriter mockSubscribedEventWriter;

    @Mock
    private TaskSubscriptionManager mockTaskSubscriptionManager;

    @Rule
    public MockStreamProcessorController<TaskEvent> mockController = new MockStreamProcessorController<>(
        TaskEvent.class,
        (t) -> t.setType(TASK_TYPE).setEventType(TaskEventType.CREATED),
        TASK_EVENT,
        0);

    @Before
    public void setup() throws InterruptedException, ExecutionException
    {
        MockitoAnnotations.initMocks(this);

        when(mockLogStream.getId()).thenReturn(1);

        streamProcessor = new TaskInstanceStreamProcessor(mockResponseWriter, mockSubscribedEventWriter, mockIndexStore, mockTaskSubscriptionManager);

        final StreamProcessorContext context = new StreamProcessorContext();
        context.setSourceStream(mockLogStream);

        mockController.initStreamProcessor(streamProcessor, context);
    }

    @Test
    public void shouldCreateTask()
    {
        // when
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.CREATED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter).longKey(2L);
        verify(mockResponseWriter).tryWriteResponse();
    }

    @Test
    public void shouldLockTask() throws InterruptedException, ExecutionException
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        // when
        mockController.processEvent(2L,
            event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3),
            metadata -> metadata
                .reqChannelId(4)
                .subscriptionId(5L));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCKED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(1)).tryWriteResponse();

        verify(mockSubscribedEventWriter, times(1)).tryWriteMessage();
        verify(mockSubscribedEventWriter).channelId(4);
        verify(mockSubscribedEventWriter).subscriptionId(5L);
        verify(mockSubscribedEventWriter).subscriptionType(SubscriptionType.TASK_SUBSCRIPTION);
    }

    @Test
    public void shouldLockFailedTask() throws InterruptedException, ExecutionException
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        mockController.processEvent(2L,
            event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3),
            metadata -> metadata
                .reqChannelId(4)
                .subscriptionId(5L));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.FAIL)
                .setLockOwner(3));

        // when
        mockController.processEvent(2L,
            event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3),
            metadata -> metadata
                .reqChannelId(6)
                .subscriptionId(7L));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCKED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(2)).tryWriteResponse();

        verify(mockSubscribedEventWriter, times(2)).tryWriteMessage();
        verify(mockSubscribedEventWriter).channelId(6);
        verify(mockSubscribedEventWriter).subscriptionId(7L);
    }

    @Test
    public void shouldLockExpiredTask()
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        mockController.processEvent(2L,
            event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3),
            metadata -> metadata
                .reqChannelId(4)
                .subscriptionId(5L));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.EXPIRE_LOCK)
                .setLockOwner(3));

        // when
        mockController.processEvent(2L,
            event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3),
            metadata -> metadata
                .reqChannelId(6)
                .subscriptionId(7L));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCKED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(1)).tryWriteResponse();

        verify(mockSubscribedEventWriter, times(2)).tryWriteMessage();
        verify(mockSubscribedEventWriter).channelId(6);
        verify(mockSubscribedEventWriter).subscriptionId(7L);
    }

    @Test
    public void shouldCompleteTask() throws InterruptedException, ExecutionException
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.COMPLETE)
                .setLockOwner(3));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.COMPLETED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(2)).tryWriteResponse();
    }

    @Test
    public void shouldCompleteExpiredTask() throws InterruptedException, ExecutionException
    {
        // given
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.CREATE));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.EXPIRE_LOCK)
                .setLockOwner(3));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.COMPLETE)
                .setLockOwner(3));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.COMPLETED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(2)).tryWriteResponse();
    }

    @Test
    public void shouldMarkTaskAsFailed() throws InterruptedException, ExecutionException
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.FAIL)
                .setLockOwner(3));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.FAILED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(2)).tryWriteResponse();
    }

    @Test
    public void shouldExpireTaskLock()
    {
        // given
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.CREATE));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.EXPIRE_LOCK));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK_EXPIRED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(1)).tryWriteResponse();
    }

    @Test
    public void shouldUpdateRetries() throws InterruptedException, ExecutionException
    {
        // given
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.CREATE)
                .setRetries(1));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.FAIL)
                .setLockOwner(3)
                .setRetries(0));
        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.UPDATE_RETRIES)
                .setLockOwner(2));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.RETRIES_UPDATED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(3)).tryWriteResponse();
    }

    @Test
    public void shouldCompensateLockRejection()
    {
        // given
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.CREATE)
                .setRetries(1));

        mockController.processEvent(2L,
            event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3),
            metadata -> metadata
                .subscriptionId(1L));

        // when
        mockController.processEvent(2L,
            event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(4),
            metadata -> metadata
                .subscriptionId(2L));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK_REJECTED);

        verify(mockTaskSubscriptionManager, times(1)).increaseSubscriptionCredits(2L, 1);
    }

    @Test
    public void shouldRejectLockTaskIfLockTimeIsNegative()
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE)
                .setType(TASK_TYPE));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(-1));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK_REJECTED);

        verify(mockResponseWriter, times(1)).tryWriteResponse();
        verify(mockSubscribedEventWriter, never()).tryWriteMessage();
    }

    @Test
    public void shouldRejectLockTaskIfLockTimeIsNull()
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(0));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK_REJECTED);

        verify(mockResponseWriter, times(1)).tryWriteResponse();
        verify(mockSubscribedEventWriter, never()).tryWriteMessage();
    }

    @Test
    public void shouldRejectLockTaskIfNotExist()
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        // when
        mockController.processEvent(4L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK_REJECTED);

        verify(mockResponseWriter, times(1)).tryWriteResponse();
        verify(mockSubscribedEventWriter, never()).tryWriteMessage();
    }

    @Test
    public void shouldRejectLockTaskIfAlreadyLocked()
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK_REJECTED);

        verify(mockResponseWriter, times(1)).tryWriteResponse();
        verify(mockSubscribedEventWriter, times(1)).tryWriteMessage();
    }

    @Test
    public void shouldRejectCompleteTaskIfNotExists()
    {
        // when
        mockController.processEvent(4L, event -> event
                .setEventType(TaskEventType.COMPLETE)
                .setLockOwner(3));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.COMPLETE_REJECTED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(1)).tryWriteResponse();
    }

    @Test
    public void shouldRejectCompleteTaskIfAlreadyCompleted()
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.COMPLETE)
                .setLockOwner(3));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.COMPLETE)
                .setLockOwner(3));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.COMPLETE_REJECTED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(3)).tryWriteResponse();
    }

    @Test
    public void shouldRejectCompleteTaskIfNotLocked()
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.COMPLETE)
                .setLockOwner(3));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.COMPLETE_REJECTED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(2)).tryWriteResponse();
    }

    @Test
    public void shouldRejectCompleteTaskIfLockedBySomeoneElse()
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.COMPLETE)
                .setLockOwner(5));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.COMPLETE_REJECTED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(2)).tryWriteResponse();
    }

    @Test
    public void shouldRejectMarkTaskAsFailedIfNotExists()
    {
        // when
        mockController.processEvent(4L, event -> event
                .setEventType(TaskEventType.FAIL)
                .setLockOwner(3));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.FAIL_REJECTED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(1)).tryWriteResponse();
    }

    @Test
    public void shouldRejectMarkTaskAsFailedIfAlreadyFailed()
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.FAIL)
                .setLockOwner(3));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.FAIL)
                .setLockOwner(3));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.FAIL_REJECTED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(3)).tryWriteResponse();
    }

    @Test
    public void shouldRejectMarkTaskAsFailedIfNotLocked()
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.FAIL)
                .setLockOwner(3));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.FAIL_REJECTED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(2)).tryWriteResponse();
    }

    @Test
    public void shouldRejectMarkTaskAsFailedIfAlreadyCompleted()
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.COMPLETE)
                .setLockOwner(3));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.FAIL)
                .setLockOwner(3));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.FAIL_REJECTED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(3)).tryWriteResponse();
    }

    @Test
    public void shouldRejectMarkTaskAsFailedIfLockedBySomeoneElse()
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.FAIL)
                .setLockOwner(5));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.FAIL_REJECTED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(2)).tryWriteResponse();
    }

    @Test
    public void shouldRejectExpireLockIfNotExists()
    {
        // when
        mockController.processEvent(4L, event -> event
                .setEventType(TaskEventType.EXPIRE_LOCK));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK_EXPIRATION_REJECTED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, never()).tryWriteResponse();
    }

    @Test
    public void shouldRejectExpireLockIfAlreadyExpired()
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.EXPIRE_LOCK));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.EXPIRE_LOCK));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK_EXPIRATION_REJECTED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(1)).tryWriteResponse();
    }

    @Test
    public void shouldRejectExpireLockIfNotLocked()
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.EXPIRE_LOCK)
                .setLockOwner(3));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK_EXPIRATION_REJECTED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(1)).tryWriteResponse();
    }

    @Test
    public void shouldRejectExpireLockIfAlreadyCompleted()
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.COMPLETE)
                .setLockOwner(3));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.EXPIRE_LOCK));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK_EXPIRATION_REJECTED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(2)).tryWriteResponse();
    }

    @Test
    public void shouldRejectExpireLockIfAlreadyFailed()
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.FAIL)
                .setLockOwner(3));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.EXPIRE_LOCK));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK_EXPIRATION_REJECTED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(2)).tryWriteResponse();
    }

    @Test
    public void shouldRejectUpdateRetriesIfNotExists()
    {
        // when
        mockController.processEvent(4L, event -> event
                .setEventType(TaskEventType.UPDATE_RETRIES)
                .setRetries(3));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.UPDATE_RETRIES_REJECTED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(1)).tryWriteResponse();
    }

    @Test
    public void shouldRejectUpdateRetriesIfCompleted()
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.COMPLETE)
                .setLockOwner(3));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.UPDATE_RETRIES));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.UPDATE_RETRIES_REJECTED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(3)).tryWriteResponse();
    }

    @Test
    public void shouldRejectUpdateRetriesIfLocked()
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.UPDATE_RETRIES));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.UPDATE_RETRIES_REJECTED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(2)).tryWriteResponse();
    }

}
