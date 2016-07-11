package org.camunda.tngp.broker.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.util.buffer.BufferReader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class LogEntryProcessorTest
{

    @Mock
    protected LogReader logReader;

    @Mock
    protected BufferReader bufferReader;

    @Mock
    protected LogEntryHandler<BufferReader> logEntryHandler;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldHandleSingleEntry()
    {
        final LogEntryProcessor<BufferReader> logEntryProcessor = new LogEntryProcessor<>(logReader, bufferReader, logEntryHandler);

        when(logReader.read(bufferReader)).thenReturn(true, false);
        when(logReader.position()).thenReturn(42L, -1L);

        // when
        final int fragmentsProcessed = logEntryProcessor.doWork(Integer.MAX_VALUE);

        // then
        assertThat(fragmentsProcessed).isEqualTo(1);

        final InOrder inOrder = inOrder(logEntryHandler, logReader);
        inOrder.verify(logReader).read(bufferReader);
        inOrder.verify(logEntryHandler, times(1)).handle(42L, bufferReader);
        verifyNoMoreInteractions(logEntryHandler);
    }

    @Test
    public void shouldHandlerMultipleEntries()
    {
        final LogEntryProcessor<BufferReader> logEntryProcessor = new LogEntryProcessor<>(logReader, bufferReader, logEntryHandler);

        when(logReader.read(bufferReader)).thenReturn(true, true, false);
        when(logReader.position()).thenReturn(42L, 6889L, -1L);

        // when
        final int fragmentsProcessed = logEntryProcessor.doWork(Integer.MAX_VALUE);

        // then
        assertThat(fragmentsProcessed).isEqualTo(2);

        final InOrder inOrder = inOrder(logEntryHandler, logReader);
        inOrder.verify(logReader).read(bufferReader);
        inOrder.verify(logEntryHandler, times(1)).handle(42L, bufferReader);
        inOrder.verify(logReader).read(bufferReader);
        inOrder.verify(logEntryHandler, times(1)).handle(6889L, bufferReader);
    }

    @Test
    public void shouldNotIndexIfNoneAvailable()
    {
        // given
        when(logReader.read(bufferReader)).thenReturn(false);

        final LogEntryProcessor<BufferReader> logEntryProcessor = new LogEntryProcessor<>(logReader, bufferReader, logEntryHandler);

        // when
        final int fragmentsProcessed = logEntryProcessor.doWork(Integer.MAX_VALUE);

        // then
        assertThat(fragmentsProcessed).isEqualTo(0);

        verifyZeroInteractions(logEntryHandler);
    }

}
