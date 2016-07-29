package org.camunda.tngp.broker.wf.repository.idx;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.camunda.tngp.broker.log.LogEntryProcessor;
import org.camunda.tngp.broker.wf.repository.handler.MockedWfRepositoryContext;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionReader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class WfDefinitionIndexWriterTest
{

    MockedWfRepositoryContext wfRepositoryCtx;
    WfDefinitionIndexWriter wfDefinitionIndexWriter;
    WfDefinitionReader wfDefinitionReaderMock;
    LogEntryProcessor<WfDefinitionReader> logEntryProcessor;

    @Before
    public void setup()
    {
        wfRepositoryCtx = new MockedWfRepositoryContext();
        wfDefinitionIndexWriter = new WfDefinitionIndexWriter(wfRepositoryCtx);

        wfDefinitionReaderMock = mock(WfDefinitionReader.class);

        logEntryProcessor = mock(LogEntryProcessor.class);
        wfDefinitionIndexWriter.logEntryProcessor = logEntryProcessor;
    }

    @Test
    public void shouldDelegateToLogEntryProcessor()
    {
        // when
        wfDefinitionIndexWriter.update();

        // then
        verify(logEntryProcessor).doWork(Integer.MAX_VALUE);
        verifyNoMoreInteractions(logEntryProcessor);
    }

    @Test
    public void shouldHandleSingleEntry()
    {
        // given
        final byte[] key = new byte[]{ 0, 0, 1, 2, 3, 4 };
        final DirectBuffer typeBuffer = new UnsafeBuffer(key);

        when(wfDefinitionReaderMock.getTypeKey()).thenReturn(typeBuffer);
        when(wfDefinitionReaderMock.id()).thenReturn(19L);

        // when
        wfDefinitionIndexWriter.handle(123L, wfDefinitionReaderMock);

        // then
        verify(wfDefinitionIndexWriter.wfDefinitionIdIndex).put(19L, 123L);
        verify(wfDefinitionIndexWriter.wfDefinitionKeyIndex).put(typeBuffer, 0, key.length, 19);
    }


    @Test
    public void shouldHandleMultipleEntries()
    {
        final byte[] key = new byte[]{ 0, 0, 1, 2, 3, 4 };
        final DirectBuffer typeBuffer = new UnsafeBuffer(key);

        when(wfDefinitionReaderMock.id()).thenReturn(19L, 20L);
        when(wfDefinitionReaderMock.getTypeKey()).thenReturn(typeBuffer);

        // when
        wfDefinitionIndexWriter.handle(123L, wfDefinitionReaderMock);
        wfDefinitionIndexWriter.handle(456L, wfDefinitionReaderMock);

        // then
        final InOrder idIndexInOrder = inOrder(wfDefinitionIndexWriter.wfDefinitionIdIndex);
        final InOrder keyIndexInOrder = inOrder(wfDefinitionIndexWriter.wfDefinitionKeyIndex);

        idIndexInOrder.verify(wfDefinitionIndexWriter.wfDefinitionIdIndex).put(19L, 123L);
        idIndexInOrder.verify(wfDefinitionIndexWriter.wfDefinitionIdIndex).put(20L, 456L);

        keyIndexInOrder.verify(wfDefinitionIndexWriter.wfDefinitionKeyIndex).put(typeBuffer, 0, key.length, 19L);
        keyIndexInOrder.verify(wfDefinitionIndexWriter.wfDefinitionKeyIndex).put(typeBuffer, 0, key.length, 20L);
    }
}
