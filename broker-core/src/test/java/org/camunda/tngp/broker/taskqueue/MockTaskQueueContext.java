package org.camunda.tngp.broker.taskqueue;

import static org.mockito.Mockito.mock;

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
