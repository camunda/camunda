package org.camunda.tngp.taskqueue.impl.cmd;

import java.nio.ByteBuffer;

import org.camunda.tngp.taskqueue.client.ClientCommand;
import org.camunda.tngp.taskqueue.client.cmd.SetPayloadCmd;
import org.camunda.tngp.taskqueue.impl.TngpClientImpl;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

@SuppressWarnings("unchecked")
public abstract class AbstractSetPayloadCmd<R, C extends ClientCommand<R>> extends AbstractCmdImpl<R> implements SetPayloadCmd<R, C>
{
    protected final DirectBuffer payload = new UnsafeBuffer(0,0);
    protected int payloadLength = 0;

    public AbstractSetPayloadCmd(TngpClientImpl client, int responseSchemaId, int responseTemplateId)
    {
        super(client, responseSchemaId, responseTemplateId);
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
        this.payload.wrap(payload, 0, length);
        this.payloadLength = length;
        return (C) this;
    }

    @Override
    public C payload(ByteBuffer byteBuffer)
    {
        this.payload.wrap(byteBuffer);
        return (C) this;
    }

    @Override
    public C payload(DirectBuffer buffer, int offset, int length)
    {
        this.payload.wrap(buffer, offset, length);
        this.payloadLength = length;
        return (C) this;
    }

    @Override
    public void reset()
    {
        super.reset();

        payloadLength = 0;
        payload.wrap(0,0);
    }

}
