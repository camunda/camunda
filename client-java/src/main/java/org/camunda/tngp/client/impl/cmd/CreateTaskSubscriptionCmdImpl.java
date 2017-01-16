package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.util.buffer.RequestWriter;

public class CreateTaskSubscriptionCmdImpl extends AbstractCmdImpl<Long>
{
    public CreateTaskSubscriptionCmdImpl(ClientCmdExecutor cmdExecutor)
    {
        super(cmdExecutor, new TaskSubscriptionResponseHandler());
    }

    @Override
    public RequestWriter getRequestWriter()
    {
        return null;
    }

    public CreateTaskSubscriptionCmdImpl consumerId(short consumerId)
    {
        return this;
    }

    public CreateTaskSubscriptionCmdImpl initialCredits(int initialCredits)
    {
        return this;
    }

    public CreateTaskSubscriptionCmdImpl lockDuration(long lockDuration)
    {
        return this;
    }

    public CreateTaskSubscriptionCmdImpl taskType(String taskType)
    {
        return this;
    }

}
