package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.util.buffer.RequestWriter;

public abstract class AbstractSingleMessageCmd
{
    protected final ClientCmdExecutor cmdExecutor;

    public AbstractSingleMessageCmd(ClientCmdExecutor cmdExecutor)
    {
        this.cmdExecutor = cmdExecutor;
    }

    public void execute()
    {
        cmdExecutor.execute(this);
    }

    public abstract RequestWriter getRequestWriter();
}
