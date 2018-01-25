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

import static io.zeebe.msgpack.MsgPackUtil.encodeMsgPack;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.Iterator;
import java.util.Map;

import io.zeebe.msgpack.spec.MsgPackWriter;
import io.zeebe.msgpack.value.ValueArray;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ArrayValueTest
{
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldSerializePOJO()
    {
        // given
        final POJOArray pojo = new POJOArray();
        final ValueArray<MinimalPOJO> iterator1 = pojo.simpleArray();
        iterator1.add().setLongProp(123L);
        iterator1.add().setLongProp(456L);
        iterator1.add().setLongProp(789L);

        final ValueArray<MinimalPOJO> iterator2 = pojo.emptyDefaultArray();
        iterator2.add().setLongProp(753L);

        final ValueArray<MinimalPOJO> iterator3 = pojo.notEmptyDefaultArray();
        iterator3.add().setLongProp(357L);
        iterator3.add().setLongProp(951L);

        final int writeLength = pojo.getLength();

        // when
        final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[writeLength]);
        pojo.write(resultBuffer, 0);

        // then
        final Map<String, Object> msgPackMap = MsgPackUtil.asMap(resultBuffer, 0, resultBuffer.capacity());
        assertThat(msgPackMap).hasSize(3);
        assertThat(msgPackMap).contains(
                entry("simpleArray", "[{longProp=123}, {longProp=456}, {longProp=789}]"),
                entry("emptyDefaultArray", "[{longProp=753}]"),
                entry("notEmptyDefaultArray", "[{longProp=357}, {longProp=951}, {longProp=123}, {longProp=456}, {longProp=789}]"));
    }

    @Test
    public void shouldSerializePOJOWithDefaultValues()
    {
        // given
        final POJOArray pojo = new POJOArray();
        final ValueArray<MinimalPOJO> iterator1 = pojo.simpleArray();
        iterator1.add().setLongProp(123L);

        final int writeLength = pojo.getLength();

        // when
        final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[writeLength]);
        pojo.write(resultBuffer, 0);

        // then
        final Map<String, Object> msgPackMap = MsgPackUtil.asMap(resultBuffer, 0, resultBuffer.capacity());
        assertThat(msgPackMap).hasSize(3);
        assertThat(msgPackMap).contains(
                entry("simpleArray", "[{longProp=123}]"),
                entry("emptyDefaultArray", "[]"),
                entry("notEmptyDefaultArray", "[{longProp=123}, {longProp=456}, {longProp=789}]"));
    }

    @Test
    public void shouldSerializeAfterPartiallyReadEntries()
    {
        // given
        final POJOArray pojo = new POJOArray();

        final DirectBuffer buffer = encodeMsgPack((w) ->
        {
            w.writeMapHeader(1);
            encodeSimpleArrayProp(w);
        });

        pojo.wrap(buffer);
        final Iterator<MinimalPOJO> iterator = pojo.simpleArray().iterator();
        iterator.next();
        iterator.next();
        iterator.next();

        final int writeLength = pojo.getLength();

        // when
        final UnsafeBuffer pojoBuffer = new UnsafeBuffer(new byte[writeLength]);
        pojo.write(pojoBuffer, 0);

        // then
        final Map<String, Object> msgPackMap = MsgPackUtil.asMap(pojoBuffer, 0, pojoBuffer.capacity());
        assertThat(msgPackMap).hasSize(3);
        assertThat(msgPackMap).contains(
                entry("simpleArray", "[{longProp=123}, {longProp=456}, {longProp=789}, {longProp=555}, {longProp=777}]"),
                entry("emptyDefaultArray", "[]"),
                entry("notEmptyDefaultArray", "[{longProp=123}, {longProp=456}, {longProp=789}]"));
    }

    @Test
    public void shouldNotSerializeRemovedEntry()
    {
        // given
        final POJOArray pojo = new POJOArray();

        final DirectBuffer buffer = encodeMsgPack((w) ->
        {
            w.writeMapHeader(1);
            encodeSimpleArrayProp(w);
        });

        pojo.wrap(buffer);
        final Iterator<MinimalPOJO> iterator = pojo.simpleArray().iterator();
        iterator.next();
        iterator.next();
        iterator.next();

        // when
        iterator.remove();

        // then
        final int writeLength = pojo.getLength();
        final UnsafeBuffer pojoBuffer = new UnsafeBuffer(new byte[writeLength]);
        pojo.write(pojoBuffer, 0);

        final Map<String, Object> msgPackMap = MsgPackUtil.asMap(pojoBuffer, 0, pojoBuffer.capacity());
        assertThat(msgPackMap).hasSize(3);
        assertThat(msgPackMap).contains(
                entry("simpleArray", "[{longProp=123}, {longProp=456}, {longProp=555}, {longProp=777}]"),
                entry("emptyDefaultArray", "[]"),
                entry("notEmptyDefaultArray", "[{longProp=123}, {longProp=456}, {longProp=789}]"));
    }

    @Test
    public void shouldSerializeAppendedEntry()
    {
        // given
        final POJOArray pojo = new POJOArray();

        final DirectBuffer buffer = encodeMsgPack((w) ->
        {
            w.writeMapHeader(1);
            encodeSimpleArrayProp(w);
        });

        pojo.wrap(buffer);
        final Iterator<MinimalPOJO> iterator = pojo.simpleArray().iterator();
        iterator.next();
        iterator.next();
        iterator.next();
        iterator.next();
        iterator.next();

        // when
        pojo.simpleArray().add().setLongProp(999L);

        // then
        final int writeLength = pojo.getLength();
        final UnsafeBuffer pojoBuffer = new UnsafeBuffer(new byte[writeLength]);
        pojo.write(pojoBuffer, 0);

        final Map<String, Object> msgPackMap = MsgPackUtil.asMap(pojoBuffer, 0, pojoBuffer.capacity());
        assertThat(msgPackMap).hasSize(3);
        assertThat(msgPackMap).contains(
                entry("simpleArray", "[{longProp=123}, {longProp=456}, {longProp=789}, {longProp=555}, {longProp=777}, {longProp=999}]"),
                entry("emptyDefaultArray", "[]"),
                entry("notEmptyDefaultArray", "[{longProp=123}, {longProp=456}, {longProp=789}]"));
    }

    @Test
    public void shouldSerializeInbetweenAddedEntry()
    {
        // given
        final POJOArray pojo = new POJOArray();
        final DirectBuffer buffer = encodeMsgPack((w) ->
        {
            w.writeMapHeader(1);
            encodeSimpleArrayProp(w);
        });

        pojo.wrap(buffer);
        final Iterator<MinimalPOJO> iterator = pojo.simpleArray().iterator();
        iterator.next();
        iterator.next();
        iterator.next();

        // when
        pojo.simpleArrayProp.add().setLongProp(999L);

        // then
        final int writeLength = pojo.getLength();
        final UnsafeBuffer pojoBuffer = new UnsafeBuffer(new byte[writeLength]);
        pojo.write(pojoBuffer, 0);

        final Map<String, Object> msgPackMap = MsgPackUtil.asMap(pojoBuffer, 0, pojoBuffer.capacity());
        assertThat(msgPackMap).hasSize(3);
        assertThat(msgPackMap).contains(
                entry("simpleArray", "[{longProp=123}, {longProp=456}, {longProp=789}, {longProp=999}, {longProp=555}, {longProp=777}]"),
                entry("emptyDefaultArray", "[]"),
                entry("notEmptyDefaultArray", "[{longProp=123}, {longProp=456}, {longProp=789}]"));
    }

    @Test
    public void shouldDeserializePOJO()
    {
        // given
        final POJOArray pojo = new POJOArray();

        final DirectBuffer buffer = encodeMsgPack((w) ->
        {
            w.writeMapHeader(3);
            encodeSimpleArrayProp(w);

            w.writeString(wrapString("emptyDefaultArray"));
            w.writeArrayHeader(1);

            w.writeMapHeader(1);
            w.writeString(wrapString("longProp"));
            w.writeInteger(753L);

            w.writeString(wrapString("notEmptyDefaultArray"));
            w.writeArrayHeader(0);
        });

        // when
        pojo.wrap(buffer);

        // then
        final Iterator<MinimalPOJO> iterator1 = pojo.simpleArray().iterator();
        assertThat(iterator1.hasNext()).isTrue();
        assertThat(iterator1.next().getLongProp()).isEqualTo(123L);
        assertThat(iterator1.hasNext()).isTrue();
        assertThat(iterator1.next().getLongProp()).isEqualTo(456L);
        assertThat(iterator1.hasNext()).isTrue();
        assertThat(iterator1.next().getLongProp()).isEqualTo(789L);
        assertThat(iterator1.hasNext()).isTrue();
        assertThat(iterator1.next().getLongProp()).isEqualTo(555L);
        assertThat(iterator1.hasNext()).isTrue();
        assertThat(iterator1.next().getLongProp()).isEqualTo(777L);
        assertThat(iterator1.hasNext()).isFalse();

        final Iterator<MinimalPOJO> iterator2 = pojo.emptyDefaultArray().iterator();
        assertThat(iterator2.hasNext()).isTrue();
        assertThat(iterator2.next().getLongProp()).isEqualTo(753L);
        assertThat(iterator2.hasNext()).isFalse();

        final Iterator<MinimalPOJO> iterator3 = pojo.notEmptyDefaultArray().iterator();
        assertThat(iterator3.hasNext()).isFalse();
    }

    @Test
    public void shouldDeserializePOJOWithDefaultValues()
    {
        // given
        final POJOArray pojo = new POJOArray();

        final DirectBuffer buffer = encodeMsgPack((w) ->
        {
            w.writeMapHeader(1);
            encodeSimpleArrayProp(w);
        });

        // when
        pojo.wrap(buffer);

        // then
        final Iterator<MinimalPOJO> iterator1 = pojo.simpleArray().iterator();
        assertThat(iterator1.hasNext()).isTrue();
        assertThat(iterator1.next().getLongProp()).isEqualTo(123L);
        assertThat(iterator1.hasNext()).isTrue();
        assertThat(iterator1.next().getLongProp()).isEqualTo(456L);
        assertThat(iterator1.hasNext()).isTrue();
        assertThat(iterator1.next().getLongProp()).isEqualTo(789L);
        assertThat(iterator1.hasNext()).isTrue();
        assertThat(iterator1.next().getLongProp()).isEqualTo(555L);
        assertThat(iterator1.hasNext()).isTrue();
        assertThat(iterator1.next().getLongProp()).isEqualTo(777L);
        assertThat(iterator1.hasNext()).isFalse();

        final Iterator<MinimalPOJO> iterator2 = pojo.emptyDefaultArray().iterator();
        assertThat(iterator2.hasNext()).isFalse();

        final Iterator<MinimalPOJO> iterator3 = pojo.notEmptyDefaultArray().iterator();
        assertThat(iterator3.hasNext()).isTrue();
        assertThat(iterator3.next().getLongProp()).isEqualTo(123L);
        assertThat(iterator3.hasNext()).isTrue();
        assertThat(iterator3.next().getLongProp()).isEqualTo(456L);
        assertThat(iterator3.hasNext()).isTrue();
        assertThat(iterator3.next().getLongProp()).isEqualTo(789L);
        assertThat(iterator3.hasNext()).isFalse();
    }

    @Test
    public void shouldFailLengthEstimationWithMissingRequiredValues()
    {
        // given
        final POJOArray pojo = new POJOArray();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Property 'simpleArray' has no valid value");

        // when
        pojo.getLength();
    }

    @Test
    public void shouldFailSerializationWithMissingRequiredValues()
    {
        // given
        final POJOArray pojo = new POJOArray();

        final UnsafeBuffer buf = new UnsafeBuffer(new byte[1024]);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Cannot write property 'simpleArray'; neither value, nor default value specified");

        // when
        pojo.write(buf, 0);
    }

    @Test
    public void shouldFailOnHasNextWithMissingRequiredValues()
    {
        // given
        final POJOArray pojo = new POJOArray();
        final ValueArray<MinimalPOJO> array = pojo.simpleArray();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Property 'simpleArray' has no valid value");

        // when
        array.iterator().hasNext();
    }

    @Test
    public void shouldFailOnNextWithMissingRequiredValues()
    {
        // given
        final POJOArray pojo = new POJOArray();
        final ValueArray<MinimalPOJO> array = pojo.simpleArray();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Property 'simpleArray' has no valid value");

        // when
        array.iterator().next();
    }

    @Test
    public void shouldFailOnInitialRemove()
    {
        // given
        final POJOArray pojo = new POJOArray();

        final DirectBuffer buffer = encodeMsgPack((w) ->
        {
            w.writeMapHeader(1);
            encodeSimpleArrayProp(w);
        });

        pojo.wrap(buffer);
        final Iterator<MinimalPOJO> iterator = pojo.simpleArray().iterator();

        // then
        exception.expect(IllegalStateException.class);

        // when
        iterator.remove();
    }

    @Test
    public void shouldFailOnRemovingEntryTwice()
    {
        // given
        final POJOArray pojo = new POJOArray();

        final DirectBuffer buffer = encodeMsgPack((w) ->
        {
            w.writeMapHeader(1);
            encodeSimpleArrayProp(w);
        });

        pojo.wrap(buffer);
        final Iterator<MinimalPOJO> iterator = pojo.simpleArray().iterator();
        iterator.next();
        iterator.remove();

        // then
        exception.expect(IllegalStateException.class);

        // when
        iterator.remove();
    }

    @Test
    public void shouldFailOnRemovingWhenEntryHasBeenAddedBefore()
    {
        // given
        final POJOArray pojo = new POJOArray();

        final DirectBuffer buffer = encodeMsgPack((w) ->
        {
            w.writeMapHeader(1);
            encodeSimpleArrayProp(w);
        });

        pojo.wrap(buffer);
        final Iterator<MinimalPOJO> iterator = pojo.simpleArray().iterator();
        iterator.next();
        pojo.simpleArray().add().setLongProp(999L);

        // then
        exception.expect(IllegalStateException.class);

        // when
        iterator.remove();
    }

    @Test
    public void shouldAddFirstEntryToSimpleArrayProp()
    {
        // given
        final POJOArray pojo = new POJOArray();
        final ValueArray<MinimalPOJO> iterator = pojo.simpleArray();

        // when
        iterator.add().setLongProp(741L);

        // then
        final int length = pojo.getLength();
        final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[length]);
        pojo.write(resultBuffer, 0);

        final Map<String, Object> msgPackMap = MsgPackUtil.asMap(resultBuffer, 0, resultBuffer.capacity());
        assertThat(msgPackMap).hasSize(3);
        assertThat(msgPackMap).contains(
                entry("simpleArray", "[{longProp=741}]"),
                entry("emptyDefaultArray", "[]"),
                entry("notEmptyDefaultArray", "[{longProp=123}, {longProp=456}, {longProp=789}]"));
    }

    @Test
    public void shouldAddFirstEntryToEmptyDefaultArrayProp()
    {
        // given
        final POJOArray pojo = new POJOArray();
        pojo.simpleArray().add().setLongProp(123L);
        final ValueArray<MinimalPOJO> iterator = pojo.emptyDefaultArray();

        // when
        iterator.add().setLongProp(741L);

        // then
        final int length = pojo.getLength();
        final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[length]);
        pojo.write(resultBuffer, 0);

        final Map<String, Object> msgPackMap = MsgPackUtil.asMap(resultBuffer, 0, resultBuffer.capacity());
        assertThat(msgPackMap).hasSize(3);
        assertThat(msgPackMap).contains(
                entry("simpleArray", "[{longProp=123}]"),
                entry("emptyDefaultArray", "[{longProp=741}]"),
                entry("notEmptyDefaultArray", "[{longProp=123}, {longProp=456}, {longProp=789}]"));
    }

    @Test
    public void shouldAddFirstEntryToNotEmptyDefaultArrayProp()
    {
        // given
        final POJOArray pojo = new POJOArray();
        pojo.simpleArray().add().setLongProp(123L);
        final ValueArray<MinimalPOJO> iterator = pojo.notEmptyDefaultArray();

        // when
        iterator.add().setLongProp(741L);

        // then
        final int length = pojo.getLength();
        final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[length]);
        pojo.write(resultBuffer, 0);

        final Map<String, Object> msgPackMap = MsgPackUtil.asMap(resultBuffer, 0, resultBuffer.capacity());
        assertThat(msgPackMap).hasSize(3);
        assertThat(msgPackMap).contains(
                entry("simpleArray", "[{longProp=123}]"),
                entry("emptyDefaultArray", "[]"),
                entry("notEmptyDefaultArray", "[{longProp=741}, {longProp=123}, {longProp=456}, {longProp=789}]"));
    }

    @Test
    public void shouldAppendNewEntryToNotEmptyDefaultArrayProp()
    {
        // given
        final POJOArray pojo = new POJOArray();
        pojo.simpleArray().add().setLongProp(123L);
        final Iterator<MinimalPOJO> iterator = pojo.notEmptyDefaultArray().iterator();

        // when
        while (iterator.hasNext())
        {
            iterator.next();
        }
        pojo.notEmptyDefaultArray().add().setLongProp(741L);

        // then
        final int length = pojo.getLength();
        final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[length]);
        pojo.write(resultBuffer, 0);

        final Map<String, Object> msgPackMap = MsgPackUtil.asMap(resultBuffer, 0, resultBuffer.capacity());
        assertThat(msgPackMap).hasSize(3);
        assertThat(msgPackMap).contains(
                entry("simpleArray", "[{longProp=123}]"),
                entry("emptyDefaultArray", "[]"),
                entry("notEmptyDefaultArray", "[{longProp=123}, {longProp=456}, {longProp=789}, {longProp=741}]"));
    }

    @Test
    public void shouldSetNotEmptyDefaultArrayPropToEmptyArray()
    {
        // given
        final POJOArray pojo = new POJOArray();
        pojo.simpleArray().add().setLongProp(123L);
        final Iterator<MinimalPOJO> iterator = pojo.notEmptyDefaultArray().iterator();

        // when
        iterator.next();
        iterator.remove();

        // then
        final int length = pojo.getLength();
        final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[length]);
        pojo.write(resultBuffer, 0);

        final Map<String, Object> msgPackMap = MsgPackUtil.asMap(resultBuffer, 0, resultBuffer.capacity());
        assertThat(msgPackMap).hasSize(3);
        assertThat(msgPackMap).contains(
                entry("simpleArray", "[{longProp=123}]"),
                entry("emptyDefaultArray", "[]"),
                entry("notEmptyDefaultArray", "[{longProp=456}, {longProp=789}]"));
    }

    @Test
    public void shouldRemoveEntriesFromNotEmptyDefaultArrayProp()
    {
        // given
        final POJOArray pojo = new POJOArray();
        pojo.simpleArray().add().setLongProp(123L);
        final Iterator<MinimalPOJO> iterator = pojo.notEmptyDefaultArray().iterator();

        // when
        iterator.next();
        iterator.next();
        iterator.remove();

        // then
        final int length = pojo.getLength();
        final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[length]);
        pojo.write(resultBuffer, 0);

        final Map<String, Object> msgPackMap = MsgPackUtil.asMap(resultBuffer, 0, resultBuffer.capacity());
        assertThat(msgPackMap).hasSize(3);
        assertThat(msgPackMap).contains(
                entry("simpleArray", "[{longProp=123}]"),
                entry("emptyDefaultArray", "[]"),
                entry("notEmptyDefaultArray", "[{longProp=123}, {longProp=789}]"));
    }

    @Test
    public void shouldWriteUpdatedDefaultValue()
    {
        // given
        final POJOArray pojo = new POJOArray();
        pojo.simpleArray().add().setLongProp(123L);
        final Iterator<MinimalPOJO> iterator = pojo.notEmptyDefaultArray().iterator();

        iterator.next().setLongProp(Long.MAX_VALUE);

        // when
        final int length = pojo.getLength();
        final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[length]);
        pojo.write(resultBuffer, 0);

        // then
        final Map<String, Object> msgPackMap = MsgPackUtil.asMap(resultBuffer, 0, resultBuffer.capacity());
        assertThat(msgPackMap).hasSize(3);
        assertThat(msgPackMap).contains(
                entry("simpleArray", "[{longProp=123}]"),
                entry("emptyDefaultArray", "[]"),
                entry("notEmptyDefaultArray", "[{longProp=9223372036854775807}, {longProp=456}, {longProp=789}]"));
    }

    @Test
    public void shouldWriteUpdatedDefaultValueAndAddedValue()
    {
        // given
        final POJOArray pojo = new POJOArray();
        pojo.simpleArray().add().setLongProp(123L);
        final Iterator<MinimalPOJO> iterator = pojo.notEmptyDefaultArray().iterator();

        iterator.next().setLongProp(Long.MAX_VALUE);
        pojo.notEmptyDefaultArray().add().setLongProp(1L);

        // when
        final int length = pojo.getLength();
        final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[length]);
        pojo.write(resultBuffer, 0);

        // then
        final Map<String, Object> msgPackMap = MsgPackUtil.asMap(resultBuffer, 0, resultBuffer.capacity());
        assertThat(msgPackMap).hasSize(3);
        assertThat(msgPackMap).contains(
                entry("simpleArray", "[{longProp=123}]"),
                entry("emptyDefaultArray", "[]"),
                entry("notEmptyDefaultArray", "[{longProp=9223372036854775807}, {longProp=1}, {longProp=456}, {longProp=789}]"));
    }

    @Test
    public void shouldIterateOverModifiedArray()
    {
        // given
        final POJOArray pojo = new POJOArray();
        final ValueArray<MinimalPOJO> array = pojo.simpleArray();

        // when
        array.add().setLongProp(123L);

        // then
        final Iterator<MinimalPOJO> iterator = array.iterator();
        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next().getLongProp()).isEqualTo(123L);
        assertThat(iterator.hasNext()).isFalse();
    }

    @Test
    public void shouldReadModifiedArrayValue()
    {
        // given
        final POJOArray pojoArray = new POJOArray();
        final ValueArray<MinimalPOJO> array = pojoArray.notEmptyDefaultArray();

        // when
        final MinimalPOJO pojo = array.iterator().next();

        final long oldValue = pojo.getLongProp();
        final long newValue = oldValue + 1;
        pojo.setLongProp(newValue);

        // then
        final Iterator<MinimalPOJO> iterator = array.iterator();
        assertThat(iterator.next().getLongProp()).isEqualTo(newValue);
    }

    protected void encodeSimpleArrayProp(MsgPackWriter writer)
    {
        writer.writeString(wrapString("simpleArray"));
        writer.writeArrayHeader(5);

        writer.writeMapHeader(1);
        writer.writeString(wrapString("longProp"));
        writer.writeInteger(123L);

        writer.writeMapHeader(1);
        writer.writeString(wrapString("longProp"));
        writer.writeInteger(456L);

        writer.writeMapHeader(1);
        writer.writeString(wrapString("longProp"));
        writer.writeInteger(789L);

        writer.writeMapHeader(1);
        writer.writeString(wrapString("longProp"));
        writer.writeInteger(555L);

        writer.writeMapHeader(1);
        writer.writeString(wrapString("longProp"));
        writer.writeInteger(777L);
    }
}
