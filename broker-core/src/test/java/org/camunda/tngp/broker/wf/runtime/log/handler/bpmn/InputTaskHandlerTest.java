package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.logstreams.LogEntryHeaderReader;
import org.camunda.tngp.broker.logstreams.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.util.mocks.StubLogWriter;
import org.camunda.tngp.broker.util.mocks.StubLogWriters;
import org.camunda.tngp.broker.util.mocks.StubResponseControl;
import org.camunda.tngp.broker.wf.runtime.log.ActivityInstanceRequestReader;
import org.camunda.tngp.broker.wf.runtime.log.handler.InputTaskHandler;
import org.camunda.tngp.protocol.log.TaskInstanceState;
import org.camunda.tngp.protocol.taskqueue.TaskInstanceReader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class InputTaskHandlerTest
{

    @Mock
    protected TaskInstanceReader taskInstanceReader;

    protected StubLogWriter logWriter;
    protected StubResponseControl responseControl;
    protected StubLogWriters logWriters;

    protected DirectBuffer payloadBuffer = new UnsafeBuffer("PingPong".getBytes(StandardCharsets.UTF_8));
    protected DirectBuffer taskTypeBuffer = new UnsafeBuffer("Foobar".getBytes(StandardCharsets.UTF_8));

    protected void mockTaskInstanceEvent(TaskInstanceState taskState)
    {
        when(taskInstanceReader.getPayload()).thenReturn(payloadBuffer);
        when(taskInstanceReader.getTaskType()).thenReturn(taskTypeBuffer);
        when(taskInstanceReader.id()).thenReturn(1234L);
        when(taskInstanceReader.lockOwnerId()).thenReturn(987L);
        when(taskInstanceReader.lockTime()).thenReturn(890L);
        when(taskInstanceReader.prevVersionPosition()).thenReturn(583L);
        when(taskInstanceReader.resourceId()).thenReturn(98);
        when(taskInstanceReader.shardId()).thenReturn(67);
        when(taskInstanceReader.state()).thenReturn(taskState);
        when(taskInstanceReader.taskTypeHash()).thenReturn(1235L);
        when(taskInstanceReader.version()).thenReturn(9876);
        when(taskInstanceReader.wfActivityInstanceEventKey()).thenReturn(23456789L);
        when(taskInstanceReader.wfRuntimeResourceId()).thenReturn(7);
    }

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        logWriter = new StubLogWriter();
        responseControl = new StubResponseControl();
        logWriters = new StubLogWriters(0);
        logWriters.addWriter(7, logWriter);

    }

    @Test
    public void shouldHandleCompleteEvent()
    {
        // given
        final InputTaskHandler handler = new InputTaskHandler();

        mockTaskInstanceEvent(TaskInstanceState.COMPLETED);

        // when
        handler.handle(taskInstanceReader, responseControl, logWriters);

        // then
        assertThat(logWriter.size()).isEqualTo(1);
        final ActivityInstanceRequestReader newEntry = logWriter.getEntryAs(0, ActivityInstanceRequestReader.class);

        assertThat(newEntry.activityInstanceKey()).isEqualTo(23456789L);

        final LogEntryHeaderReader newEntryHeader = logWriter.getEntryAs(0, LogEntryHeaderReader.class);
        assertThat(newEntryHeader.source()).isEqualTo(EventSource.LOG);
    }

    @Test
    public void shouldNotHandleLockedEvent()
    {
        // given
        final InputTaskHandler handler = new InputTaskHandler();

        mockTaskInstanceEvent(TaskInstanceState.LOCKED);

        // when
        handler.handle(taskInstanceReader, responseControl, logWriters);

        // then
        assertThat(logWriter.size()).isEqualTo(0);
    }

}
