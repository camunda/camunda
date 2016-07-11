package org.camunda.tngp.broker.wf.repository.idx;

import java.util.Arrays;

import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.log.LogEntryProcessor;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.wf.repository.WfRepositoryContext;
import org.camunda.tngp.broker.wf.repository.log.WfTypeReader;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.LogReader;

import uk.co.real_logic.agrona.DirectBuffer;

public class WfTypeIndexWriter implements LogEntryHandler<WfTypeReader>
{
    protected static final byte[] WF_TYPE_BUFFER = new byte[256];

    protected final HashIndexManager<Bytes2LongHashIndex> wfTypeKeyIndexManager;
    protected final HashIndexManager<Long2LongHashIndex> wfTypeIdIndexManager;

    protected final Long2LongHashIndex wfTypeIdIndex;
    protected final Bytes2LongHashIndex wfTypeKeyIndex;

    protected LogReader logReader;

    protected LogEntryProcessor<WfTypeReader> logEntryProcessor;

    public WfTypeIndexWriter(WfRepositoryContext context)
    {
        wfTypeKeyIndexManager = context.getWfTypeKeyIndex();
        wfTypeIdIndexManager = context.getWfTypeIdIndex();

        wfTypeIdIndex = wfTypeIdIndexManager.getIndex();
        wfTypeKeyIndex = wfTypeKeyIndexManager.getIndex();

        logReader = new LogReader(context.getWfTypeLog(), WfTypeReader.MAX_LENGTH);

        final long lastCheckpointPosition = Math.min(wfTypeKeyIndexManager.getLastCheckpointPosition(), wfTypeIdIndexManager.getLastCheckpointPosition());
        if (lastCheckpointPosition != -1)
        {
            logReader.setPosition(lastCheckpointPosition);
        }

        logEntryProcessor = new LogEntryProcessor<>(logReader, new WfTypeReader(), this);
    }

    public int update()
    {
        return logEntryProcessor.doWork(Integer.MAX_VALUE);
    }

    public void writeCheckpoints()
    {
        final long position = logReader.position();
        wfTypeKeyIndexManager.writeCheckPoint(position);
        wfTypeIdIndexManager.writeCheckPoint(position);
    }

    @Override
    public void handle(long position, WfTypeReader reader)
    {
        final long id = reader.id();

        wfTypeIdIndex.put(id, position);

        final DirectBuffer typeKey = reader.getTypeKey();
        final int taskTypeLength = typeKey.capacity();

        typeKey.getBytes(0, WF_TYPE_BUFFER, 0, taskTypeLength);

        if (taskTypeLength < WF_TYPE_BUFFER.length)
        {
            Arrays.fill(WF_TYPE_BUFFER, taskTypeLength, WF_TYPE_BUFFER.length, (byte) 0);
        }

        wfTypeKeyIndex.put(WF_TYPE_BUFFER, id);

    }

}
