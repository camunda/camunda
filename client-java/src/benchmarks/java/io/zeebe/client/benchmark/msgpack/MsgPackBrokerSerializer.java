package io.zeebe.client.benchmark.msgpack;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import io.zeebe.broker.util.msgpack.UnpackedObject;

public class MsgPackBrokerSerializer implements MsgPackSerializer
{

    @Override
    public void serialize(Object value, MutableDirectBuffer buf, int offset)
    {
        ((UnpackedObject) value).write(buf, offset);
    }

    @Override
    public Object deserialize(Class<?> clazz, DirectBuffer buf, int offset, int length)
    {
        final UnpackedObject value;
        try
        {
            value = (UnpackedObject) clazz.newInstance();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        value.wrap(buf, offset, length);
        return value;
    }

    @Override
    public String getDescription()
    {
        return "Broker UnpackedObject";
    }
}
