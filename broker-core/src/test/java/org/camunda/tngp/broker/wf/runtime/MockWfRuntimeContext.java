package org.camunda.tngp.broker.wf.runtime;

import static org.mockito.Mockito.mock;

public class MockWfRuntimeContext extends WfRuntimeContext
{

    public MockWfRuntimeContext()
    {
        super(0, null);

        setLogWriter(mock(LogWriter.class));
    }

}
