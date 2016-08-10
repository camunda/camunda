package org.camunda.tngp.broker.wf.runtime.log.idx;

import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.camunda.tngp.broker.log.LogEntryHeaderReader;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.broker.util.mocks.TestWfRuntimeLogEntries;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class BpmnEventIndexWriterTest
{

    protected StubLogReader logReader;

    @Mock
    protected HashIndexManager<Long2LongHashIndex> bpmnEventIndexManager;

    @Mock
    protected Long2LongHashIndex bpmnEventIndex;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        logReader = new StubLogReader(null);
        when(bpmnEventIndexManager.getIndex()).thenReturn(bpmnEventIndex);
    }

    @Test
    public void shouldWriteIndexOnActivityEvent()
    {
        // given
        final BpmnEventIndexWriter indexWriter =
                new BpmnEventIndexWriter(bpmnEventIndexManager, Templates.wfRuntimeLogTemplates());

        logReader.addEntry(TestWfRuntimeLogEntries.createActivityInstanceEvent(ExecutionEventType.ACT_INST_CREATED));

        final LogEntryHeaderReader reader = new LogEntryHeaderReader();
        logReader.read(reader);

        // when
        indexWriter.indexLogEntry(76L, reader);

        // then
        verify(bpmnEventIndex).put(TestWfRuntimeLogEntries.KEY, 76L);
        verifyNoMoreInteractions(bpmnEventIndex);

    }

    @Test
    public void shouldRemoveActivityInstanceOnComplete()
    {
        // given
        final BpmnEventIndexWriter indexWriter =
                new BpmnEventIndexWriter(bpmnEventIndexManager, Templates.wfRuntimeLogTemplates());

        logReader.addEntry(TestWfRuntimeLogEntries.createActivityInstanceEvent(ExecutionEventType.ACT_INST_COMPLETED));

        final LogEntryHeaderReader reader = new LogEntryHeaderReader();
        logReader.read(reader);

        // when
        indexWriter.indexLogEntry(76L, reader);

        // then
        verify(bpmnEventIndex).remove(eq(23456789L), anyLong());
        verifyNoMoreInteractions(bpmnEventIndex);
    }

    @Test
    public void shouldNotWriteIndexOnEventEvent()
    {
        // given
        final BpmnEventIndexWriter indexWriter =
                new BpmnEventIndexWriter(bpmnEventIndexManager, Templates.wfRuntimeLogTemplates());

        logReader.addEntry(TestWfRuntimeLogEntries.createFlowElementEvent());

        final LogEntryHeaderReader reader = new LogEntryHeaderReader();
        logReader.read(reader);

        // when
        indexWriter.indexLogEntry(76L, reader);

        // then
        verifyZeroInteractions(bpmnEventIndex);

    }

    @Test
    public void shouldWriteIndexOnProcessEvent()
    {
        // given
        final BpmnEventIndexWriter indexWriter =
                new BpmnEventIndexWriter(bpmnEventIndexManager, Templates.wfRuntimeLogTemplates());

        logReader.addEntry(TestWfRuntimeLogEntries.createProcessEvent(ExecutionEventType.PROC_INST_CREATED));

        final LogEntryHeaderReader reader = new LogEntryHeaderReader();
        logReader.read(reader);

        // when
        indexWriter.indexLogEntry(76L, reader);

        // then
        verify(bpmnEventIndex).put(TestWfRuntimeLogEntries.PROCESS_INSTANCE_ID, 76L);
        verifyNoMoreInteractions(bpmnEventIndex);
    }

    @Test
    public void shouldRemoveProcessEventOnComplete()
    {
        // given
        final BpmnEventIndexWriter indexWriter =
                new BpmnEventIndexWriter(bpmnEventIndexManager, Templates.wfRuntimeLogTemplates());

        logReader.addEntry(TestWfRuntimeLogEntries.createProcessEvent(ExecutionEventType.PROC_INST_COMPLETED));

        final LogEntryHeaderReader reader = new LogEntryHeaderReader();
        logReader.read(reader);

        // when
        indexWriter.indexLogEntry(76L, reader);

        // then
        verify(bpmnEventIndex).remove(TestWfRuntimeLogEntries.PROCESS_INSTANCE_ID, -1L);
        verifyNoMoreInteractions(bpmnEventIndex);
    }

}
