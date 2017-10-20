/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.msgpack;

import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.msgpack.spec.MsgPackWriter;
import io.zeebe.msgpack.value.ArrayValue;
import io.zeebe.msgpack.value.ValueArray;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

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

    public ValueArray<MinimalPOJO> simpleArray()
    {
        return simpleArrayProp;
    }

    public ValueArray<MinimalPOJO> emptyDefaultArray()
    {
        return emptyDefaultArrayProp;
    }

    public ValueArray<MinimalPOJO> notEmptyDefaultArray()
    {
        return notEmptyDefaultArrayProp;
    }

}
