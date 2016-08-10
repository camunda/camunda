package org.camunda.tngp.broker.wf.runtime.log.idx;

import org.camunda.tngp.broker.log.LogEntryHeaderReader;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.log.idx.IndexWriter;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.wf.runtime.log.WfDefinitionRuntimeRequestReader;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.taskqueue.data.WfDefinitionRuntimeRequestDecoder;

import uk.co.real_logic.agrona.DirectBuffer;

public class WfDefinitionKeyIndexWriter implements IndexWriter
{

    protected HashIndexManager<Bytes2LongHashIndex> indexManager;
    protected Bytes2LongHashIndex index;
    protected Templates templates;

    public WfDefinitionKeyIndexWriter(HashIndexManager<Bytes2LongHashIndex> indexManager, Templates templates)
    {
        this.indexManager = indexManager;
        this.index = indexManager.getIndex();
        this.templates = templates;
    }


    @Override
    public void indexLogEntry(long position, LogEntryHeaderReader reader)
    {
        if (reader.templateId() == WfDefinitionRuntimeRequestDecoder.TEMPLATE_ID)
        {
            final WfDefinitionRuntimeRequestReader wfDefinitionReader = templates.getReader(Templates.WF_DEFINITION_RUNTIME_REQUEST);
            reader.readInto(wfDefinitionReader);

            final DirectBuffer wfDefinitionKey = wfDefinitionReader.key();
            index.put(wfDefinitionKey, 0, wfDefinitionKey.capacity(), wfDefinitionReader.id());
        }
    }

    @Override
    public HashIndexManager<?> getIndexManager()
    {
        return indexManager;
    }

}