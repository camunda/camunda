package org.camunda.tngp.example.msgpack.impl;

import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;

public class KeyOperator implements JsonPathOperator
{
    protected byte[] keyBytes;

    public void wrap(String key)
    {
        keyBytes = key.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean matchesString(MsgPackNavigator context, DirectBuffer buffer, int offset, int length)
    {
        // TODO: assert length

        return ByteUtil.equal(keyBytes, buffer, offset, length);

    }

}
