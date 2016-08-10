package org.camunda.tngp.broker.wf.runtime.log.handler.bpmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.broker.log.LogEntryHeaderReader;
import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.taskqueue.TaskInstanceReader;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContextProvider;
import org.camunda.tngp.broker.util.mocks.StubLogWriter;
import org.camunda.tngp.broker.util.mocks.StubResponseControl;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.broker.wf.runtime.log.ActivityInstanceRequestReader;
import org.camunda.tngp.broker.wf.runtime.log.handler.InputTaskHandler;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class InputTaskHandlerTest
{

    @Mock
    protected TaskInstanceReader taskInstanceReader;

    @Mock
    protected ResourceContextProvider<WfRuntimeContext> runtimeContextProvider;

    @Mock
    protected WfRuntimeContext runtimeContext;

    protected StubLogWriter logWriter;
    protected StubResponseControl responseControl;

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

        when(runtimeContextProvider.getContextForResource(7)).thenReturn(runtimeContext);
        when(runtimeContext.getLogWriter()).thenReturn(logWriter);

    }

    @Test
    public void shouldHandleCompleteEvent()
    {
        // given
        final InputTaskHandler handler = new InputTaskHandler(runtimeContextProvider);

        mockTaskInstanceEvent(TaskInstanceState.COMPLETED);

        // when
        handler.handle(taskInstanceReader, responseControl);

        // then
        assertThat(logWriter.size()).isEqualTo(1);
        final ActivityInstanceRequestReader newEntry = logWriter.getEntryAs(0, ActivityInstanceRequestReader.class);

        assertThat(newEntry.activityInstanceKey()).isEqualTo(23456789L);

        final LogEntryHeaderReader newEntryHeader = logWriter.getEntryAs(0, LogEntryHeaderReader.class);
        assertThat(newEntryHeader.source()).isEqualTo(EventSource.EXTERNAL_LOG);
    }

    @Test
    public void shouldNotHandleLockedEvent()
    {
        // given
        final InputTaskHandler handler = new InputTaskHandler(runtimeContextProvider);

        mockTaskInstanceEvent(TaskInstanceState.LOCKED);

        // when
        handler.handle(taskInstanceReader, responseControl);

        // then
        assertThat(logWriter.size()).isEqualTo(0);
    }

}
