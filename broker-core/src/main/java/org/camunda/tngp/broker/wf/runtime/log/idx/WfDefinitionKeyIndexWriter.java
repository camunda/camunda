package org.camunda.tngp.broker.wf.runtime.log.idx;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.log.LogEntryHeaderReader;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.log.idx.IndexWriter;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.wf.runtime.log.WfDefinitionReader;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.taskqueue.data.WfDefinitionDecoder;

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
        if (reader.templateId() == WfDefinitionDecoder.TEMPLATE_ID)
        {
            final WfDefinitionReader wfDefinitionReader = templates.getReader(Templates.WF_DEFINITION);
            reader.readInto(wfDefinitionReader);

            final DirectBuffer wfDefinitionKey = wfDefinitionReader.getTypeKey();
            index.put(wfDefinitionKey, 0, wfDefinitionKey.capacity(), wfDefinitionReader.id());
        }
    }

    @Override
    public HashIndexManager<?> getIndexManager()
    {
        return indexManager;
    }

}