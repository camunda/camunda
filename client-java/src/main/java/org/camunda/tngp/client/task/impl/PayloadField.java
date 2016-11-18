package org.camunda.tngp.client.task.impl;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class PayloadField
{


    protected final byte[] payload = new byte[1024 * 1024]; // TODO: size
    protected final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);
    protected String payloadString;

    public String getPayloadString()
    {
        return payloadString;
    }

    public void setPayloadString(String updatedPayload)
    {
        // TODO: ensure not null; ensure length, etc.
        this.payloadString = updatedPayload;
        final byte[] payloadBytes = updatedPayload.getBytes(StandardCharsets.UTF_8);
        this.payloadBuffer.wrap(payload, 0, payloadBytes.length);
        this.payloadBuffer.putBytes(0, payloadBytes);
    }

    public DirectBuffer getPayloadBuffer()
    {
        return payloadBuffer;
    }

    public void initFromPayloadBuffer(DirectBuffer buffer, int offset, int length)
    {
        // TODO: check bounds
        buffer.getBytes(offset, this.payload, 0, length);
        this.payloadBuffer.wrap(this.payload, 0, length);
        this.payloadString = new String(this.payload, 0, length, StandardCharsets.UTF_8);
    }

}
