package org.camunda.tngp.broker.util.msgpack;

import static org.assertj.core.api.Assertions.*;
import static org.camunda.tngp.broker.util.msgpack.MsgPackUtil.*;

import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.util.msgpack.value.ArrayValueIterator;
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
        final ArrayValueIterator<MinimalPOJO> iterator1 = pojo.simpleArray();
        iterator1.add().setLongProp(123L);
        iterator1.add().setLongProp(456L);
        iterator1.add().setLongProp(789L);

        final ArrayValueIterator<MinimalPOJO> iterator2 = pojo.emptyDefaultArray();
        iterator2.add().setLongProp(753L);

        final ArrayValueIterator<MinimalPOJO> iterator3 = pojo.notEmptyDefaultArray();
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
                entry("notEmptyDefaultArray", "[{longProp=357}, {longProp=951}]"));
    }

    @Test
    public void shouldSerializePOJOWithDefaultValues()
    {
        // given
        final POJOArray pojo = new POJOArray();
        final ArrayValueIterator<MinimalPOJO> iterator1 = pojo.simpleArray();
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

            w.writeString(utf8("simpleArray"));
            w.writeArrayHeader(5);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(123L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(456L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(789L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(555L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(777L);
        });

        pojo.wrap(buffer);
        final ArrayValueIterator<MinimalPOJO> iterator = pojo.simpleArray();
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

            w.writeString(utf8("simpleArray"));
            w.writeArrayHeader(5);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(123L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(456L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(789L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(555L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(777L);
        });

        pojo.wrap(buffer);
        final ArrayValueIterator<MinimalPOJO> iterator = pojo.simpleArray();
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

            w.writeString(utf8("simpleArray"));
            w.writeArrayHeader(5);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(123L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(456L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(789L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(555L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(777L);
        });

        pojo.wrap(buffer);
        final ArrayValueIterator<MinimalPOJO> iterator = pojo.simpleArray();
        iterator.next();
        iterator.next();
        iterator.next();
        iterator.next();
        iterator.next();

        // when
        iterator.add().setLongProp(999L);

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

            w.writeString(utf8("simpleArray"));
            w.writeArrayHeader(5);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(123L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(456L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(789L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(555L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(777L);
        });

        pojo.wrap(buffer);
        final ArrayValueIterator<MinimalPOJO> iterator = pojo.simpleArray();
        iterator.next();
        iterator.next();
        iterator.next();

        // when
        iterator.add().setLongProp(999L);

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

            w.writeString(utf8("simpleArray"));
            w.writeArrayHeader(3);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(123L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(456L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(789L);

            w.writeString(utf8("emptyDefaultArray"));
            w.writeArrayHeader(1);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(753L);

            w.writeString(utf8("notEmptyDefaultArray"));
            w.writeArrayHeader(0);
        });

        // when
        pojo.wrap(buffer);

        // then
        final ArrayValueIterator<MinimalPOJO> iterator1 = pojo.simpleArray();
        assertThat(iterator1.hasNext()).isTrue();
        assertThat(iterator1.next().getLongProp()).isEqualTo(123L);
        assertThat(iterator1.hasNext()).isTrue();
        assertThat(iterator1.next().getLongProp()).isEqualTo(456L);
        assertThat(iterator1.hasNext()).isTrue();
        assertThat(iterator1.next().getLongProp()).isEqualTo(789L);
        assertThat(iterator1.hasNext()).isFalse();

        final ArrayValueIterator<MinimalPOJO> iterator2 = pojo.emptyDefaultArray();
        assertThat(iterator2.hasNext()).isTrue();
        assertThat(iterator2.next().getLongProp()).isEqualTo(753L);
        assertThat(iterator2.hasNext()).isFalse();

        final ArrayValueIterator<MinimalPOJO> iterator3 = pojo.notEmptyDefaultArray();
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

            w.writeString(utf8("simpleArray"));
            w.writeArrayHeader(3);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(123L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(456L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(789L);
        });

        // when
        pojo.wrap(buffer);

        // then
        final ArrayValueIterator<MinimalPOJO> iterator1 = pojo.simpleArray();
        assertThat(iterator1.hasNext()).isTrue();
        assertThat(iterator1.next().getLongProp()).isEqualTo(123L);
        assertThat(iterator1.hasNext()).isTrue();
        assertThat(iterator1.next().getLongProp()).isEqualTo(456L);
        assertThat(iterator1.hasNext()).isTrue();
        assertThat(iterator1.next().getLongProp()).isEqualTo(789L);
        assertThat(iterator1.hasNext()).isFalse();

        final ArrayValueIterator<MinimalPOJO> iterator2 = pojo.emptyDefaultArray();
        assertThat(iterator2.hasNext()).isFalse();

        final ArrayValueIterator<MinimalPOJO> iterator3 = pojo.notEmptyDefaultArray();
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
        exception.expectMessage("Property has no valid value");

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
        exception.expectMessage("Cannot write property; neither value, nor default value specified");

        // when
        pojo.write(buf, 0);
    }

    @Test
    public void shouldFailOnHasNextWithMissingRequiredValues()
    {
        // given
        final POJOArray pojo = new POJOArray();
        final ArrayValueIterator<MinimalPOJO> iterator = pojo.simpleArray();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Property has no valid value");

        // when
        iterator.hasNext();
    }

    @Test
    public void shouldFailOnNextWithMissingRequiredValues()
    {
        // given
        final POJOArray pojo = new POJOArray();
        final ArrayValueIterator<MinimalPOJO> iterator = pojo.simpleArray();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Property has no valid value");

        // when
        iterator.next();
    }

    @Test
    public void shouldFailOnInitialRemove()
    {
        // given
        final POJOArray pojo = new POJOArray();

        final DirectBuffer buffer = encodeMsgPack((w) ->
        {
            w.writeMapHeader(1);

            w.writeString(utf8("simpleArray"));
            w.writeArrayHeader(3);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(123L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(456L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(789L);
        });

        pojo.wrap(buffer);
        final ArrayValueIterator<MinimalPOJO> iterator = pojo.simpleArray();

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

            w.writeString(utf8("simpleArray"));
            w.writeArrayHeader(3);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(123L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(456L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(789L);
        });

        pojo.wrap(buffer);
        final ArrayValueIterator<MinimalPOJO> iterator = pojo.simpleArray();
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

            w.writeString(utf8("simpleArray"));
            w.writeArrayHeader(3);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(123L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(456L);

            w.writeMapHeader(1);
            w.writeString(utf8("longProp"));
            w.writeInteger(789L);
        });

        pojo.wrap(buffer);
        final ArrayValueIterator<MinimalPOJO> iterator = pojo.simpleArray();
        iterator.next();
        iterator.add().setLongProp(999L);

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
        final ArrayValueIterator<MinimalPOJO> iterator = pojo.simpleArray();

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
        final ArrayValueIterator<MinimalPOJO> iterator = pojo.emptyDefaultArray();

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
        final ArrayValueIterator<MinimalPOJO> iterator = pojo.notEmptyDefaultArray();

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
                entry("notEmptyDefaultArray", "[{longProp=741}]"));
    }

    @Test
    public void shouldAppendNewEntryToNotEmptyDefaultArrayProp()
    {
        // given
        final POJOArray pojo = new POJOArray();
        pojo.simpleArray().add().setLongProp(123L);
        final ArrayValueIterator<MinimalPOJO> iterator = pojo.notEmptyDefaultArray();

        // when
        while (iterator.hasNext())
        {
            iterator.next();
        }
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
                entry("notEmptyDefaultArray", "[{longProp=123}, {longProp=456}, {longProp=789}, {longProp=741}]"));
    }

    @Test
    public void shouldSetNotEmptyDefaultArrayPropToEmptyArray()
    {
        // given
        final POJOArray pojo = new POJOArray();
        pojo.simpleArray().add().setLongProp(123L);
        final ArrayValueIterator<MinimalPOJO> iterator = pojo.notEmptyDefaultArray();

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
                entry("notEmptyDefaultArray", "[]"));
    }

    @Test
    public void shouldRemoveEntriesFromNotEmptyDefaultArrayProp()
    {
        // given
        final POJOArray pojo = new POJOArray();
        pojo.simpleArray().add().setLongProp(123L);
        final ArrayValueIterator<MinimalPOJO> iterator = pojo.notEmptyDefaultArray();

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
                entry("notEmptyDefaultArray", "[{longProp=123}]"));
    }

}
