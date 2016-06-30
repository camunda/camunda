package org.camunda.tngp.broker.taskqueue;

import static org.mockito.Mockito.mock;

import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.impl.PrivateIdGenerator;

public class MockTaskQueueContext extends TaskQueueContext
{
    public MockTaskQueueContext()
    {
        super(null, 0);

        setLogWriter(mock(LogWriter.class));
        setTaskInstanceIdGenerator(new PrivateIdGenerator(0L));
    }

}
