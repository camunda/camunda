package org.camunda.tngp.broker.wf.repository.idx;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.camunda.tngp.broker.log.LogEntryProcessor;
import org.camunda.tngp.broker.wf.repository.handler.MockedWfRepositoryContext;
import org.camunda.tngp.broker.wf.repository.log.WfTypeReader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import uk.co.real_logic.agrona.DirectBuffer;

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
        final byte[] key = new byte[]{ 0, 0, 1, 2, 3, 4 };
        final byte[] expectedIndexKey = new byte[WfTypeIndexWriter.WF_TYPE_BUFFER.length];
        Arrays.fill(expectedIndexKey, (byte)0);
        System.arraycopy(key, 0, expectedIndexKey, 0, key.length);

        // fill indexer's buffer with 1s to verify that the indexer fills it with 0s again
        Arrays.fill(WfTypeIndexWriter.WF_TYPE_BUFFER, (byte) 1);

        final DirectBuffer typeBufferMock = mock(DirectBuffer.class);

        when(wfTypeReaderMock.getTypeKey()).thenReturn(typeBufferMock);
        when(wfTypeReaderMock.id()).thenReturn(19L);

        when(typeBufferMock.capacity()).thenReturn(key.length);

        doAnswer((a) ->
        {
            final byte[] typeKey = (byte[]) a.getArguments()[1];
            System.arraycopy(key, 0, typeKey, 0, key.length);
            return null;
        }).when(typeBufferMock).getBytes(0, WfTypeIndexWriter.WF_TYPE_BUFFER, 0, key.length);

        // when
        wfTypeIndexWriter.handle(123L, wfTypeReaderMock);

        // then
        verify(wfTypeIndexWriter.wfTypeIdIndex).put(19L, 123L);
        verify(wfTypeIndexWriter.wfTypeKeyIndex).put(expectedIndexKey, 19);
    }


    @Test
    public void shouldHandleMultipleEntries()
    {
        final DirectBuffer typeBufferMock = mock(DirectBuffer.class);

        when(wfTypeReaderMock.getTypeKey()).thenReturn(typeBufferMock);
        when(wfTypeReaderMock.id()).thenReturn(19L, 20L);

        // when
        wfTypeIndexWriter.handle(123L, wfTypeReaderMock);
        wfTypeIndexWriter.handle(456L, wfTypeReaderMock);

        // then
        final InOrder idIndexInOrder = inOrder(wfTypeIndexWriter.wfTypeIdIndex);
        final InOrder keyIndexInOrder = inOrder(wfTypeIndexWriter.wfTypeKeyIndex);

        idIndexInOrder.verify(wfTypeIndexWriter.wfTypeIdIndex).put(19L, 123L);
        idIndexInOrder.verify(wfTypeIndexWriter.wfTypeIdIndex).put(20L, 456L);

        keyIndexInOrder.verify(wfTypeIndexWriter.wfTypeKeyIndex).put(WfTypeIndexWriter.WF_TYPE_BUFFER, 19L);
        keyIndexInOrder.verify(wfTypeIndexWriter.wfTypeKeyIndex).put(WfTypeIndexWriter.WF_TYPE_BUFFER, 20L);
    }
}
