package org.camunda.tngp.broker.wf.repository.idx;

import java.util.Arrays;

import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.wf.repository.WfRepositoryContext;
import org.camunda.tngp.broker.wf.repository.log.WfTypeReader;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.LogReader;

import uk.co.real_logic.agrona.DirectBuffer;

public class WfTypeIndexWriter
{
    protected final static byte[] WF_TYPE_BUFFER = new byte[256];

    protected final HashIndexManager<Bytes2LongHashIndex> wfTypeKeyIndexManager;
    protected final HashIndexManager<Long2LongHashIndex> wfTypeIdIndexManager;

    protected final Long2LongHashIndex wfTypeIdIndex;
    protected final Bytes2LongHashIndex wfTypeKeyIndex;

    protected LogReader logReader;
    protected WfTypeReader reader = new WfTypeReader();

    public WfTypeIndexWriter(WfRepositoryContext context)
    {
        wfTypeKeyIndexManager = context.getWfTypeKeyIndex();
        wfTypeIdIndexManager = context.getWfTypeIdIndex();

        wfTypeIdIndex = wfTypeIdIndexManager.getIndex();
        wfTypeKeyIndex = wfTypeKeyIndexManager.getIndex();

        logReader = new LogReader(context.getWfTypeLog(), WfTypeReader.MAX_LENGTH);

        final long lastCheckpointPosition = Math.min(wfTypeKeyIndexManager.getLastCheckpointPosition(), wfTypeIdIndexManager.getLastCheckpointPosition());
        if(lastCheckpointPosition != -1)
        {
            logReader.setPosition(lastCheckpointPosition);
        }
    }

    public int update()
    {
        int fragmentsIndexed = 0;

        boolean hasEntry = false;

        do
        {
            final long position = logReader.position();

            hasEntry = logReader.read(reader);

            if(hasEntry)
            {
                updateIndex(position);
                ++fragmentsIndexed;
            }
        }
        while(hasEntry);

        return fragmentsIndexed;
    }

    public void writeCheckpoints()
    {
        final long position = logReader.position();
        wfTypeKeyIndexManager.writeCheckPoint(position);
        wfTypeIdIndexManager.writeCheckPoint(position);
    }

    protected void updateIndex(final long position)
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
