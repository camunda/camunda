package org.camunda.tngp.client.benchmark.msgpack;

import static org.camunda.tngp.util.StringUtil.getBytes;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.client.benchmark.msgpack.MsgPackSerializer.Type;
import org.camunda.tngp.msgpack.spec.MsgPackWriter;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class POJODeserializationContext
{

    protected static final DirectBuffer PAYLOAD = new UnsafeBuffer(new byte[1024 * 1]);

    protected MutableDirectBuffer msgpackBuf = new UnsafeBuffer(new byte[1024 * 105]);

    @Param(value = {
            "JACKSON",
            "BROKER"
        })
    protected MsgPackSerializer.Type serializerType;


    protected MsgPackSerializer serializer;
    protected Class<?> targetClass;

    public POJODeserializationContext()
    {
        initMsgPack();
    }

    protected void initMsgPack()
    {
        final MsgPackWriter writer = new MsgPackWriter();
        writer.wrap(msgpackBuf, 0);
        writer.writeMapHeader(5);

        writer.writeString(utf8("eventType"));
        writer.writeString(utf8(TaskEventType.ABORT_FAILED.toString()));

        writer.writeString(utf8("lockTime"));
        writer.writeInteger(123123123L);

        writer.writeString(utf8("type"));
        writer.writeString(utf8("foofoobarbaz"));

        writer.writeString(utf8("headers"));
        writer.writeMapHeader(3);
        writer.writeString(utf8("key1"));
        writer.writeString(utf8("val1"));
        writer.writeString(utf8("key2"));
        writer.writeString(utf8("val2"));
        writer.writeString(utf8("key3"));
        writer.writeString(utf8("val3"));

        writer.writeString(utf8("payload"));
        writer.writeBinary(PAYLOAD);

        msgpackBuf.wrap(msgpackBuf, 0, writer.getOffset());
    }

    @Setup
    public void setUp()
    {
        if (serializerType == Type.BROKER)
        {
            serializer = new MsgPackBrokerSerializer();
            targetClass = BrokerTaskEvent.class;
        }
        else
        {
            serializer = new MsgPackJacksonSerializer();
            targetClass = JacksonTaskEvent.class;
        }
    }

    public MsgPackSerializer getSerializer()
    {
        return serializer;
    }

    protected static DirectBuffer utf8(String value)
    {
        return new UnsafeBuffer(getBytes(value));
    }

    public DirectBuffer getMsgpackBuffer()
    {
        return msgpackBuf;
    }

    public Class<?> getTargetClass()
    {
        return targetClass;
    }

}
