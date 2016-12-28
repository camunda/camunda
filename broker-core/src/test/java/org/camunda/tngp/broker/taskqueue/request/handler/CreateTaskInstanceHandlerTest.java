package org.camunda.tngp.broker.taskqueue.request.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.taskqueue.MockTaskQueueContext;
import org.camunda.tngp.broker.util.mocks.StubLogWriter;
import org.camunda.tngp.protocol.log.TaskInstanceDecoder;
import org.camunda.tngp.protocol.log.TaskInstanceState;
import org.camunda.tngp.protocol.taskqueue.TaskInstanceReader;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CreateTaskInstanceHandlerTest
{

    @Mock
    protected DirectBuffer message;

    @Mock
    protected DeferredResponse response;

    @Mock
    protected CreateTaskInstanceRequestReader requestReader;

    protected TaskQueueContext taskContext;
    protected StubLogWriter logWriter;
    protected IdGenerator idGenerator;

    public static final byte[] PAYLOAD = "foo".getBytes(StandardCharsets.UTF_8);
    public static final byte[] TASK_TYPE = "bar".getBytes(StandardCharsets.UTF_8);

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
        taskContext = new MockTaskQueueContext();

        logWriter = new StubLogWriter();
        taskContext.setLogWriter(logWriter);

        idGenerator = new PrivateIdGenerator(66L);
        taskContext.setTaskInstanceIdGenerator(idGenerator);

    }

    @Test
    public void shouldWriteValidRequestToLog()
    {
        // given
        final CreateTaskInstanceHandler handler = new CreateTaskInstanceHandler();

        when(requestReader.getPayload()).thenReturn(new UnsafeBuffer(PAYLOAD));
        when(requestReader.getTaskType()).thenReturn(new UnsafeBuffer(TASK_TYPE));

        handler.requestReader = requestReader;

        // when
        handler.onRequest(taskContext, message, 123, 456, response);

        // then
        verify(response).deferFifo();
        verifyNoMoreInteractions(response);

        assertThat(logWriter.size()).isEqualTo(1);

        final TaskInstanceReader taskInstanceReader = logWriter.getEntryAs(0, TaskInstanceReader.class);
        assertThat(taskInstanceReader.id()).isEqualTo(67L);
        assertThat(Integer.toUnsignedLong((int) taskInstanceReader.lockOwnerId()))
            .isEqualTo(TaskInstanceDecoder.lockOwnerIdNullValue()); // https://github.com/camunda-tngp/tasks/issues/16
        assertThat(taskInstanceReader.lockTime()).isEqualTo(TaskInstanceDecoder.lockTimeNullValue());
        assertThat(taskInstanceReader.state()).isEqualTo(TaskInstanceState.NEW);
        assertThatBuffer(taskInstanceReader.getPayload()).hasBytes(PAYLOAD);
        assertThatBuffer(taskInstanceReader.getTaskType()).hasBytes(TASK_TYPE);
    }
}
