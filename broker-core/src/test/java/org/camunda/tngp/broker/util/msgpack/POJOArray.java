package org.camunda.tngp.broker.util.msgpack;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.util.msgpack.property.ArrayProperty;
import org.camunda.tngp.broker.util.msgpack.value.ArrayValue;
import org.camunda.tngp.broker.util.msgpack.value.ArrayValueIterator;
import org.camunda.tngp.msgpack.spec.MsgPackHelper;
import org.camunda.tngp.msgpack.spec.MsgPackWriter;

public class POJOArray extends UnpackedObject
{
    protected static final DirectBuffer EMPTY_ARRAY = new UnsafeBuffer(MsgPackHelper.EMPTY_ARRAY);

    protected static final DirectBuffer NOT_EMPTY_ARRAY;

    static
    {
        final ArrayValue<MinimalPOJO> values = new ArrayValue<>();
        values.setInnerValue(new MinimalPOJO());

        values.add().setLongProp(123L);
        values.add().setLongProp(456L);
        values.add().setLongProp(789L);

        final int length = values.getEncodedLength();
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[length]);

        final MsgPackWriter writer = new MsgPackWriter();
        writer.wrap(buffer, 0);
        values.write(writer);

        NOT_EMPTY_ARRAY = buffer;
    }

    protected ArrayProperty<MinimalPOJO> simpleArrayProp;
    protected ArrayProperty<MinimalPOJO> emptyDefaultArrayProp;
    protected ArrayProperty<MinimalPOJO> notEmptyDefaultArrayProp;

    public POJOArray()
    {
        this.simpleArrayProp = new ArrayProperty<>("simpleArray", new ArrayValue<>(), new MinimalPOJO());

        this.emptyDefaultArrayProp = new ArrayProperty<>("emptyDefaultArray",
                new ArrayValue<>(),
                new ArrayValue<>(EMPTY_ARRAY, 0, EMPTY_ARRAY.capacity()),
                new MinimalPOJO());

        this.notEmptyDefaultArrayProp = new ArrayProperty<>("notEmptyDefaultArray",
                new ArrayValue<>(),
                new ArrayValue<>(NOT_EMPTY_ARRAY, 0, NOT_EMPTY_ARRAY.capacity()),
                new MinimalPOJO());

        this.declareProperty(simpleArrayProp)
            .declareProperty(emptyDefaultArrayProp)
            .declareProperty(notEmptyDefaultArrayProp);
    }

    public ArrayValueIterator<MinimalPOJO> simpleArray()
    {
        return simpleArrayProp;
    }

    public ArrayValueIterator<MinimalPOJO> emptyDefaultArray()
    {
        return emptyDefaultArrayProp;
    }

    public ArrayValueIterator<MinimalPOJO> notEmptyDefaultArray()
    {
        return notEmptyDefaultArrayProp;
    }

}
