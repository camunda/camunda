package org.camunda.tngp.broker.wf.runtime;

import static org.mockito.Mockito.mock;

import org.camunda.tngp.broker.wf.repository.WfTypeCacheService;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.impl.PrivateIdGenerator;
import org.mockito.MockitoAnnotations;

public class MockWfRuntimeContext extends WfRuntimeContext
{

    public MockWfRuntimeContext()
    {
        super(0, null);

        MockitoAnnotations.initMocks(this);

        setIdGenerator(new PrivateIdGenerator(0));
        setLogReader(mock(LogReader.class));
        setLogWriter(mock(LogWriter.class));
        setWfTypeCacheService(mock(WfTypeCacheService.class));
    }

}
