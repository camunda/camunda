package io.zeebe.msgpack.util;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;

public class MsgPackUtil
{

    public static DirectBuffer encodeMsgPack(CheckedConsumer<MessageBufferPacker> msgWriter)
    {
        final MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
        try
        {
            msgWriter.accept(packer);
            packer.close();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        final byte[] bytes = packer.toByteArray();
//        System.out.println(ByteUtil.bytesToBinary(bytes));
        return new UnsafeBuffer(bytes);
    }

    @FunctionalInterface
    public interface CheckedConsumer<T>
    {
        void accept(T t) throws Exception;
    }

}
