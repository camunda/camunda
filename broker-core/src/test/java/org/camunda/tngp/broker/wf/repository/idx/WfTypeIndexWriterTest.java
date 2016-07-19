package org.camunda.tngp.broker.wf.repository.idx;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.camunda.tngp.broker.log.LogEntryProcessor;
import org.camunda.tngp.broker.wf.repository.handler.MockedWfRepositoryContext;
import org.camunda.tngp.broker.wf.repository.log.WfTypeReader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class WfTypeIndexWriterTest
{

    MockedWfRepositoryContext wfRepositoryCtx;
    WfTypeIndexWriter wfTypeIndexWriter;
    WfTypeReader wfTypeReaderMock;
    LogEntryProcessor<WfTypeReader> logEntryProcessor;

    @Before
    public void setup()
    {
        wfRepositoryCtx = new MockedWfRepositoryContext();
        wfTypeIndexWriter = new WfTypeIndexWriter(wfRepositoryCtx);

        wfTypeReaderMock = mock(WfTypeReader.class);

        logEntryProcessor = mock(LogEntryProcessor.class);
        wfTypeIndexWriter.logEntryProcessor = logEntryProcessor;
    }

    @Test
    public void shouldDelegateToLogEntryProcessor()
    {
        // when
        wfTypeIndexWriter.update();

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

        when(wfTypeReaderMock.getTypeKey()).thenReturn(typeBuffer);
        when(wfTypeReaderMock.id()).thenReturn(19L);

        // when
        wfTypeIndexWriter.handle(123L, wfTypeReaderMock);

        // then
        verify(wfTypeIndexWriter.wfTypeIdIndex).put(19L, 123L);
        verify(wfTypeIndexWriter.wfTypeKeyIndex).put(typeBuffer, 0, key.length, 19);
    }


    @Test
    public void shouldHandleMultipleEntries()
    {
        final byte[] key = new byte[]{ 0, 0, 1, 2, 3, 4 };
        final DirectBuffer typeBuffer = new UnsafeBuffer(key);

        when(wfTypeReaderMock.id()).thenReturn(19L, 20L);
        when(wfTypeReaderMock.getTypeKey()).thenReturn(typeBuffer);

        // when
        wfTypeIndexWriter.handle(123L, wfTypeReaderMock);
        wfTypeIndexWriter.handle(456L, wfTypeReaderMock);

        // then
        final InOrder idIndexInOrder = inOrder(wfTypeIndexWriter.wfTypeIdIndex);
        final InOrder keyIndexInOrder = inOrder(wfTypeIndexWriter.wfTypeKeyIndex);

        idIndexInOrder.verify(wfTypeIndexWriter.wfTypeIdIndex).put(19L, 123L);
        idIndexInOrder.verify(wfTypeIndexWriter.wfTypeIdIndex).put(20L, 456L);

        keyIndexInOrder.verify(wfTypeIndexWriter.wfTypeKeyIndex).put(typeBuffer, 0, key.length, 19L);
        keyIndexInOrder.verify(wfTypeIndexWriter.wfTypeKeyIndex).put(typeBuffer, 0, key.length, 20L);
    }
}
