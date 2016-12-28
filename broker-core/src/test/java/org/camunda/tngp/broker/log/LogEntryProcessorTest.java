package org.camunda.tngp.broker.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.camunda.tngp.broker.test.util.BufferReaderMatcher;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.protocol.log.TaskInstanceState;
import org.camunda.tngp.protocol.taskqueue.TaskInstanceReader;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class LogEntryProcessorTest
{

    protected StubLogReader logReader;

    @Mock
    protected LogEntryHandler<TaskInstanceReader> logEntryHandler;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        logReader = new StubLogReader(null);
    }

    @Test
    public void shouldHandleSingleEntry()
    {
        final LogEntryProcessor<TaskInstanceReader> logEntryProcessor = new LogEntryProcessor<>(logReader, new TaskInstanceReader(), logEntryHandler);

        logReader.addEntry(new TaskInstanceWriter().id(123L)
                .state(TaskInstanceState.COMPLETED));

        // when
        final int fragmentsProcessed = logEntryProcessor.doWork(Integer.MAX_VALUE);

        // then
        assertThat(fragmentsProcessed).isEqualTo(1);

        verify(logEntryHandler, times(1))
            .handle(
                    eq(logReader.getEntryPosition(0)),
                    argThat(BufferReaderMatcher.<TaskInstanceReader>readsProperties()
                            .matching((r) -> r.id(), 123L)));
        verifyNoMoreInteractions(logEntryHandler);
    }

    @Test
    public void shouldHandleMultipleEntries()
    {
        final LogEntryProcessor<TaskInstanceReader> logEntryProcessor = new LogEntryProcessor<>(logReader, new TaskInstanceReader(), logEntryHandler);

        logReader.addEntry(new TaskInstanceWriter().id(123L)
                .state(TaskInstanceState.COMPLETED));
        logReader.addEntry(new TaskInstanceWriter().id(456L)
                .state(TaskInstanceState.COMPLETED));

        // when
        final int fragmentsProcessed = logEntryProcessor.doWork(Integer.MAX_VALUE);

        // then
        assertThat(fragmentsProcessed).isEqualTo(2);

        final InOrder inOrder = inOrder(logEntryHandler);

        inOrder.verify(logEntryHandler, times(1))
            .handle(
                    eq(logReader.getEntryPosition(0)),
                    any());

        inOrder.verify(logEntryHandler, times(1))
            .handle(
                    eq(logReader.getEntryPosition(1)),
                    any());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void shouldNotProcessIfNoneAvailable()
    {
        // given
        final LogEntryProcessor<TaskInstanceReader> logEntryProcessor = new LogEntryProcessor<>(logReader, new TaskInstanceReader(), logEntryHandler);

        // when
        final int fragmentsProcessed = logEntryProcessor.doWork(Integer.MAX_VALUE);

        // then
        assertThat(fragmentsProcessed).isEqualTo(0);

        verifyZeroInteractions(logEntryHandler);
    }

    @Test
    public void shouldHandleEntryAgainIfPostponeResult()
    {
        // given
        final LogEntryProcessor<TaskInstanceReader> logEntryProcessor = new LogEntryProcessor<>(logReader, new TaskInstanceReader(), logEntryHandler);

        logReader.addEntry(new TaskInstanceWriter().id(123L)
                .state(TaskInstanceState.COMPLETED));

        when(logEntryHandler.handle(anyLong(), any())).thenReturn(
                LogEntryHandler.POSTPONE_ENTRY_RESULT,
                LogEntryHandler.CONSUME_ENTRY_RESULT);

        // when
        final int fragmentsProcessed = logEntryProcessor.doWork(Integer.MAX_VALUE);

        // then
        assertThat(fragmentsProcessed).isEqualTo(2);

        verify(logEntryHandler, times(2))
            .handle(
                    eq(logReader.getEntryPosition(0)),
                    argThat(BufferReaderMatcher.<TaskInstanceReader>readsProperties()
                            .matching((r) -> r.id(), 123L)));
        verifyNoMoreInteractions(logEntryHandler);
    }

    @Test
    public void shouldStopHandleEntriesIfFailedResult()
    {
        // given
        final LogEntryProcessor<TaskInstanceReader> logEntryProcessor = new LogEntryProcessor<>(logReader, new TaskInstanceReader(), logEntryHandler);

        logReader.addEntry(new TaskInstanceWriter().id(123L)
                .state(TaskInstanceState.COMPLETED));
        logReader.addEntry(new TaskInstanceWriter().id(456L)
                .state(TaskInstanceState.COMPLETED));

        when(logEntryHandler.handle(anyLong(), any())).thenReturn(
                LogEntryHandler.FAILED_ENTRY_RESULT,
                LogEntryHandler.CONSUME_ENTRY_RESULT);

        // when
        final int fragmentsProcessed = logEntryProcessor.doWork(Integer.MAX_VALUE);

        // then
        assertThat(fragmentsProcessed).isEqualTo(1);

        verify(logEntryHandler, times(1))
            .handle(
                    eq(logReader.getEntryPosition(0)),
                    argThat(BufferReaderMatcher.<TaskInstanceReader>readsProperties()
                            .matching((r) -> r.id(), 123L)));
        verifyNoMoreInteractions(logEntryHandler);
    }

}
