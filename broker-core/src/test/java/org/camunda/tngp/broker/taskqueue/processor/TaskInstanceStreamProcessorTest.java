package org.camunda.tngp.broker.taskqueue.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.protocol.clientapi.EventType.TASK_EVENT;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.Constants;
import org.camunda.tngp.broker.taskqueue.data.TaskEvent;
import org.camunda.tngp.broker.taskqueue.data.TaskEventType;
import org.camunda.tngp.broker.test.MockStreamProcessorController;
import org.camunda.tngp.broker.test.util.FluentMock;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.broker.util.msgpack.MsgPackUtil;
import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
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

    @Rule
    public MockStreamProcessorController<TaskEvent> mockController = new MockStreamProcessorController<>(
        TaskEvent.class,
        (t) -> t.setType(TASK_TYPE).setEventType(TaskEventType.CREATED),
        TASK_EVENT);

    @Before
    public void setup() throws InterruptedException, ExecutionException
    {
        MockitoAnnotations.initMocks(this);

        when(mockLogStream.getId()).thenReturn(1);

        streamProcessor = new TaskInstanceStreamProcessor(mockResponseWriter, mockIndexStore);

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
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123)
                .setLockOwner(3));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCKED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(2)).tryWriteResponse();
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

        verify(mockResponseWriter, times(3)).tryWriteResponse();
    }

    @Test
    public void shouldFailToLockTaskIfLockTimeIsNegative()
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
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK_FAILED);

        verify(mockResponseWriter, times(1)).tryWriteResponse();
    }

    @Test
    public void shouldFailToLockTaskIfLockTimeIsNull()
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(0));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK_FAILED);

        verify(mockResponseWriter, times(1)).tryWriteResponse();
    }

    @Test
    public void shouldFailToLockTaskIfNotExist()
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        // when
        mockController.processEvent(4L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK_FAILED);

        verify(mockResponseWriter, times(1)).tryWriteResponse();
    }

    @Test
    public void shouldFailToLockTaskIfAlreadyLocked()
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
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.LOCK_FAILED);

        verify(mockResponseWriter, times(2)).tryWriteResponse();
    }

    @Test
    public void shouldFailToCompleteTaskIfNotExists()
    {
        // when
        mockController.processEvent(4L, event -> event
                .setEventType(TaskEventType.COMPLETE)
                .setLockOwner(3));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.COMPLETE_FAILED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(1)).tryWriteResponse();
    }

    @Test
    public void shouldFailToCompleteTaskIfAlreadyCompleted()
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
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.COMPLETE_FAILED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(4)).tryWriteResponse();
    }

    @Test
    public void shouldFailToCompleteTaskIfNotLocked()
    {
        // given
        mockController.processEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        // when
        mockController.processEvent(2L, event -> event
                .setEventType(TaskEventType.COMPLETE)
                .setLockOwner(3));

        // then
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.COMPLETE_FAILED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(2)).tryWriteResponse();
    }

    @Test
    public void shouldFailToCompleteTaskIfLockedBySomeoneElse()
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
        assertThat(mockController.getLastWrittenEventValue().getEventType()).isEqualTo(TaskEventType.COMPLETE_FAILED);
        assertThat(mockController.getLastWrittenEventMetadata().getProtocolVersion()).isEqualTo(Constants.PROTOCOL_VERSION);

        verify(mockResponseWriter, times(3)).tryWriteResponse();
    }

}
