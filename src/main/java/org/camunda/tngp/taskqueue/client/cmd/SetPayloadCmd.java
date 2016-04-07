package org.camunda.tngp.taskqueue.client.cmd;

import java.nio.ByteBuffer;

import org.camunda.tngp.taskqueue.client.ClientCommand;

import uk.co.real_logic.agrona.DirectBuffer;

public interface SetPayloadCmd<R, C extends ClientCommand<R>> extends ClientCommand<R>
{
    C payload(String payload);

    C payload(byte[] payload);

    C payload(byte[] payload, int offset, int length);

    C payload(ByteBuffer byteBuffer);

    C payload(DirectBuffer buffer, int offset, int length);
}
