package org.camunda.tngp.broker.wf.repository.idx;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.camunda.tngp.broker.wf.repository.log.WfDefinitionReader;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class WfDefinitionIndexWriterTest
{

    WfTypeIndexLogTracker wfDefinitionIndexWriter;

    @Mock
    WfDefinitionReader wfDefinitionReaderMock;

    @Mock
    protected Long2LongHashIndex wfDefinitionIdIndex;

    @Mock
    protected Bytes2LongHashIndex wfDefinitionKeyIndex;



    @Before
    public void setup()
    {
        MockitoAnnotations.initMocks(this);

        wfDefinitionIndexWriter = new WfTypeIndexLogTracker(wfDefinitionIdIndex, wfDefinitionKeyIndex);
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
        wfDefinitionIndexWriter.onLogEntryCommit(wfDefinitionReaderMock, 123L);

        // then
        verify(wfDefinitionIndexWriter.wfTypeIdIndex).put(19L, 123L);
        verify(wfDefinitionIndexWriter.wfTypeKeyIndex).put(typeBuffer, 0, key.length, 19);
    }


    @Test
    public void shouldHandleMultipleEntries()
    {
        final byte[] key = new byte[]{ 0, 0, 1, 2, 3, 4 };
        final DirectBuffer typeBuffer = new UnsafeBuffer(key);

        when(wfDefinitionReaderMock.id()).thenReturn(19L, 20L);
        when(wfDefinitionReaderMock.getTypeKey()).thenReturn(typeBuffer);

        // when
        wfDefinitionIndexWriter.onLogEntryCommit(wfDefinitionReaderMock, 123L);
        wfDefinitionIndexWriter.onLogEntryCommit(wfDefinitionReaderMock, 456L);

        // then
        final InOrder idIndexInOrder = inOrder(wfDefinitionIndexWriter.wfTypeIdIndex);
        final InOrder keyIndexInOrder = inOrder(wfDefinitionIndexWriter.wfTypeKeyIndex);

        idIndexInOrder.verify(wfDefinitionIndexWriter.wfTypeIdIndex).put(19L, 123L);
        idIndexInOrder.verify(wfDefinitionIndexWriter.wfTypeIdIndex).put(20L, 456L);

        keyIndexInOrder.verify(wfDefinitionIndexWriter.wfTypeKeyIndex).put(typeBuffer, 0, key.length, 19L);
        keyIndexInOrder.verify(wfDefinitionIndexWriter.wfTypeKeyIndex).put(typeBuffer, 0, key.length, 20L);
    }
}
