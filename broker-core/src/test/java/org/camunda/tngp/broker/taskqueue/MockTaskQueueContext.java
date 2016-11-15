package org.camunda.tngp.broker.taskqueue;

import static org.mockito.Mockito.*;

import org.camunda.tngp.broker.log.LogWriter;
import org.camunda.tngp.log.idgenerator.impl.PrivateIdGenerator;
import org.camunda.tngp.logstreams.LogStream;

public class MockTaskQueueContext extends TaskQueueContext
{
    public MockTaskQueueContext()
    {
        super(null, 0);

        setLog(mock(LogStream.class));
        setLogWriter(mock(LogWriter.class));
        setTaskInstanceIdGenerator(new PrivateIdGenerator(0L));
    }

}
