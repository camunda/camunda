package org.camunda.tngp.protocol.taskqueue;

import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchDecoder.TasksDecoder;
import org.camunda.tngp.util.buffer.BufferReader;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

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
    protected UnsafeBuffer bodyTasksPayloadBuffer = new UnsafeBuffer(0, 0);
    protected UnsafeBuffer inputBuffer = new UnsafeBuffer(0, 0);

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        inputBuffer.wrap(buffer, offset, length);

        headerDecoder.wrap(buffer, offset);
        bodyDecoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        // sets the body decoder's limit to after the header of the tasks group
        bodyTasksDecoder = bodyDecoder.tasks();
    }

    public int consumerId()
    {
        return bodyDecoder.consumerId();
    }

    public long lockTime()
    {
        return bodyDecoder.lockTime();
    }

    public int numTasks()
    {
        return bodyTasksDecoder.count();
    }

    public LockTaskBatchResponseReader nextTask()
    {
        // sets the body decoders limit to after the field block of the first task
        bodyTasksDecoder.next();

        final int offset = bodyDecoder.limit();

        bodyTasksPayloadBuffer.wrap(
                inputBuffer,
                offset + TasksDecoder.payloadHeaderLength(),
                bodyTasksDecoder.payloadLength());

        bodyDecoder.limit(offset + TasksDecoder.payloadHeaderLength() + bodyTasksDecoder.payloadLength());

        return this;
    }

    public long currentTaskId()
    {
        return bodyTasksDecoder.taskId();
    }

    public long currentTaskWfInstanceId()
    {
        return bodyTasksDecoder.wfInstanceId();
    }

    public DirectBuffer currentTaskPayload()
    {
        return bodyTasksPayloadBuffer;
    }

}
