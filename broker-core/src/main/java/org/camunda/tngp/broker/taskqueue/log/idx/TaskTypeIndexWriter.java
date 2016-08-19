package org.camunda.tngp.broker.taskqueue.log.idx;

import org.camunda.tngp.broker.log.LogEntryHeaderReader;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.log.idx.IndexWriter;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.taskqueue.TaskInstanceReader;
import org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.taskqueue.data.TaskInstanceDecoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;

import org.agrona.DirectBuffer;

public class TaskTypeIndexWriter implements IndexWriter
{

    protected HashIndexManager<Bytes2LongHashIndex> indexManager;
    protected Bytes2LongHashIndex index;
    protected Templates templates;

    public TaskTypeIndexWriter(HashIndexManager<Bytes2LongHashIndex> indexManager, Templates templates)
    {
        this.indexManager = indexManager;
        this.index = indexManager.getIndex();
        this.templates = templates;
    }

    @Override
    public void indexLogEntry(long position, LogEntryHeaderReader reader)
    {
        if (reader.templateId() == TaskInstanceDecoder.TEMPLATE_ID)
        {
            final TaskInstanceReader taskInstanceReader = templates.getReader(Templates.TASK_INSTANCE);
            reader.readInto(taskInstanceReader);

            final TaskInstanceState state = taskInstanceReader.state();

            if (state == TaskInstanceState.LOCKED)
            {
                final DirectBuffer taskType = taskInstanceReader.getTaskType();

                // TODO: this is next line is completely broken and only works if the previous version has the exact same length as this entry
                // SEE: https://github.com/camunda-tngp/broker/issues/4
                final long newPosition = taskInstanceReader.prevVersionPosition() + DataFrameDescriptor.alignedLength(taskInstanceReader.length());

                index.put(taskType, 0, taskType.capacity(), newPosition);
            }
        }
    }

    @Override
    public HashIndexManager<?> getIndexManager()
    {

        return indexManager;
    }

}
