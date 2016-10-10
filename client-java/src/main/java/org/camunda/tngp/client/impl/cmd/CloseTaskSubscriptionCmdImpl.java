package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.impl.ClientCmdExecutor;

public class CloseTaskSubscriptionCmdImpl extends AbstractCmdImpl<Long>
{

    protected CloseTaskSubscriptionRequestWriter requestWriter = new CloseTaskSubscriptionRequestWriter();

    public CloseTaskSubscriptionCmdImpl(ClientCmdExecutor cmdExecutor)
    {
        super(cmdExecutor, new TaskSubscriptionResponseHandler());
    }

    @Override
    public CloseTaskSubscriptionRequestWriter getRequestWriter()
    {
        return requestWriter;
    }

    public CloseTaskSubscriptionCmdImpl consumerId(short consumerId)
    {
        requestWriter.consumerId(consumerId);
        return this;
    }


    public CloseTaskSubscriptionCmdImpl subscriptionId(long subscriptionId)
    {
        requestWriter.subscriptionId(subscriptionId);
        return this;
    }

}
