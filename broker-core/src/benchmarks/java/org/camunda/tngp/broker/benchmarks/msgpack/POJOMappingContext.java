package org.camunda.tngp.broker.benchmarks.msgpack;

import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.taskqueue.data.TaskEvent;
import org.camunda.tngp.broker.taskqueue.data.TaskEventType;
import org.camunda.tngp.msgpack.spec.MsgPackReader;
import org.camunda.tngp.msgpack.spec.MsgPackWriter;
import org.camunda.tngp.util.buffer.BufferUtil;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class POJOMappingContext
{

    protected TaskEvent taskEvent = new TaskEvent();

    /*
     * values encoded in the way TaskEvent declares them
     */
    protected MutableDirectBuffer optimalOrderMsgPack;
    protected DirectBuffer reverseOrderMsgPack;

    protected MutableDirectBuffer writeBuffer;


    @Setup
    public void setUp()
    {

        taskEvent.setEventType(TaskEventType.CREATE);
        taskEvent.setLockTime(System.currentTimeMillis());
        taskEvent.setType(BufferUtil.wrapString("someTaskType"));

        final DirectBuffer payload = write((w) ->
        {
            w.writeString(BufferUtil.wrapString("key1"));
            w.writeString(BufferUtil.wrapString("aValue"));
            w.writeString(BufferUtil.wrapString("key2"));
            w.writeString(BufferUtil.wrapString("alsoaValue"));
            w.writeString(BufferUtil.wrapString("key3"));
            w.writeString(BufferUtil.wrapString("anotherValue"));
            w.writeString(BufferUtil.wrapString("key4"));
            w.writeString(BufferUtil.wrapString("yetAnotherValue"));
        });
        taskEvent.setPayload(payload);

        final DirectBuffer headers = write((w) ->
        {
            w.writeMapHeader(2);
            w.writeString(BufferUtil.wrapString("key1"));
            w.writeString(BufferUtil.wrapString("value"));
            w.writeString(BufferUtil.wrapString("key2"));
            w.writeString(BufferUtil.wrapString("value"));
        });
        taskEvent.setHeaders(headers);

        optimalOrderMsgPack = new UnsafeBuffer(new byte[taskEvent.getLength()]);
        taskEvent.write(optimalOrderMsgPack, 0);

        this.reverseOrderMsgPack = revertMapProperties(optimalOrderMsgPack);

        this.writeBuffer = new UnsafeBuffer(new byte[optimalOrderMsgPack.capacity()]);
    }

    protected DirectBuffer write(Consumer<MsgPackWriter> arg)
    {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024]);
        final MsgPackWriter writer = new MsgPackWriter();
        writer.wrap(buffer, 0);
        arg.accept(writer);
        buffer.wrap(buffer, 0, writer.getOffset());
        return buffer;
    }

    public DirectBuffer getOptimalOrderEncodedTaskEvent()
    {
        return optimalOrderMsgPack;
    }

    public DirectBuffer getReverseOrderEncodedTaskEvent()
    {
        return reverseOrderMsgPack;
    }

    public MutableDirectBuffer getWriteBuffer()
    {
        return writeBuffer;
    }

    public TaskEvent getTaskEvent()
    {
        return taskEvent;
    }

    protected DirectBuffer revertMapProperties(DirectBuffer msgPack)
    {
        MsgPackReader reader = new MsgPackReader();
        reader.wrap(msgPack, 0, msgPack.capacity());
        int size = reader.readMapHeader();

        UnsafeBuffer buf = new UnsafeBuffer(new byte[msgPack.capacity()]);

        MsgPackWriter writer = new MsgPackWriter();
        writer.wrap(buf, 0);
        writer.writeMapHeader(size);

        int targetOffset = msgPack.capacity();

        for (int i = 0; i < size; i++)
        {
            int keySourceOffset = reader.getOffset();
            reader.skipValue();
            int valueSourceOffset = reader.getOffset();
            int keyLength = valueSourceOffset - keySourceOffset;
            reader.skipValue();
            int valueLength = reader.getOffset() - valueSourceOffset;

            targetOffset -= keyLength + valueLength;
            buf.putBytes(targetOffset, msgPack, keySourceOffset, keyLength + valueLength);
        }

        return buf;
    }
}
