package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.util.buffer.RequestWriter;

public class ProvideSubscriptionCreditsCmdImpl extends AbstractSingleMessageCmd
{

    public ProvideSubscriptionCreditsCmdImpl(ClientCmdExecutor cmdExecutor)
    {
        super(cmdExecutor);
    }

    @Override
    public RequestWriter getRequestWriter()
    {
        return null;
    }

    public ProvideSubscriptionCreditsCmdImpl consumerId(short consumerId)
    {
        return this;
    }


    public ProvideSubscriptionCreditsCmdImpl subscriptionId(long subscriptionId)
    {
        return this;
    }

    public ProvideSubscriptionCreditsCmdImpl credits(int credits)
    {
        return this;
    }


}
