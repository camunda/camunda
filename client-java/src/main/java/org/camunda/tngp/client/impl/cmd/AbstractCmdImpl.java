package org.camunda.tngp.client.impl.cmd;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;

import org.camunda.tngp.client.ClientCommand;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.util.buffer.RequestWriter;

public abstract class AbstractCmdImpl<R> implements ClientCommand<R>
{
    protected static final Charset CHARSET = StandardCharsets.UTF_8;

    protected final ClientCmdExecutor cmdExecutor;
    protected final ClientResponseHandler<R> responseHandler;

    public AbstractCmdImpl(final ClientCmdExecutor cmdExecutor, ClientResponseHandler<R> responseHandler)
    {
        this.cmdExecutor = cmdExecutor;
        this.responseHandler = responseHandler;
    }

    @Override
    public R execute(final TransportConnection connection)
    {
        return cmdExecutor.execute(this, connection);
    }

    @Override
    public R execute()
    {
        return cmdExecutor.execute(this);
    }

    @Override
    public Future<R> executeAsync(final TransportConnection connection)
    {
        return cmdExecutor.executeAsync(this, connection);
    }

    public ClientResponseHandler<R> getResponseHandler()
    {
        return responseHandler;
    }

    public abstract RequestWriter getRequestWriter();

}
