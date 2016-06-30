package org.camunda.tngp.broker.wf.runtime;

import static org.mockito.Mockito.mock;

import org.camunda.tngp.broker.wf.repository.WfTypeCacheService;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.impl.PrivateIdGenerator;

public class MockWfRuntimeContext extends WfRuntimeContext
{

    public MockWfRuntimeContext()
    {
        super(0, null);

        setIdGenerator(new PrivateIdGenerator(0));
        setLogWriter(mock(LogWriter.class));
        setWfTypeCacheService(mock(WfTypeCacheService.class));
    }

}
