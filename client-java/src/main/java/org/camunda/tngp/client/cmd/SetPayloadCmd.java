package org.camunda.tngp.client.cmd;

import java.nio.ByteBuffer;

import org.camunda.tngp.client.ClientCommand;

import org.agrona.DirectBuffer;

public interface SetPayloadCmd<R, C extends ClientCommand<R>> extends ClientCommand<R>
{
    C payload(String payload);

    C payload(byte[] payload);

    C payload(byte[] payload, int offset, int length);

    C payload(ByteBuffer byteBuffer);

    C payload(DirectBuffer buffer, int offset, int length);
}
