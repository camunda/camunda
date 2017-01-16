package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.util.buffer.RequestWriter;

public class CloseTaskSubscriptionCmdImpl extends AbstractCmdImpl<Long>
{

    public CloseTaskSubscriptionCmdImpl(ClientCmdExecutor cmdExecutor)
    {
        super(cmdExecutor, new TaskSubscriptionResponseHandler());
    }

    @Override
    public RequestWriter getRequestWriter()
    {
        return null;
    }

    public CloseTaskSubscriptionCmdImpl consumerId(short consumerId)
    {
        return this;
    }


    public CloseTaskSubscriptionCmdImpl subscriptionId(long subscriptionId)
    {
        return this;
    }

}
