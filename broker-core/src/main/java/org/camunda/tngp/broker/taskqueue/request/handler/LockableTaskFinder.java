package org.camunda.tngp.broker.taskqueue.request.handler;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.log.LogEntryProcessor;
import org.camunda.tngp.log.BufferedLogReader;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.protocol.log.TaskInstanceState;
import org.camunda.tngp.protocol.taskqueue.TaskInstanceReader;

public class LockableTaskFinder implements LogEntryHandler<TaskInstanceReader>
{
    protected LogReader logReader;
    protected LogEntryProcessor<TaskInstanceReader> logEntryProcessor;

    protected long taskTypeHashToPoll;
    protected DirectBuffer taskTypeToPoll;

    protected TaskInstanceReader lockableTask;
    protected long lockableTaskPosition;

    public LockableTaskFinder()
    {
        this(new BufferedLogReader());
    }

    public LockableTaskFinder(LogReader logReader)
    {
        this.logReader = logReader;
        this.logEntryProcessor = new LogEntryProcessor<>(logReader, new TaskInstanceReader(), this);
    }

    void init(
            Log log,
            long position,
            int taskTypeHashToPoll,
            DirectBuffer taskTypeToPoll)
    {
        this.logReader.wrap(log, position);
        this.taskTypeHashToPoll = Integer.toUnsignedLong(taskTypeHashToPoll);
        this.taskTypeToPoll = taskTypeToPoll;
        this.lockableTask = null;
        this.lockableTaskPosition = -1;
    }

    public boolean findNextLockableTask()
    {
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

    private static boolean taskTypeEqual(
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
    public int handle(long position, TaskInstanceReader reader)
    {
        if (reader.taskTypeHash() == taskTypeHashToPoll && reader.state() == TaskInstanceState.NEW)
        {
            if (taskTypeEqual(reader.getTaskType(), taskTypeToPoll))
            {
                lockableTask = reader;
                lockableTaskPosition = position;
            }
        }

        return LogEntryHandler.CONSUME_ENTRY_RESULT;
    }
}
