package org.camunda.tngp.broker.wf.runtime.log.idx;

import org.camunda.tngp.broker.log.LogEntryHeaderReader;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.log.idx.IndexWriter;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.wf.runtime.log.WfDefinitionReader;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.protocol.log.WfDefinitionDecoder;

public class WfDefinitionIdIndexWriter implements IndexWriter
{
    public static final String DEBUG_LOGGING_ENABLED_PROP_NAME = "camunda.debug.logging.enabled";
    public static final boolean DEBUG_LOGGING_ENABLED = Boolean.getBoolean(DEBUG_LOGGING_ENABLED_PROP_NAME);

    protected HashIndexManager<Long2LongHashIndex> indexManager;
    protected Long2LongHashIndex index;
    protected Templates templates;

    public WfDefinitionIdIndexWriter(HashIndexManager<Long2LongHashIndex> indexManager, Templates templates)
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
            final WfDefinitionReader requestReader = templates.getReader(Templates.WF_DEFINITION);
            reader.readInto(requestReader);

            if (DEBUG_LOGGING_ENABLED)
            {
                System.out.println("Indexing wf definition at position " + position);
            }

            index.put(requestReader.id(), position);
        }
    }

    @Override
    public HashIndexManager<?> getIndexManager()
    {
        return indexManager;
    }

}
