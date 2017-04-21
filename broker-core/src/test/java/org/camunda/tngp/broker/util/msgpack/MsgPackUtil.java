package org.camunda.tngp.broker.util.msgpack;

import static org.camunda.tngp.util.StringUtil.getBytes;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.msgpack.spec.MsgPackReader;
import org.camunda.tngp.msgpack.spec.MsgPackToken;
import org.camunda.tngp.msgpack.spec.MsgPackWriter;


public class MsgPackUtil
{

    public static DirectBuffer utf8(String value)
    {
        return new UnsafeBuffer(getBytes(value));
    }

    public static String toString(DirectBuffer buf)
    {
        final byte[] bytes = new byte[buf.capacity()];
        buf.getBytes(0, bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static String toString(Object[] arr)
    {
        final StringBuilder buf = new StringBuilder("[");

        if (arr.length > 0)
        {
            buf.append(arr[0].toString());
            for (int i = 1; i < arr.length; i++)
            {
                buf.append(", ");
                buf.append(arr[i].toString());
            }
        }

        buf.append("]");

        return buf.toString();
    }

    public static MutableDirectBuffer encodeMsgPack(Consumer<MsgPackWriter> arg)
    {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024]);
        final MsgPackWriter writer = new MsgPackWriter();
        writer.wrap(buffer, 0);
        arg.accept(writer);
        buffer.wrap(buffer, 0, writer.getOffset());
        return buffer;
    }

    public static Map<String, Object> asMap(DirectBuffer buffer, int offset, int length)
    {
        final MsgPackReader reader = new MsgPackReader();
        reader.wrap(buffer, offset, length);
        return (Map<String, Object>) deserializeElement(reader);
    }

    protected static Object deserializeElement(MsgPackReader reader)
    {

        final MsgPackToken token = reader.readToken();
        switch (token.getType())
        {
            case INTEGER:
                return token.getIntegerValue();
            case FLOAT:
                return token.getFloatValue();
            case STRING:
                return toString(token.getValueBuffer());
            case BOOLEAN:
                return token.getBooleanValue();
            case BINARY:
                final DirectBuffer valueBuffer = token.getValueBuffer();
                final byte[] valueArray = new byte[valueBuffer.capacity()];
                valueBuffer.getBytes(0, valueArray);
                return valueArray;
            case MAP:
                final Map<String, Object> valueMap = new HashMap<>();
                final int mapSize = token.getSize();
                for (int i = 0; i < mapSize; i++)
                {
                    final String key = (String) deserializeElement(reader);
                    final Object value = deserializeElement(reader);
                    valueMap.put(key, value);
                }
                return valueMap;
            case ARRAY:
                final int size = token.getSize();
                final Object[] arr = new Object[size];
                for (int i = 0; i < size; i++)
                {
                    arr[i] = deserializeElement(reader);
                }
                return toString(arr);
            default:
                throw new RuntimeException("Not implemented yet");
        }
    }
}
