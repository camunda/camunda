package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.impl.ClientCmdExecutor;

public class CreateTaskSubscriptionCmdImpl extends AbstractCmdImpl<Long>
{

    protected CreateTaskSubscriptionRequestWriter requestWriter = new CreateTaskSubscriptionRequestWriter();

    public CreateTaskSubscriptionCmdImpl(ClientCmdExecutor cmdExecutor)
    {
        super(cmdExecutor, new TaskSubscriptionResponseHandler());
    }

    @Override
    public CreateTaskSubscriptionRequestWriter getRequestWriter()
    {
        return requestWriter;
    }

    public CreateTaskSubscriptionCmdImpl consumerId(short consumerId)
    {
        requestWriter.consumerId(consumerId);
        return this;
    }

    public CreateTaskSubscriptionCmdImpl initialCredits(int initialCredits)
    {
        requestWriter.initialCredits(initialCredits);
        return this;
    }

    public CreateTaskSubscriptionCmdImpl lockDuration(long lockDuration)
    {
        requestWriter.lockDuration(lockDuration);
        return this;
    }

    public CreateTaskSubscriptionCmdImpl taskType(String taskType)
    {
        requestWriter.taskType(taskType);
        return this;
    }

}
