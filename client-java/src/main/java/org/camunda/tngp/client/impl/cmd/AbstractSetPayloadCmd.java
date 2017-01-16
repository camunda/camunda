package org.camunda.tngp.client.impl.cmd;

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.camunda.tngp.client.ClientCommand;
import org.camunda.tngp.client.cmd.SetPayloadCmd;
import org.camunda.tngp.client.impl.ClientCmdExecutor;

public abstract class AbstractSetPayloadCmd<R, C extends ClientCommand<R>> extends AbstractCmdImpl<R> implements SetPayloadCmd<R, C>
{
    public AbstractSetPayloadCmd(final ClientCmdExecutor cmdExecutor, final ClientResponseHandler<R> responseHandler)
    {
        super(cmdExecutor, responseHandler);
    }

    @Override
    public C payload(byte[] payload)
    {
        return payload(new String(payload, CHARSET));
    }

    @Override
    public C payload(byte[] payload, int offset, int length)
    {
        final String payloadAsString = new String(payload, offset, length, CHARSET);

        return payload(payloadAsString);
    }

    @Override
    public C payload(ByteBuffer byteBuffer)
    {
        final byte[] bytes = new byte[byteBuffer.capacity()];
        byteBuffer.get(bytes);

        return payload(bytes);
    }

    @Override
    public C payload(DirectBuffer buffer, int offset, int length)
    {
        final byte[] bytes = new byte[length];
        buffer.getBytes(offset, bytes);

        return payload(bytes);
    }

}
