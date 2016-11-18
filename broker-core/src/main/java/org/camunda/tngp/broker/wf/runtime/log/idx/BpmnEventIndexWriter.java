package org.camunda.tngp.broker.wf.runtime.log.idx;

import org.camunda.tngp.broker.log.LogEntryHeaderReader;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.log.idx.IndexWriter;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnBranchEventReader;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnProcessEventReader;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.protocol.log.BpmnActivityEventDecoder;
import org.camunda.tngp.protocol.log.BpmnBranchEventDecoder;
import org.camunda.tngp.protocol.log.BpmnProcessEventDecoder;

public class BpmnEventIndexWriter implements IndexWriter
{

    protected HashIndexManager<Long2LongHashIndex> indexManager;
    protected Long2LongHashIndex index;
    protected Templates templates;

    public BpmnEventIndexWriter(HashIndexManager<Long2LongHashIndex> indexManager, Templates templates)
    {
        this.indexManager = indexManager;
        this.index = indexManager.getIndex();
        this.templates = templates;
    }

    @Override
    public void indexLogEntry(long position, LogEntryHeaderReader reader)
    {
        if (reader.templateId() == BpmnActivityEventDecoder.TEMPLATE_ID)
        {
            final BpmnActivityEventReader bpmnActivityEventReader = templates.getReader(Templates.ACTIVITY_EVENT);
            reader.readInto(bpmnActivityEventReader);

            if (bpmnActivityEventReader.event() == ExecutionEventType.ACT_INST_COMPLETED)
            {
                index.remove(bpmnActivityEventReader.key(), -1L);
            }
            else
            {
                index.put(bpmnActivityEventReader.key(), position);
            }
        }
        else if (reader.templateId() == BpmnProcessEventDecoder.TEMPLATE_ID)
        {
            final BpmnProcessEventReader bpmnProcessEventReader = templates.getReader(Templates.PROCESS_EVENT);
            reader.readInto(bpmnProcessEventReader);

            if (bpmnProcessEventReader.event() == ExecutionEventType.PROC_INST_COMPLETED)
            {
                index.remove(bpmnProcessEventReader.processInstanceId(), -1L);
            }
            else
            {
                index.put(bpmnProcessEventReader.processInstanceId(), position);
            }
        }
        else if (reader.templateId() == BpmnBranchEventDecoder.TEMPLATE_ID)
        {
            // TODO: must write branch end events so that we can remove these index entries

            final BpmnBranchEventReader eventReader = templates.getReader(Templates.BPMN_BRANCH_EVENT);
            reader.readInto(eventReader);

            index.put(eventReader.key(), position);
        }
    }

    @Override
    public HashIndexManager<?> getIndexManager()
    {
        return indexManager;
    }
}