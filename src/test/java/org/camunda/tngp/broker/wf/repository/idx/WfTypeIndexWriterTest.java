package org.camunda.tngp.broker.wf.repository.idx;

import static org.mockito.Mockito.*;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

import org.camunda.tngp.broker.wf.repository.handler.MockedWfRepositoryContext;
import org.camunda.tngp.broker.wf.repository.log.WfTypeReader;
import org.camunda.tngp.log.LogReader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import uk.co.real_logic.agrona.DirectBuffer;

public class WfTypeIndexWriterTest
{

    MockedWfRepositoryContext wfRepositoryCtx;
    WfTypeIndexWriter wfTypeIndexWriter;
    LogReader logReaderMock;
    WfTypeReader wfTypeReaderMock;

    @Before
    public void setup()
    {
        wfRepositoryCtx = new MockedWfRepositoryContext();
        wfTypeIndexWriter = new WfTypeIndexWriter(wfRepositoryCtx);

        logReaderMock = mock(LogReader.class);
        wfTypeIndexWriter.logReader = logReaderMock;

        wfTypeReaderMock = mock(WfTypeReader.class);
        wfTypeIndexWriter.reader = wfTypeReaderMock;
    }

    @Test
    public void shouldNotIndexIfNoneAvailable()
    {
        when(logReaderMock.read(wfTypeReaderMock)).thenReturn(false);

        final int fragmentsIndexed = wfTypeIndexWriter.update();

        assertThat(fragmentsIndexed).isEqualTo(0);

        verifyNoMoreInteractions(wfTypeIndexWriter.wfTypeIdIndex);
        verifyNoMoreInteractions(wfTypeIndexWriter.wfTypeKeyIndex);
    }

    @Test
    public void shouldIndexSingleEntry()
    {
        final byte[] key = new byte[]{ 0, 0, 1, 2, 3, 4 };
        final byte[] expectedIndexKey = new byte[WfTypeIndexWriter.WF_TYPE_BUFFER.length];
        Arrays.fill(expectedIndexKey, (byte)0);
        System.arraycopy(key, 0, expectedIndexKey, 0, key.length);

        // fill indexer's buffer with 1s to verify that the indexer fills it with 0s again
        Arrays.fill(WfTypeIndexWriter.WF_TYPE_BUFFER, (byte) 1);

        final DirectBuffer typeBufferMock = mock(DirectBuffer.class);

        when(logReaderMock.read(wfTypeReaderMock)).thenReturn(true, false);
        when(logReaderMock.position()).thenReturn(123l);
        when(wfTypeReaderMock.getTypeKey()).thenReturn(typeBufferMock);
        when(wfTypeReaderMock.id()).thenReturn(19l);

        when(typeBufferMock.capacity()).thenReturn(key.length);

        doAnswer((a) ->
        {
            final byte[] typeKey = (byte[]) a.getArguments()[1];
            System.arraycopy(key, 0, typeKey, 0, key.length);
            return null;
        }).when(typeBufferMock).getBytes(0, WfTypeIndexWriter.WF_TYPE_BUFFER, 0, key.length);

        final int fragmentsIndexed = wfTypeIndexWriter.update();

        assertThat(fragmentsIndexed).isEqualTo(1);

        verify(wfTypeIndexWriter.wfTypeIdIndex).put(19l, 123l);
        verify(wfTypeIndexWriter.wfTypeKeyIndex).put(expectedIndexKey, 19);
    }


    @Test
    public void shouldIndexMultipleEntries()
    {
        final DirectBuffer typeBufferMock = mock(DirectBuffer.class);

        when(logReaderMock.read(wfTypeReaderMock)).thenReturn(true, true, false);
        when(logReaderMock.position()).thenReturn(123l, 456l);
        when(wfTypeReaderMock.getTypeKey()).thenReturn(typeBufferMock);
        when(wfTypeReaderMock.id()).thenReturn(19l, 20l);

        final int fragmentsIndexed = wfTypeIndexWriter.update();

        assertThat(fragmentsIndexed).isEqualTo(2);

        InOrder idIndexInOrder = inOrder(wfTypeIndexWriter.wfTypeIdIndex);
        InOrder keyIndexInOrder = inOrder(wfTypeIndexWriter.wfTypeKeyIndex);

        idIndexInOrder.verify(wfTypeIndexWriter.wfTypeIdIndex).put(19l, 123l);
        idIndexInOrder.verify(wfTypeIndexWriter.wfTypeIdIndex).put(20l, 456l);

        keyIndexInOrder.verify(wfTypeIndexWriter.wfTypeKeyIndex).put(WfTypeIndexWriter.WF_TYPE_BUFFER, 19l);
        keyIndexInOrder.verify(wfTypeIndexWriter.wfTypeKeyIndex).put(WfTypeIndexWriter.WF_TYPE_BUFFER, 20l);
    }
}
