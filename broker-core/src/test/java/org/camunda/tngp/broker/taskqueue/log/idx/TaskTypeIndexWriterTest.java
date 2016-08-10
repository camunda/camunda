package org.camunda.tngp.broker.taskqueue.log.idx;

import static org.camunda.tngp.broker.test.util.BufferMatcher.hasBytes;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.camunda.tngp.broker.log.LogEntryHeaderReader;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.taskqueue.TaskInstanceWriter;
import org.camunda.tngp.broker.taskqueue.TestTaskQueueLogEntries;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.taskqueue.data.TaskInstanceEncoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskTypeIndexWriterTest
{

    @Mock
    protected HashIndexManager<Bytes2LongHashIndex> indexManager;

    @Mock
    protected Bytes2LongHashIndex index;

    protected StubLogReader logReader;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        logReader = new StubLogReader(666L, null);
        when(indexManager.getIndex()).thenReturn(index);
    }

    @Test
    public void shouldIndexLockedTaskInstance()
    {
        // given
        final TaskTypeIndexWriter indexWriter = new TaskTypeIndexWriter(indexManager, Templates.taskQueueLogTemplates());

        final long previousVersionPosition = 123L;

        final TaskInstanceWriter writer =
                TestTaskQueueLogEntries.createTaskInstance(TaskInstanceState.LOCKED, 1L, 2L);
        writer.prevVersionPosition(previousVersionPosition);
        final int entryLength = writer.getLength();

        logReader.addEntry(writer);


        final LogEntryHeaderReader reader = new LogEntryHeaderReader();
        logReader.read(reader);

        final long entryPosition = logReader.getEntryPosition(0);

        // when
        indexWriter.indexLogEntry(entryPosition, reader);

        // then
        verify(index).put(
                argThat(hasBytes(TestTaskQueueLogEntries.TASK_TYPE)),
                eq(0),
                eq(TestTaskQueueLogEntries.TASK_TYPE.length),
                eq(previousVersionPosition + DataFrameDescriptor.alignedLength(entryLength)));
    }

    @Test
    public void shouldNotIndexNewTaskInstance()
    {
        // given
        final TaskTypeIndexWriter indexWriter = new TaskTypeIndexWriter(indexManager, Templates.taskQueueLogTemplates());

        final TaskInstanceWriter writer =
                TestTaskQueueLogEntries.createTaskInstance(
                        TaskInstanceState.NEW,
                        TaskInstanceEncoder.lockOwnerIdNullValue(),
                        TaskInstanceEncoder.lockTimeNullValue());

        logReader.addEntry(writer);

        final LogEntryHeaderReader reader = new LogEntryHeaderReader();
        logReader.read(reader);

        final long entryPosition = logReader.getEntryPosition(0);

        // when
        indexWriter.indexLogEntry(entryPosition, reader);

        // then
        verifyZeroInteractions(index);
    }

}
