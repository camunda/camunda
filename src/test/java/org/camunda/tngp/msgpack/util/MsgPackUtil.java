package org.camunda.tngp.msgpack.util;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.msgpack.query.MsgPackFilterContext;
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

    public static MsgPackFilterContext generateDefaultInstances(int... filterIds)
    {

        final MsgPackFilterContext filterInstances = new MsgPackFilterContext(filterIds.length, 10);
        for (int i = 0; i < filterIds.length; i++)
        {
            filterInstances.appendElement();
            filterInstances.filterId(filterIds[i]);
        }
        return filterInstances;
    }

}
