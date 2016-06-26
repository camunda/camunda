package org.camunda.tngp.client.impl.cmd;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import org.camunda.tngp.client.cmd.LockedTasksBatch;
import org.camunda.tngp.client.cmd.PollAndLockAsyncTasksCmd;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.taskqueue.PollAndLockRequestWriter;
import org.camunda.tngp.protocol.taskqueue.CreateTaskInstanceEncoder;

public class PollAndLockTasksCmdImpl extends AbstractCmdImpl<LockedTasksBatch>
    implements PollAndLockAsyncTasksCmd
{
    static final Charset CHARSET = Charset.forName(CreateTaskInstanceEncoder.taskTypeCharacterEncoding());

    protected final PollAndLockRequestWriter requestWriter = new PollAndLockRequestWriter();

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
        requestWriter.getTaskType().wrap(taskTypeBytes);
        return this;
    }

    @Override
    public PollAndLockAsyncTasksCmd lockTime(long lockTimeMs)
    {
        requestWriter.lockTimeMs(lockTimeMs);
        return this;
    }

    @Override
    public PollAndLockAsyncTasksCmd lockTime(long lockTime, TimeUnit timeUnit)
    {
        return lockTime(timeUnit.toMillis(lockTime));
    }

    @Override
    public PollAndLockAsyncTasksCmd maxTasks(int maxTasks)
    {
        requestWriter.maxTasks(maxTasks);
        return this;
    }

    @Override
    public ClientRequestWriter getRequestWriter()
    {
        return requestWriter;
    }
}
