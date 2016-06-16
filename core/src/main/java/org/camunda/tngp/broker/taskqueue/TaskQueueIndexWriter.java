package org.camunda.tngp.broker.taskqueue;

import java.util.Arrays;

import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.taskqueue.log.TaskInstanceReader;
import org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;

import uk.co.real_logic.agrona.DirectBuffer;

public class TaskQueueIndexWriter
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
        logReader = new LogReader(taskQueueContext.getLog(), TaskInstanceReader.MAX_LENGTH);

        final long lastCheckpointPosition = Math.min(lockedTasksIndexManager.getLastCheckpointPosition(), taskTypeIndexManager.getLastCheckpointPosition());
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
            final long position = logReader.position();

            if(logReader.read(taskInstanceReader))
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
        lockedTasksIndexManager.writeCheckPoint(logReader.position());
        taskTypeIndexManager.writeCheckPoint(logReader.position());
    }


    protected void updateIndex(long position)
    {
        final long id = taskInstanceReader.id();
        final TaskInstanceState state = taskInstanceReader.state();

        if(state == TaskInstanceState.LOCKED)
        {
            lockedTasksIndexManager.getIndex().put(id, position);

            final DirectBuffer taskType = taskInstanceReader.getTaskType();
            final int taskTypeLength = taskType.capacity();

            taskType.getBytes(0, taskTypeBuffer, 0, taskTypeLength);

            if(taskTypeLength < taskTypeBuffer.length)
            {
                Arrays.fill(taskTypeBuffer, taskTypeLength, taskTypeBuffer.length, (byte)0);
            }

            final Bytes2LongHashIndex taskTypePositionIndex = taskTypeIndexManager.getIndex();
            long currentPosition = taskTypePositionIndex.get(taskTypeBuffer, -1);

            // TODO: this is next line is completely broken and only works if the previous version has the exact same length as this entry
            // SEE: https://github.com/camunda-tngp/broker/issues/4
            long newPosition = taskInstanceReader.prevVersionPosition() + DataFrameDescriptor.alignedLength(taskInstanceReader.length());

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
