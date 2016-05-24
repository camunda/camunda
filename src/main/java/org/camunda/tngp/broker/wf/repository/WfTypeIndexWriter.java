package org.camunda.tngp.broker.wf.repository;

import java.nio.channels.FileChannel;
import java.util.Arrays;

import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.LogFragmentHandler;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.taskqueue.data.WfTypeDecoder;

public class WfTypeIndexWriter implements LogFragmentHandler
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
        logReader = new LogReader(context.getWfTypeLog(), this);

        final long lastCheckpointPosition = Math.min(wfTypeKeyIndexManager.getLastCheckpointPosition(), wfTypeIdIndexManager.getLastCheckpointPosition());
        if(lastCheckpointPosition != -1)
        {
            logReader.setPosition(lastCheckpointPosition);
        }
    }

    public int update(int maxFragments)
    {
        return logReader.read(maxFragments);
    }

    public void writeCheckpoints()
    {
        final long position = logReader.getPosition();
        wfTypeKeyIndexManager.writeCheckPoint(position);
        wfTypeIdIndexManager.writeCheckPoint(position);
    }

    @Override
    public void onFragment(long position, FileChannel fileChannel, int offset, int length)
    {
        final Long2LongHashIndex wfTypeIdIndex = wfTypeIdIndexManager.getIndex();
        final Bytes2LongHashIndex wfTypeKeyIndex = wfTypeKeyIndexManager.getIndex();

        reader.onFragment(position, fileChannel, offset, length);

        final WfTypeDecoder decoder = reader.getDecoder();
        final long id = decoder.id();

        wfTypeIdIndex.put(id, position);

        final int taskTypeLength = reader.getWfTypeKeyLength();
        reader.getBlockBuffer().getBytes(reader.getWfTypeKeyOffset(), wfTypeBuffer, 0, taskTypeLength);
        if (taskTypeLength < wfTypeBuffer.length)
        {
            Arrays.fill(wfTypeBuffer, taskTypeLength, wfTypeBuffer.length, (byte) 0);
        }

        wfTypeKeyIndex.put(wfTypeBuffer, id);
    }

}
