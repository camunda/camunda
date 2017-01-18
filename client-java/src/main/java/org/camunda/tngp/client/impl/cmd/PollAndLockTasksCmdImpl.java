package org.camunda.tngp.client.impl.cmd;

import java.time.Duration;

import org.camunda.tngp.client.cmd.LockedTasksBatch;
import org.camunda.tngp.client.cmd.PollAndLockAsyncTasksCmd;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.util.buffer.RequestWriter;

public class PollAndLockTasksCmdImpl extends AbstractCmdImpl<LockedTasksBatch>
    implements PollAndLockAsyncTasksCmd
{

    public PollAndLockTasksCmdImpl(ClientCmdExecutor cmdExecutor)
    {
        super(cmdExecutor);
    }

    @Override
    public PollAndLockTasksCmdImpl taskQueueId(int taskQueueId)
    {
        return this;
    }

    @Override
    public PollAndLockTasksCmdImpl taskType(String taskType)
    {
        return this;
    }

    @Override
    public PollAndLockAsyncTasksCmd lockTime(long lockTimeMs)
    {
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
        return this;
    }

    @Override
    public RequestWriter getRequestWriter()
    {
        return null;
    }

    @Override
    public ClientResponseHandler<LockedTasksBatch> getResponseHandler()
    {
        return new PollAndLockResponseHandler();
    }

}
