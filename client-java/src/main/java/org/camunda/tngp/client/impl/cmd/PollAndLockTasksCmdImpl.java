package org.camunda.tngp.client.impl.cmd;

import java.nio.charset.Charset;
import java.time.Duration;

import org.camunda.tngp.client.cmd.LockedTasksBatch;
import org.camunda.tngp.client.cmd.PollAndLockAsyncTasksCmd;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.taskqueue.PollAndLockRequestWriter;
import org.camunda.tngp.protocol.taskqueue.CreateTaskInstanceEncoder;
import org.camunda.tngp.util.buffer.RequestWriter;

public class PollAndLockTasksCmdImpl extends AbstractCmdImpl<LockedTasksBatch>
    implements PollAndLockAsyncTasksCmd
{
    static final Charset CHARSET = Charset.forName(CreateTaskInstanceEncoder.taskTypeCharacterEncoding());

    protected PollAndLockRequestWriter requestWriter = new PollAndLockRequestWriter();

    public PollAndLockTasksCmdImpl(ClientCmdExecutor cmdExecutor)
    {
        super(cmdExecutor, new PollAndLockResponseHandler());
    }

    @Override
    public PollAndLockTasksCmdImpl taskQueueId(int taskQueueId)
    {
        requestWriter.resourceId(taskQueueId);
        return this;
    }

    @Override
    public PollAndLockTasksCmdImpl taskType(String taskType)
    {
        final byte[] taskTypeBytes = taskType.getBytes(CHARSET);
        requestWriter.taskType(taskTypeBytes, 0, taskTypeBytes.length);
        return this;
    }

    @Override
    public PollAndLockAsyncTasksCmd lockTime(long lockTimeMs)
    {
        requestWriter.lockTimeMs(lockTimeMs);
        return this;
    }

    @Override
    public PollAndLockAsyncTasksCmd lockTime(Duration timeDuration)
    {
        return lockTime(timeDuration.toMillis());
    }

    @Override
    public PollAndLockAsyncTasksCmd maxTasks(int maxTasks)
    {
        requestWriter.maxTasks(maxTasks);
        return this;
    }

    @Override
    public RequestWriter getRequestWriter()
    {
        return requestWriter;
    }

    public void setRequestWriter(PollAndLockRequestWriter requestWriter)
    {
        this.requestWriter = requestWriter;
    }
}
