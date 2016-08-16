package org.camunda.tngp.log;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.util.buffer.BufferWriter;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import uk.co.real_logic.agrona.MutableDirectBuffer;

public class LogEntryWriterTest
{
    Log logMock;
    Dispatcher logBufferMock;
    BufferWriter writerMock;

    LogEntryWriter logEntryWriter;
    ClaimedFragment claimedFragmentMock;

    @Before
    public void setup()
    {
        logMock = mock(Log.class);
        logBufferMock = mock(Dispatcher.class);

        when(logMock.getWriteBuffer()).thenReturn(logBufferMock);

        writerMock = mock(BufferWriter.class);

        claimedFragmentMock = mock(ClaimedFragment.class);
        logEntryWriter = new LogEntryWriter();
        logEntryWriter.claimedFragment = claimedFragmentMock;
    }

    @Test
    public void shouldWriteAndCommit()
    {
        final MutableDirectBuffer buffer = mock(MutableDirectBuffer.class);

        when(logMock.getId()).thenReturn(32);
        when(writerMock.getLength()).thenReturn(123);
        when(logBufferMock.claim(claimedFragmentMock, 123, 32)).thenReturn(150L);
        when(claimedFragmentMock.getBuffer()).thenReturn(buffer);
        when(claimedFragmentMock.getOffset()).thenReturn(432);

        final long result = logEntryWriter.write(logMock, writerMock);

        assertThat(result).isGreaterThanOrEqualTo(0);
        verify(writerMock).getLength();
        verify(logBufferMock).claim(claimedFragmentMock, 123, 32);

        final InOrder inOrder = inOrder(writerMock, claimedFragmentMock);
        inOrder.verify(writerMock).write(buffer, 432);
        inOrder.verify(claimedFragmentMock).commit();
    }

    @Test
    public void shouldRetryOnAdminAction()
    {
        final MutableDirectBuffer buffer = mock(MutableDirectBuffer.class);

        when(logMock.getId()).thenReturn(32);
        when(writerMock.getLength()).thenReturn(123);
        when(logBufferMock.claim(claimedFragmentMock, 123, 32)).thenReturn(-2L, 150L);
        when(claimedFragmentMock.getBuffer()).thenReturn(buffer);
        when(claimedFragmentMock.getOffset()).thenReturn(432);

        final long result = logEntryWriter.write(logMock, writerMock);

        assertThat(result).isGreaterThanOrEqualTo(0);
        verify(logBufferMock, times(2)).claim(claimedFragmentMock, 123, 32);
    }

    @Test
    public void shouldNotWriteIfBufferFull()
    {
        when(logMock.getId()).thenReturn(32);
        when(writerMock.getLength()).thenReturn(123);
        when(logBufferMock.claim(claimedFragmentMock, 123, 32)).thenReturn(-1L);

        final long result = logEntryWriter.write(logMock, writerMock);

        assertThat(result).isGreaterThanOrEqualTo(-1);

        verify(writerMock, never()).write(any(), anyInt());
        verify(claimedFragmentMock, never()).commit();
    }

}
