package org.camunda.tngp.broker.wf.repository;

import java.util.Arrays;

import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.wf.repository.log.WfTypeReader;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.LogReader;

import uk.co.real_logic.agrona.DirectBuffer;

public class WfTypeIndexWriter
{
    protected final static byte[] wfTypeBuffer = new byte[256];

    protected final LogReader logReader;
    protected final HashIndexManager<Bytes2LongHashIndex> wfTypeKeyIndexManager;
    protected final HashIndexManager<Long2LongHashIndex> wfTypeIdIndexManager;

    protected final WfTypeReader reader = new WfTypeReader();

    public WfTypeIndexWriter(WfRepositoryContext context)
    {
        wfTypeKeyIndexManager = context.getWfTypeKeyIndex();
        wfTypeIdIndexManager = context.getWfTypeIdIndex();
        logReader = new LogReader(context.getWfTypeLog(), WfTypeReader.MAX_LENGTH);

        final long lastCheckpointPosition = Math.min(wfTypeKeyIndexManager.getLastCheckpointPosition(), wfTypeIdIndexManager.getLastCheckpointPosition());
        if(lastCheckpointPosition != -1)
        {
            logReader.setPosition(lastCheckpointPosition);
        }
    }

    public int update(int maxFragments)
    {
        int fragmentsIndexed = 0;

        do
        {
            final long position = logReader.getPosition();

            if(logReader.read(reader))
            {
                updateIndex(position);
                ++fragmentsIndexed;
            }
            else
            {
                break;
            }
        }
        while(fragmentsIndexed < maxFragments);

        return fragmentsIndexed;
    }

    public void writeCheckpoints()
    {
        final long position = logReader.getPosition();
        wfTypeKeyIndexManager.writeCheckPoint(position);
        wfTypeIdIndexManager.writeCheckPoint(position);
    }

    protected void updateIndex(final long position)
    {
        final Long2LongHashIndex wfTypeIdIndex = wfTypeIdIndexManager.getIndex();
        final Bytes2LongHashIndex wfTypeKeyIndex = wfTypeKeyIndexManager.getIndex();

        final long id = reader.id();

        wfTypeIdIndex.put(id, position);

        final DirectBuffer typeKey = reader.getTypeKey();
        final int taskTypeLength = typeKey.capacity();

        typeKey.getBytes(0, wfTypeBuffer);

        if (taskTypeLength < wfTypeBuffer.length)
        {
            Arrays.fill(wfTypeBuffer, taskTypeLength, wfTypeBuffer.length, (byte) 0);
        }

        wfTypeKeyIndex.put(wfTypeBuffer, id);
    }

}
