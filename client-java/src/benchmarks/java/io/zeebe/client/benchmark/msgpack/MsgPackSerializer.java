package io.zeebe.client.benchmark.msgpack;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public interface MsgPackSerializer
{

    void serialize(Object value, MutableDirectBuffer buf, int offset) throws Exception;

    Object deserialize(Class<?> clazz, DirectBuffer buf, int offset, int length) throws Exception;

    String getDescription();

    enum Type
    {
        JACKSON,
        BROKER
    }

}
