package org.camunda.tngp.broker.wf.repository.handler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.wf.repository.WfRepositoryContext;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;

public class MockedWfRepositoryContext extends WfRepositoryContext
{
    public MockedWfRepositoryContext()
    {
        super(101, "someName");

        final Dispatcher logWriteBufferMock = mock(Dispatcher.class);

        final Log logMock = mock(Log.class);
        when(logMock.getWriteBuffer()).thenReturn(logWriteBufferMock);


        final Bytes2LongHashIndex keyIndexMock = mock(Bytes2LongHashIndex.class);

        final HashIndexManager keyIndexManagerMock = mock(HashIndexManager.class);
        when(keyIndexManagerMock.getIndex()).thenReturn(keyIndexMock);

        final Long2LongHashIndex idIndexMock = mock(Long2LongHashIndex.class);

        final HashIndexManager idIndexManagerMock = mock(HashIndexManager.class);
        when(idIndexManagerMock.getIndex()).thenReturn(idIndexMock);

        final IdGenerator idGeneratorMock = mock(IdGenerator.class);

        setWfDefinitionLog(logMock);
        setWfDefinitionKeyIndex(keyIndexManagerMock);
        setWfDefinitionIdGenerator(idGeneratorMock);
        setWfDefinitionIdIndex(idIndexManagerMock);

        setLogWriter(mock(LogWriter.class));
    }

}
