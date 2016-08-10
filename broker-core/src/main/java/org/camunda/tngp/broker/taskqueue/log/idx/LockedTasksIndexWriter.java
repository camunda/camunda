package org.camunda.tngp.broker.taskqueue.log.idx;

import org.camunda.tngp.broker.log.LogEntryHeaderReader;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.broker.log.idx.IndexWriter;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.taskqueue.TaskInstanceReader;
import org.camunda.tngp.broker.taskqueue.log.TaskInstanceRequestReader;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.taskqueue.data.TaskInstanceDecoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceRequestType;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;

public class LockedTasksIndexWriter implements IndexWriter
{

    protected HashIndexManager<Long2LongHashIndex> indexManager;
    protected Long2LongHashIndex index;
    protected Templates templates;

    public LockedTasksIndexWriter(HashIndexManager<Long2LongHashIndex> indexManager, Templates templates)
    {
        this.indexManager = indexManager;
        this.index = indexManager.getIndex();
        this.templates = templates;
    }

    @Override
    public void indexLogEntry(long position, LogEntryHeaderReader reader)
    {

        if (reader.templateId() == Templates.TASK_INSTANCE_REQUEST.id())
        {
            final TaskInstanceRequestReader taskInstanceRequestReader = templates.getReader(Templates.TASK_INSTANCE_REQUEST);
            reader.readInto(taskInstanceRequestReader);

            // TODO: should this for all complete requests remove this or only for the successful?
            if (taskInstanceRequestReader.type() == TaskInstanceRequestType.COMPLETE)
            {
                index.remove(taskInstanceRequestReader.key(), -1L);
            }

        }

        if (reader.templateId() == TaskInstanceDecoder.TEMPLATE_ID)
        {
            final TaskInstanceReader taskInstanceReader = templates.getReader(Templates.TASK_INSTANCE);
            reader.readInto(taskInstanceReader);

            final TaskInstanceState state = taskInstanceReader.state();
            final long id = taskInstanceReader.id();

            if (state == TaskInstanceState.LOCKED)
            {
                index.put(id, position);
            }
        }
    }

    @Override
    public HashIndexManager<?> getIndexManager()
    {

        return indexManager;
    }

}
