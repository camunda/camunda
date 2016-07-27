package org.camunda.tngp.broker.taskqueue;

import java.util.function.LongConsumer;

import org.camunda.tngp.broker.idx.LogEntryTracker;
import org.camunda.tngp.broker.taskqueue.log.TaskInstanceReader;
import org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.camunda.tngp.util.BiLongConsumer;

import uk.co.real_logic.agrona.DirectBuffer;

public class TaskQueueIndexLogTracker implements LogEntryTracker<TaskInstanceReader>
{
    protected final Long2LongHashIndex lockedTasksIndex;
    protected final Bytes2LongHashIndex taskTypeIndex;

    public TaskQueueIndexLogTracker(Long2LongHashIndex lockedTasksIndex, Bytes2LongHashIndex taskTypeIndex)
    {
        this.lockedTasksIndex = lockedTasksIndex;
        this.taskTypeIndex = taskTypeIndex;
    }

    @Override
    public void onLogEntryStaged(TaskInstanceReader logEntry)
    {
        onLogEntry(logEntry,
            (id) -> lockedTasksIndex.markDirty(id),
            (id) -> lockedTasksIndex.markDirty(id),
            (type, newPosition) -> taskTypeIndex.markDirty(type, 0, type.capacity()));
    }

    @Override
    public void onLogEntryFailed(TaskInstanceReader logEntry)
    {
        onLogEntry(logEntry,
            (id) -> lockedTasksIndex.resolveDirty(id),
            (id) -> lockedTasksIndex.resolveDirty(id),
            (type, newPosition) -> taskTypeIndex.resolveDirty(type, 0, type.capacity()));
    }

    @Override
    public void onLogEntryCommit(TaskInstanceReader logEntry, final long position)
    {
        onLogEntry(logEntry,
            (id) ->
            {
                lockedTasksIndex.put(id, position);
                lockedTasksIndex.resolveDirty(id);
            },
            (id) ->
            {
                lockedTasksIndex.put(id, -1);
                lockedTasksIndex.resolveDirty(id);
            },
            (type, newPosition) ->
            {
                taskTypeIndex.put(type, 0, type.capacity(), newPosition);
                taskTypeIndex.resolveDirty(type, 0, type.capacity());
            });
    }


    protected void onLogEntry(
            TaskInstanceReader logEntry,
            LongConsumer onLockedTaskInstance,
            LongConsumer onCompleteTaskInstance,
            BiLongConsumer<DirectBuffer> onLastLockedTaskInstance)
    {
        final long id = logEntry.id();
        final TaskInstanceState state = logEntry.state();

        if (state == TaskInstanceState.LOCKED)
        {
            onLockedTaskInstance.accept(id);

            final DirectBuffer taskType = logEntry.getTaskType();

            // TODO: this is next line is completely broken and only works if the previous version has the exact same length as this entry
            // SEE: https://github.com/camunda-tngp/broker/issues/4
            final long newPosition = logEntry.prevVersionPosition() + DataFrameDescriptor.alignedLength(logEntry.length());

            onLastLockedTaskInstance.accept(taskType, newPosition);
        }
        else if (state == TaskInstanceState.COMPLETED)
        {
            onCompleteTaskInstance.accept(id);
        }
    }

}
