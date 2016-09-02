package org.camunda.tngp.broker.wf.runtime.log.idx;

import static org.camunda.tngp.broker.test.util.BufferMatcher.hasBytes;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.broker.log.LogEntryHeaderReader;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.util.mocks.StubLogReader;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionWriter;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class WfDefinitionKeyIndexWriterTest
{

    @Mock
    protected HashIndexManager<Bytes2LongHashIndex> indexManager;

    @Mock
    protected Bytes2LongHashIndex index;

    protected StubLogReader logReader;

    public static final byte[] KEY1 = "ref".getBytes(StandardCharsets.UTF_8);
    public static final byte[] KEY2 = "actoring".getBytes(StandardCharsets.UTF_8);


    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        when(indexManager.getIndex()).thenReturn(index);

        logReader = new StubLogReader(777L, null);
    }

    @Test
    public void shouldHandleSingleEntry()
    {
        // given
        final WfDefinitionKeyIndexWriter indexWriter = new WfDefinitionKeyIndexWriter(indexManager, Templates.wfRuntimeLogTemplates());

        final WfDefinitionWriter wfDefinitionWriter = new WfDefinitionWriter();
        wfDefinitionWriter.id(19L);
        wfDefinitionWriter.wfDefinitionKey(KEY1);

        logReader.addEntry(wfDefinitionWriter);

        final LogEntryHeaderReader headerReader = new LogEntryHeaderReader();
        logReader.read(headerReader);

        final long position = logReader.getEntryPosition(0);

        // when
        indexWriter.indexLogEntry(position, headerReader);

        // then
        verify(index).put(argThat(hasBytes(KEY1)), eq(0), eq(KEY1.length), eq(19L));
    }


    @Test
    public void shouldHandleMultipleEntries()
    {
        // given
        final WfDefinitionKeyIndexWriter indexWriter = new WfDefinitionKeyIndexWriter(indexManager, Templates.wfRuntimeLogTemplates());

        final WfDefinitionWriter wfDefinitionWriter = new WfDefinitionWriter();

        wfDefinitionWriter.id(19L);
        wfDefinitionWriter.wfDefinitionKey(KEY1);
        logReader.addEntry(wfDefinitionWriter);

        wfDefinitionWriter.id(20L);
        wfDefinitionWriter.wfDefinitionKey(KEY2);
        logReader.addEntry(wfDefinitionWriter);

        final LogEntryHeaderReader header1Reader = new LogEntryHeaderReader();
        logReader.read(header1Reader);
        final LogEntryHeaderReader header2Reader = new LogEntryHeaderReader();
        logReader.read(header2Reader);

        final long position1 = logReader.getEntryPosition(0);
        final long position2 = logReader.getEntryPosition(1);

        // when indexing the first entry
        indexWriter.indexLogEntry(position1, header1Reader);

        // then (asserting here is a little dirty, but we cannot do it after the second
        //   indexing operation, because Mockito captures only the object reference
        //   of the buffer that is handed to the index; Since we reuse the same buffer for both invocations,
        //   its internal state changes and asserting in the end would be "too late")
        final InOrder inOrder = inOrder(index);
        inOrder.verify(index).put(argThat(hasBytes(KEY1)), eq(0), eq(KEY1.length), eq(19L));

        // when indexing the second entry
        indexWriter.indexLogEntry(position2, header2Reader);

        // then
        inOrder.verify(index).put(argThat(hasBytes(KEY2)), eq(0), eq(KEY2.length), eq(20L));

    }
}
