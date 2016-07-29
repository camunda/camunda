package org.camunda.tngp.broker.wf.repository.idx;

import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.log.LogEntryProcessor;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.wf.repository.WfRepositoryContext;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionReader;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.log.LogReaderImpl;

import uk.co.real_logic.agrona.DirectBuffer;

public class WfDefinitionIndexWriter implements LogEntryHandler<WfDefinitionReader>
{
    protected final HashIndexManager<Bytes2LongHashIndex> wfDefinitionKeyIndexManager;
    protected final HashIndexManager<Long2LongHashIndex> wfDefinitionIdIndexManager;

    protected final Long2LongHashIndex wfDefinitionIdIndex;
    protected final Bytes2LongHashIndex wfDefinitionKeyIndex;

    protected LogReader logReader;

    protected LogEntryProcessor<WfDefinitionReader> logEntryProcessor;

    public WfDefinitionIndexWriter(WfRepositoryContext context)
    {
        wfDefinitionKeyIndexManager = context.getWfDefinitionKeyIndex();
        wfDefinitionIdIndexManager = context.getWfDefinitionIdIndex();

        wfDefinitionIdIndex = wfDefinitionIdIndexManager.getIndex();
        wfDefinitionKeyIndex = wfDefinitionKeyIndexManager.getIndex();

        logReader = new LogReaderImpl(context.getWfDefinitionLog(), WfDefinitionReader.MAX_LENGTH);

        final long lastCheckpointPosition = Math.min(wfDefinitionKeyIndexManager.getLastCheckpointPosition(), wfDefinitionIdIndexManager.getLastCheckpointPosition());
        if (lastCheckpointPosition != -1)
        {
            logReader.setPosition(lastCheckpointPosition);
        }

        logEntryProcessor = new LogEntryProcessor<>(logReader, new WfDefinitionReader(), this);
    }

    public int update()
    {
        return logEntryProcessor.doWork(Integer.MAX_VALUE);
    }

    public void writeCheckpoints()
    {
        final long position = logReader.position();
        wfDefinitionKeyIndexManager.writeCheckPoint(position);
        wfDefinitionIdIndexManager.writeCheckPoint(position);
    }

    @Override
    public void handle(long position, WfDefinitionReader reader)
    {
        final long id = reader.id();

        wfDefinitionIdIndex.put(id, position);

        final DirectBuffer typeKey = reader.getTypeKey();
        final int taskTypeLength = typeKey.capacity();

        wfDefinitionKeyIndex.put(typeKey, 0, taskTypeLength, id);

    }

}
