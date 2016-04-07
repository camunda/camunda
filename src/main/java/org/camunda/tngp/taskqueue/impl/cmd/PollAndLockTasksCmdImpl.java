package org.camunda.tngp.taskqueue.impl.cmd;

import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import org.camunda.tngp.taskqueue.client.cmd.LockedTasksBatch;
import org.camunda.tngp.taskqueue.client.cmd.PollAndLockTasksCmd;
import org.camunda.tngp.taskqueue.impl.TngpClientImpl;
import org.camunda.tngp.taskqueue.protocol.CreateTaskInstanceEncoder;
import org.camunda.tngp.taskqueue.protocol.LockedTaskBatchDecoder;
import org.camunda.tngp.taskqueue.protocol.LockedTaskBatchDecoder.TasksDecoder;
import org.camunda.tngp.taskqueue.protocol.PollAndLockTasksEncoder;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

public class PollAndLockTasksCmdImpl extends AbstractCmdImpl<LockedTasksBatch> implements PollAndLockTasksCmd
{
    static final Charset CHARSET = Charset.forName(CreateTaskInstanceEncoder.taskTypeCharacterEncoding());

    protected final PollAndLockTasksEncoder requestEncoder = new PollAndLockTasksEncoder();
    protected final LockedTaskBatchDecoder responseDecoder = new LockedTaskBatchDecoder();

    protected byte[] taskType = null;
    protected int taskTypeLength = 0;
    protected long lockTimeMs;
    protected int maxTasks;

    public PollAndLockTasksCmdImpl(TngpClientImpl client)
    {
        super(client, LockedTaskBatchDecoder.SCHEMA_ID, LockedTaskBatchDecoder.TEMPLATE_ID);
    }

    @Override
    protected int getRequestLength()
    {
        return headerEncoder.encodedLength()
                + requestEncoder.sbeBlockLength()
                + PollAndLockTasksEncoder.taskTypeHeaderLength()
                + taskTypeLength;
    }

    @Override
    protected void writeRequest(MutableDirectBuffer buffer, int offset)
    {
        int writeOffset = offset;

        headerEncoder.wrap(buffer, writeOffset)
            .blockLength(requestEncoder.sbeBlockLength())
            .schemaId(requestEncoder.sbeSchemaId())
            .templateId(requestEncoder.sbeTemplateId())
            .version(requestEncoder.sbeSchemaVersion());

        writeOffset += headerEncoder.encodedLength();

        requestEncoder.wrap(buffer, writeOffset)
            .lockTime(lockTimeMs)
            .maxTasks(maxTasks)
            .putTaskType(taskType, 0, taskTypeLength);

    }

    @Override
    public PollAndLockTasksCmdImpl taskType(String taskType)
    {
        this.taskType = taskType.getBytes(CHARSET);
        this.taskTypeLength = this.taskType.length;
        return this;
    }

    @Override
    protected LockedTasksBatch readReponseBody(
            final DirectBuffer responseBuffer,
            final int offset,
            final int actingBlockLength,
            final int actingVersion)
    {
        final LockedTasksBatchImpl lockedTasksBatch = new LockedTasksBatchImpl(maxTasks);

        responseDecoder.wrap(responseBuffer, offset, actingBlockLength, actingVersion);

        lockedTasksBatch.setLockTime(responseDecoder.lockTime());

        final Iterator<TasksDecoder> taskIterator = responseDecoder.tasks().iterator();
        while (taskIterator.hasNext())
        {
            final TasksDecoder taskDecoder = taskIterator.next();
            final int payloadLength = taskDecoder.payloadLength();

            final LockedTaskImpl lockedTask = new LockedTaskImpl(payloadLength);

            lockedTask.setId(taskDecoder.taskId());
            taskDecoder.getPayload(lockedTask.getPayloadBuffer(), 0, payloadLength);

            lockedTasksBatch.addTask(lockedTask);
        }

        return lockedTasksBatch;
    }

    @Override
    public PollAndLockTasksCmd lockTime(long lockTimeMs)
    {
        this.lockTimeMs = lockTimeMs;
        return this;
    }

    @Override
    public PollAndLockTasksCmd lockTime(long lockTime, TimeUnit timeUnit)
    {
        return lockTime(timeUnit.toMillis(lockTime));
    }

    @Override
    public PollAndLockTasksCmd maxTasks(int maxTasks)
    {
        this.maxTasks = maxTasks;
        return this;
    }

    @Override
    public void reset()
    {
        super.reset();
        lockTimeMs = 0;
        maxTasks = 0;
        taskTypeLength = 0;
    }

}
