package org.camunda.tngp.example.msgpack;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.example.msgpack.impl.ByteUtil;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;

public class MsgPackUtil
{

    protected static DirectBuffer encodeMsgPack(CheckedConsumer<MessageBufferPacker> msgWriter)
    {
        MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
        try
        {
            msgWriter.accept(packer);
            packer.close();
        } catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        byte[] bytes = packer.toByteArray();
        System.out.println(ByteUtil.bytesToBinary(bytes));
        return new UnsafeBuffer(bytes);
    }

    @FunctionalInterface
    protected static interface CheckedConsumer<T>
    {
        void accept(T t) throws Exception;
    }

}
