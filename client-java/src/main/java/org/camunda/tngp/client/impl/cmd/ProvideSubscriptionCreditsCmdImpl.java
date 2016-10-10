package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.util.buffer.RequestWriter;

public class ProvideSubscriptionCreditsCmdImpl extends AbstractSingleMessageCmd
{

    protected ProvideSubscriptionCreditsWriter requestWriter = new ProvideSubscriptionCreditsWriter();

    public ProvideSubscriptionCreditsCmdImpl(ClientCmdExecutor cmdExecutor)
    {
        super(cmdExecutor);
    }

    @Override
    public RequestWriter getRequestWriter()
    {
        return requestWriter;
    }

    public ProvideSubscriptionCreditsCmdImpl consumerId(short consumerId)
    {
        requestWriter.consumerId(consumerId);
        return this;
    }


    public ProvideSubscriptionCreditsCmdImpl subscriptionId(long subscriptionId)
    {
        requestWriter.subscriptionId(subscriptionId);
        return this;
    }

    public ProvideSubscriptionCreditsCmdImpl credits(int credits)
    {
        requestWriter.credits(credits);
        return this;
    }


}
