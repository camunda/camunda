package org.camunda.tngp.client.cmd;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.camunda.tngp.client.ClientCommand;

public interface SetPayloadCmd<R, C extends ClientCommand<R>> extends ClientCommand<R>
{
    Charset CHARSET = StandardCharsets.UTF_8;

    C payload(String payload);

    default C payload(byte[] payload)
    {
        return payload(new String(payload, CHARSET));
    }

    default C payload(byte[] payload, int offset, int length)
    {
        final String payloadAsString = new String(payload, offset, length, CHARSET);

        return payload(payloadAsString);
    }

    default C payload(ByteBuffer byteBuffer)
    {
        final byte[] bytes = new byte[byteBuffer.capacity()];
        byteBuffer.get(bytes);

        return payload(bytes);
    }

    default C payload(DirectBuffer buffer, int offset, int length)
    {
        final byte[] bytes = new byte[length];
        buffer.getBytes(offset, bytes);

        return payload(bytes);
    }
}
