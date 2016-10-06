package org.camunda.tngp.protocol.taskqueue;

import org.agrona.DirectBuffer;
import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchDecoder.TasksDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

/**
 * <p>Careful: This reader is stateful when reading tasks. Users should use the {@link #nextTask()}
 * and the <code>currentTask*</code> methods appropriately.
 *
 * <p>TODO: could implement Iterable (similar to the generated TasksDecoder)
 *
 * @author Lindhauer
 */
public class LockTaskBatchResponseReader implements BufferReader
{
    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected LockedTaskBatchDecoder bodyDecoder = new LockedTaskBatchDecoder();

    protected TasksDecoder bodyTasksDecoder;
    protected LockedTaskDecoder taskDecoder;

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);
        bodyDecoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        // sets the body decoder's limit to after the header of the tasks group
        bodyTasksDecoder = bodyDecoder.tasks();
    }

    public int consumerId()
    {
        return bodyDecoder.consumerId();
    }

    public int numTasks()
    {
        return bodyTasksDecoder.count();
    }

    public LockTaskBatchResponseReader nextTask()
    {
        bodyTasksDecoder.next();
        taskDecoder = bodyTasksDecoder.task();

        return this;
    }

    public long currentTaskId()
    {
        return taskDecoder.id();
    }

    public long currentTaskWfInstanceId()
    {
        return taskDecoder.wfInstanceId();
    }

    public long currentTaskLockTime()
    {
        return taskDecoder.lockTime();
    }

}
