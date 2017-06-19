package org.camunda.tngp.broker.util.msgpack;

import static org.camunda.tngp.util.buffer.BufferUtil.wrapArray;
import static org.camunda.tngp.util.buffer.BufferUtil.wrapString;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.msgpack.spec.MsgPackReader;
import org.camunda.tngp.msgpack.spec.MsgPackToken;
import org.camunda.tngp.msgpack.spec.MsgPackWriter;
import org.camunda.tngp.test.util.collection.MapBuilder;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class MsgPackUtil
{
    public static final ObjectMapper MSGPACK_MAPPER = new ObjectMapper(new MessagePackFactory());
    public static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    public static final String JSON_DOCUMENT = "{'string':'value', 'jsonObject':{'testAttr':'test'}}";
    public static final byte[] MSGPACK_PAYLOAD;

    static
    {
        JSON_MAPPER.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        byte[] bytes = null;
        try
        {
            bytes = MSGPACK_MAPPER.writeValueAsBytes(
                JSON_MAPPER.readTree(JSON_DOCUMENT));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            MSGPACK_PAYLOAD = bytes;
        }
    }


    public static DirectBuffer utf8(String value)
    {
        return wrapString(value);
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
        encodeMsgPack(buffer, arg);
        return buffer;
    }

    private static void encodeMsgPack(MutableDirectBuffer buffer, Consumer<MsgPackWriter> arg)
    {
        final MsgPackWriter writer = new MsgPackWriter();
        writer.wrap(buffer, 0);
        arg.accept(writer);
        buffer.wrap(buffer, 0, writer.getOffset());
    }

    public static Map<String, Object> asMap(byte[] array)
    {
        return asMap(wrapArray(array));
    }

    public static Map<String, Object> asMap(DirectBuffer buffer)
    {
        return asMap(buffer, 0, buffer.capacity());
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(DirectBuffer buffer, int offset, int length)
    {
        final MsgPackReader reader = new MsgPackReader();
        reader.wrap(buffer, offset, length);
        return (Map<String, Object>) deserializeElement(reader);
    }

    private static Object deserializeElement(MsgPackReader reader)
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

    public static DirectBuffer fromMap(Map<String, Object> entries)
    {
        return fromMap(new UnsafeBuffer(new byte[1024]), entries);
    }

    private static DirectBuffer fromMap(MutableDirectBuffer buffer, Map<String, Object> entries)
    {
        encodeMsgPack(buffer, c ->
        {
            c.writeMapHeader(entries.size());

            entries.entrySet().stream().forEach(e ->
            {
                c.writeString(wrapString(e.getKey()));

                // simple mapping - can be extended if needed
                final Object value = e.getValue();
                if (value instanceof String)
                {
                    c.writeString(wrapString((String) value));
                }
                else if (value instanceof Integer)
                {
                    c.writeInteger((int) value);
                }
                else if (value instanceof Long)
                {
                    c.writeInteger((long) value);
                }
                else if (value instanceof Double)
                {
                    c.writeFloat((double) value);
                }
                else if (value instanceof Boolean)
                {
                    c.writeBoolean((boolean) value);
                }
                else if (value instanceof byte[])
                {
                    c.writeBinary(wrapArray((byte[]) value));
                }
                else
                {
                    throw new UnsupportedOperationException();
                }
            });
        });

        return buffer;
    }

    public static MapBuilder<DirectBuffer> createMsgPack()
    {
        final MutableDirectBuffer buf = new UnsafeBuffer(new byte[1024]);

        return new MapBuilder<>(buf, m -> fromMap(buf, m));
    }
}
