package io.zeebe.client.impl.cmd;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;

import io.zeebe.client.cmd.ClientCommand;
import io.zeebe.client.impl.ClientCommandManager;
import io.zeebe.client.impl.Topic;
import io.zeebe.util.buffer.RequestWriter;

public abstract class AbstractCmdImpl<R> implements ClientCommand<R>
{
    protected static final Charset CHARSET = StandardCharsets.UTF_8;

    private final ClientCommandManager commandManager;
    protected final Topic topic;

    public AbstractCmdImpl(final ClientCommandManager commandManager, final Topic topic)
    {
        this.commandManager = commandManager;
        this.topic = topic;
    }

    @Override
    public R execute()
    {
        return commandManager.execute(this);
    }

    @Override
    public Future<R> executeAsync()
    {
        return commandManager.executeAsync(this);
    }

    @Override
    public Topic getTopic()
    {
        return topic;
    }

    public abstract ClientResponseHandler<R> getResponseHandler();

    public abstract RequestWriter getRequestWriter();

}
