package org.camunda.tngp.client.impl.cmd;

import java.nio.ByteBuffer;

import org.camunda.tngp.client.ClientCommand;
import org.camunda.tngp.client.cmd.SetPayloadCmd;
import org.camunda.tngp.client.impl.ClientCmdExecutor;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

@SuppressWarnings("unchecked")
public abstract class AbstractSetPayloadCmd<R, C extends ClientCommand<R>>
    extends AbstractCmdImpl<R> implements SetPayloadCmd<R, C>
{
    public AbstractSetPayloadCmd(final ClientCmdExecutor cmdExecutor, final ClientResponseHandler<R> responseHandler)
    {
        super(cmdExecutor, responseHandler);
    }

    @Override
    public C payload(String payload)
    {
        return payload(payload.getBytes(CHARSET));
    }

    @Override
    public C payload(byte[] payload)
    {
        return payload(payload, 0, payload.length);
    }

    @Override
    public C payload(byte[] payload, int offset, int length)
    {
        getPayloadBuffer().wrap(payload, 0, length);
        return (C) this;
    }

    @Override
    public C payload(ByteBuffer byteBuffer)
    {
        getPayloadBuffer().wrap(byteBuffer);
        return (C) this;
    }

    @Override
    public C payload(DirectBuffer buffer, int offset, int length)
    {
        getPayloadBuffer().wrap(buffer, offset, length);
        return (C) this;
    }

    protected abstract MutableDirectBuffer getPayloadBuffer();

}
