package org.camunda.tngp.broker.idx;

import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.taskqueue.TaskInstanceWriter;
import org.camunda.tngp.broker.taskqueue.log.TaskInstanceReader;
import org.camunda.tngp.broker.test.util.BufferReaderMatcher;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.dispatcher.impl.Subscription;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class IndexWriterTest
{
    protected StubLogReader logReader;

    @Mock
    protected Subscription subscription;

    @Mock
    protected LogEntryTracker<TaskInstanceReader> logEntryTracker;

    @Mock
    protected HashIndexManager<Long2LongHashIndex> indexManager;

    protected IndexWriter<TaskInstanceReader> indexWriter;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        logReader = new StubLogReader(null);

        indexWriter = new IndexWriter<>(
                logReader,
                subscription,
                10,
                new TaskInstanceReader(),
                logEntryTracker,
                new HashIndexManager<?>[]{indexManager});
    }

    @Test
    public void shouldIndexEntry()
    {
        // given
        logReader.addEntry(new TaskInstanceWriter().id(123L)
                .state(TaskInstanceState.COMPLETED));

        // when
        indexWriter.indexLogEntries();

        // then
        verify(logEntryTracker).onLogEntryCommit(argThat(
                BufferReaderMatcher.<TaskInstanceReader>readsProperties()
                    .matching((r) -> r.id(), 123L)),
                eq(0L));
        verifyNoMoreInteractions(logEntryTracker);
    }

}
