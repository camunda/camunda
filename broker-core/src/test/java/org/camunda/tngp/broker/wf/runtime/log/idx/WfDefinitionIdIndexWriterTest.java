package org.camunda.tngp.broker.wf.runtime.log.idx;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.camunda.tngp.broker.log.LogEntryHeaderReader;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.broker.wf.runtime.log.WfDefinitionWriter;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class WfDefinitionIdIndexWriterTest
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

        when(indexManager.getIndex()).thenReturn(index);

        logReader = new StubLogReader(null);
    }

    @Test
    public void shouldHandleSingleEntry()
    {
        // given
        final WfDefinitionIdIndexWriter indexWriter = new WfDefinitionIdIndexWriter(indexManager, Templates.wfRuntimeLogTemplates());

        final WfDefinitionWriter wfDefinitionWriter = new WfDefinitionWriter();
        wfDefinitionWriter.id(19L);

        logReader.addEntry(wfDefinitionWriter);

        final LogEntryHeaderReader headerReader = new LogEntryHeaderReader();
        logReader.next().readValue(headerReader);

        final long position = logReader.getEntryPosition(0);

        // when
        indexWriter.indexLogEntry(position, headerReader);

        // then
        verify(index).put(19L, position);
    }


    @Test
    public void shouldHandleMultipleEntries()
    {
        // given
        final WfDefinitionIdIndexWriter indexWriter = new WfDefinitionIdIndexWriter(indexManager, Templates.wfRuntimeLogTemplates());

        final WfDefinitionWriter wfDefinitionWriter = new WfDefinitionWriter();

        wfDefinitionWriter.id(19L);
        logReader.addEntry(wfDefinitionWriter);

        wfDefinitionWriter.id(20L);
        logReader.addEntry(wfDefinitionWriter);

        final LogEntryHeaderReader header1Reader = new LogEntryHeaderReader();
        logReader.next().readValue(header1Reader);
        final LogEntryHeaderReader header2Reader = new LogEntryHeaderReader();
        logReader.next().readValue(header2Reader);

        final long position1 = logReader.getEntryPosition(0);
        final long position2 = logReader.getEntryPosition(1);

        // when
        indexWriter.indexLogEntry(position1, header1Reader);
        indexWriter.indexLogEntry(position2, header2Reader);

        // then
        final InOrder inOrder = inOrder(index);

        inOrder.verify(index).put(19L, position1);
        inOrder.verify(index).put(20L, position2);
    }
}
