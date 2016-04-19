package org.camunda.tngp.broker.taskqueue;

import java.nio.channels.FileChannel;
import java.util.Arrays;

import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.taskqueue.handler.TaskInstanceReader;
import org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.LogFragmentHandler;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.taskqueue.data.TaskInstanceDecoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;

public class TaskQueueIndexWriter implements LogFragmentHandler
{
    protected final static byte[] taskTypeBuffer = new byte[256];

    protected final LogReader logReader;
    protected final TaskInstanceReader taskInstanceReader = new TaskInstanceReader();
    protected final HashIndexManager<Long2LongHashIndex> lockedTasksIndexManager;
    protected final HashIndexManager<Bytes2LongHashIndex> taskTypeIndexManager;

    public TaskQueueIndexWriter(TaskQueueContext taskQueueContext)
    {
        lockedTasksIndexManager = taskQueueContext.getLockedTaskInstanceIndex();
        taskTypeIndexManager = taskQueueContext.getTaskTypePositionIndex();
        logReader = new LogReader(taskQueueContext.getLog(), this);

        final long lastCheckpointPosition = Math.min(lockedTasksIndexManager.getLastCheckpointPosition(), taskTypeIndexManager.getLastCheckpointPosition());
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
        lockedTasksIndexManager.writeCheckPoint(logReader.getPosition());
        taskTypeIndexManager.writeCheckPoint(logReader.getPosition());
    }

    @Override
    public void onFragment(long position, FileChannel fileChannel, int offset, int length)
    {
        if(taskInstanceReader.readBlock(position, fileChannel, offset, length))
        {
            final TaskInstanceDecoder decoder = taskInstanceReader.getDecoder();

            final long id = decoder.id();
            final TaskInstanceState state = decoder.state();

            if(state == TaskInstanceState.LOCKED)
            {
                lockedTasksIndexManager.getIndex().put(id, position);

                final int taskTypeLength = taskInstanceReader.getTaskTypeLength();
                taskInstanceReader.getBlockBuffer().getBytes(taskInstanceReader.getTaskTypeOffset(), taskTypeBuffer, 0, taskTypeLength);
                if(taskTypeLength < taskTypeBuffer.length)
                {
                    Arrays.fill(taskTypeBuffer, taskTypeLength, taskTypeBuffer.length, (byte)0);
                }

                final Bytes2LongHashIndex taskTypePositionIndex = taskTypeIndexManager.getIndex();
                long currentPosition = taskTypePositionIndex.get(taskTypeBuffer, -1);
                long newPosition = decoder.prevVersionPosition() + DataFrameDescriptor.alignedLength(length); // hmmm...
                // TODO: put if larger
                if(newPosition > currentPosition)
                {
                    taskTypePositionIndex.put(taskTypeBuffer, newPosition);
                }
            }
            else if(state == TaskInstanceState.COMPLETED)
            {
                lockedTasksIndexManager.getIndex().remove(id, -1);
            }
        }
    }

}
