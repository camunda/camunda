package org.camunda.tngp.broker.taskqueue.request.handler;

import org.agrona.DirectBuffer;
import org.agrona.collections.LongHashSet;
import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.log.LogEntryHeaderReader;
import org.camunda.tngp.broker.log.LogEntryProcessor;
import org.camunda.tngp.broker.log.Templates;
import org.camunda.tngp.log.BufferedLogReader;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.protocol.log.TaskInstanceState;
import org.camunda.tngp.protocol.taskqueue.TaskInstanceReader;

public class LockableTaskFinder implements LogEntryHandler<LogEntryHeaderReader>
{
    protected LogReader logReader;
    protected LogEntryProcessor<LogEntryHeaderReader> logEntryProcessor;

    protected LongHashSet taskTypeHashes;

    protected TaskInstanceReader taskReader = new TaskInstanceReader();
    protected TaskInstanceReader lockableTask;
    protected long lockableTaskPosition;

    public LockableTaskFinder()
    {
        this(new BufferedLogReader());
    }

    public LockableTaskFinder(LogReader logReader)
    {
        this.logReader = logReader;
        this.logEntryProcessor = new LogEntryProcessor<>(logReader, new LogEntryHeaderReader(), this);
    }

    public void init(
            long position,
            LongHashSet taskTypeHashes)
    {
        this.logReader.seek(position);
        this.taskTypeHashes = taskTypeHashes;
        this.lockableTask = null;
        this.lockableTaskPosition = -1;
    }

    public boolean findNextLockableTask()
    {
        lockableTask = null;
        int entriesProcessed = 0;
        do
        {
            entriesProcessed = logEntryProcessor.doWorkSingle();
        }
        while (entriesProcessed > 0 && lockableTask == null);

        return lockableTask != null;
    }

    public TaskInstanceReader getLockableTask()
    {
        return lockableTask;
    }

    public long getLockableTaskPosition()
    {
        return lockableTaskPosition;
    }

    public static boolean taskTypeEqual(
            DirectBuffer actualTaskType,
            DirectBuffer taskTypeToPoll)
    {

        if (taskTypeToPoll.capacity() == actualTaskType.capacity())
        {
            boolean taskTypeEqual = true;

            for (int i = 0; i < taskTypeToPoll.capacity() && taskTypeEqual; i++)
            {
                taskTypeEqual &= taskTypeToPoll.getByte(i) == actualTaskType.getByte(i);
            }

            return taskTypeEqual;
        }
        else
        {
            return false;
        }
    }

    @Override
    public int handle(long position, LogEntryHeaderReader reader)
    {
        if (!reader.template().equals(Templates.TASK_INSTANCE))
        {
            return LogEntryHandler.CONSUME_ENTRY_RESULT;
        }

        reader.readInto(taskReader);

        if (taskReader.state() == TaskInstanceState.NEW && taskTypeHashes.contains((int) taskReader.taskTypeHash()))
        {
            lockableTask = taskReader;
            lockableTaskPosition = position;
        }

        return LogEntryHandler.CONSUME_ENTRY_RESULT;
    }
}
