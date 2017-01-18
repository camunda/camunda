package org.camunda.tngp.msgpack.spec;

import java.nio.ByteOrder;

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


    public static byte[] toByte(long value)
    {
        final UnsafeBuffer buf = new UnsafeBuffer(new byte[8]);
        buf.putLong(0, value, ByteOrder.BIG_ENDIAN);
        return buf.byteArray();
    }

    public static byte[] toByte(float value)
    {
        final UnsafeBuffer buf = new UnsafeBuffer(new byte[4]);
        buf.putFloat(0, value, ByteOrder.BIG_ENDIAN);
        return buf.byteArray();
    }

    public static byte[] toByte(double value)
    {
        final UnsafeBuffer buf = new UnsafeBuffer(new byte[8]);
        buf.putDouble(0, value, ByteOrder.BIG_ENDIAN);
        return buf.byteArray();
    }

}
