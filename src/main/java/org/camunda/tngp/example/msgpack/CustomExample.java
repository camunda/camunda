package org.camunda.tngp.example.msgpack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.example.msgpack.impl.ByteUtil;
import org.camunda.tngp.example.msgpack.impl.JsonPathOperator;
import org.camunda.tngp.example.msgpack.impl.MsgPackNavigator;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;

public class CustomExample
{

    public static void main(String[] args) throws IOException
    {
        MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
        packer.packString("foo");
        packer.packString("bar");
        packer.close();
        System.out.println(ByteUtil.bytesToBinary(packer.toByteArray()));

        MsgPackNavigator navigator = new MsgPackNavigator();
        UnsafeBuffer buffer = new UnsafeBuffer(packer.toByteArray());
        navigator.wrap(buffer, 0, buffer.capacity());

        SysoutOperator operator = new SysoutOperator();
        navigator.matches(operator);
        navigator.next();
        navigator.matches(operator);

    }

    public static class SysoutOperator implements JsonPathOperator
    {

        @Override
        public boolean matchesString(MsgPackNavigator context, DirectBuffer buffer, int offset, int length)
        {
            byte[] bytes = new byte[length];
            buffer.getBytes(offset, bytes);
            System.out.println(new String(bytes, StandardCharsets.UTF_8));
            return false;
        }

    }
}
