package org.camunda.tngp.broker.taskqueue.log.idx;

import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.camunda.tngp.broker.log.LogEntryHeaderReader;
import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.taskqueue.TaskInstanceWriter;
import org.camunda.tngp.broker.taskqueue.TestTaskQueueLogEntries;
import org.camunda.tngp.broker.taskqueue.log.TaskInstanceRequestWriter;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.protocol.log.TaskInstanceEncoder;
import org.camunda.tngp.protocol.log.TaskInstanceRequestType;
import org.camunda.tngp.protocol.log.TaskInstanceState;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class LockedTasksIndexWriterTest
{

    @Mock
    protected HashIndexManager<Long2LongHashIndex> indexManager;

    @Mock
    protected Long2LongHashIndex index;

    protected StubLogReader logReader;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        logReader = new StubLogReader(null);
        when(indexManager.getIndex()).thenReturn(index);
    }

    @Test
    public void shouldIndexLockedTaskInstance()
    {
        // given
        final LockedTasksIndexWriter indexWriter =
                new LockedTasksIndexWriter(indexManager, Templates.taskQueueLogTemplates());

        final TaskInstanceWriter writer =
                TestTaskQueueLogEntries.createTaskInstance(TaskInstanceState.LOCKED, 1L, 2L);
        logReader.addEntry(writer);

        final LogEntryHeaderReader reader = new LogEntryHeaderReader();
        logReader.next().readValue(reader);

        final long entryPosition = logReader.getEntryPosition(0);

        // when
        indexWriter.indexLogEntry(entryPosition, reader);

        // then
        verify(index).put(TestTaskQueueLogEntries.ID, entryPosition);
    }

    @Test
    public void shouldNotIndexNewTaskInstance()
    {
        // given
        final LockedTasksIndexWriter indexWriter =
                new LockedTasksIndexWriter(indexManager, Templates.taskQueueLogTemplates());

        final TaskInstanceWriter writer =
                TestTaskQueueLogEntries.createTaskInstance(
                        TaskInstanceState.NEW,
                        TaskInstanceEncoder.lockOwnerIdNullValue(),
                        TaskInstanceEncoder.lockTimeNullValue());

        logReader.addEntry(writer);

        final LogEntryHeaderReader reader = new LogEntryHeaderReader();
        logReader.next().readValue(reader);

        final long entryPosition = logReader.getEntryPosition(0);

        // when
        indexWriter.indexLogEntry(entryPosition, reader);

        // then
        verifyZeroInteractions(index);
    }

    @Test
    public void shouldIndexTaskCompleteRequest()
    {
        // given
        final LockedTasksIndexWriter indexWriter =
                new LockedTasksIndexWriter(indexManager, Templates.taskQueueLogTemplates());

        final TaskInstanceRequestWriter writer = new TaskInstanceRequestWriter()
            .type(TaskInstanceRequestType.COMPLETE)
            .key(123L)
            .source(EventSource.API);
        logReader.addEntry(writer);

        final LogEntryHeaderReader reader = new LogEntryHeaderReader();
        logReader.next().readValue(reader);

        final long entryPosition = logReader.getEntryPosition(0);

        // when
        indexWriter.indexLogEntry(entryPosition, reader);

        // then
        verify(index).remove(eq(123L), anyLong());
    }
}
