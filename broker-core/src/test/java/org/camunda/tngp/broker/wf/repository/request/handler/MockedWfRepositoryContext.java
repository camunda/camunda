package org.camunda.tngp.broker.wf.repository.request.handler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.camunda.tngp.broker.log.LogWriter;
import org.camunda.tngp.broker.wf.repository.WfRepositoryContext;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.Log;

public class MockedWfRepositoryContext extends WfRepositoryContext
{
    public MockedWfRepositoryContext()
    {
        super(101, "someName");

        final Dispatcher logWriteBufferMock = mock(Dispatcher.class);

        final Log logMock = mock(Log.class);
        when(logMock.getWriteBuffer()).thenReturn(logWriteBufferMock);

        setLogWriter(mock(LogWriter.class));
    }

}
