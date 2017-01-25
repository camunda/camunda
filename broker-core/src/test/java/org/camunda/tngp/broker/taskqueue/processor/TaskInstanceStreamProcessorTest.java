package org.camunda.tngp.broker.taskqueue.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import org.camunda.tngp.broker.taskqueue.data.TaskEvent;
import org.camunda.tngp.broker.taskqueue.data.TaskEventType;
import org.camunda.tngp.broker.test.util.FluentAnswer;
import org.camunda.tngp.broker.transport.clientapi.CommandResponseWriter;
import org.camunda.tngp.hashindex.store.IndexStore;
import org.camunda.tngp.logstreams.log.LogStreamWriter;
import org.camunda.tngp.logstreams.log.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskInstanceStreamProcessorTest
{
    private TaskInstanceStreamProcessor streamProcessor;
    private TaskEvent taskEvent;

    @Mock
    private LoggedEvent mockLoggedEvent;

    @Mock
    private IndexStore mockIndexStore;

    private CommandResponseWriter mockResponseWriter;
    private LogStreamWriter mockLogStreamWriter;

    @Before
    public void setup() throws InterruptedException, ExecutionException
    {
        MockitoAnnotations.initMocks(this);

        mockResponseWriter = mock(CommandResponseWriter.class, new FluentAnswer());

        mockLogStreamWriter = mock(LogStreamWriter.class, new FluentAnswer());
        when(mockLogStreamWriter.tryWrite()).thenReturn(1L);

        streamProcessor = new TaskInstanceStreamProcessor(mockResponseWriter, mockIndexStore);
    }

    @Test
    public void shouldCreateTask()
    {
        // when
        processTaskEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        // then
        assertThat(taskEvent.getEventType()).isEqualTo(TaskEventType.CREATED);

        verify(mockResponseWriter).longKey(2L);
        verify(mockResponseWriter).tryWriteResponse();
    }

    @Test
    public void shouldLockTask() throws InterruptedException, ExecutionException
    {
        // given
        processTaskEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        // when
        processTaskEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123));

        // then
        assertThat(taskEvent.getEventType()).isEqualTo(TaskEventType.LOCKED);

        verify(mockResponseWriter, times(2)).tryWriteResponse();
    }

    @Test
    public void shouldFailToLockTaskIfLockTimeIsNegative()
    {
        // given
        processTaskEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        // when
        processTaskEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(-1));

        // then
        assertThat(taskEvent.getEventType()).isEqualTo(TaskEventType.LOCK_FAILED);

        verify(mockResponseWriter, times(1)).tryWriteResponse();
    }

    @Test
    public void shouldFailToLockTaskIfLockTimeIsNull()
    {
        // given
        processTaskEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        // when
        processTaskEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(0));

        // then
        assertThat(taskEvent.getEventType()).isEqualTo(TaskEventType.LOCK_FAILED);

        verify(mockResponseWriter, times(1)).tryWriteResponse();
    }

    @Test
    public void shouldFailToLockTaskIfNotExist()
    {
        // given
        processTaskEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        // when
        processTaskEvent(4L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123));

        // then
        assertThat(taskEvent.getEventType()).isEqualTo(TaskEventType.LOCK_FAILED);

        verify(mockResponseWriter, times(1)).tryWriteResponse();
    }

    @Test
    public void shouldFailToLockTaskIfAlreadyLocked()
    {
        // given
        processTaskEvent(2L, event ->
            event.setEventType(TaskEventType.CREATE));

        processTaskEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123));

        // when
        processTaskEvent(2L, event -> event
                .setEventType(TaskEventType.LOCK)
                .setLockTime(123));

        // then
        assertThat(taskEvent.getEventType()).isEqualTo(TaskEventType.LOCK_FAILED);

        verify(mockResponseWriter, times(2)).tryWriteResponse();
    }

    protected void processTaskEvent(long key, Consumer<TaskEvent> taskBuilder)
    {
        when(mockLoggedEvent.getLongKey()).thenReturn(key);

        doAnswer(invocation ->
        {
            taskEvent = (TaskEvent) invocation.getArguments()[0];
            taskEvent.reset();

            taskBuilder.accept(taskEvent);
            return null;
        }).when(mockLoggedEvent).readValue(any());

        final EventProcessor eventProcessor = streamProcessor.onEvent(mockLoggedEvent);
        eventProcessor.processEvent();
        eventProcessor.executeSideEffects();
        eventProcessor.writeEvent(mockLogStreamWriter);
        eventProcessor.updateState();
    }

}
