package org.camunda.tngp.broker.util.msgpack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;
import static org.camunda.tngp.broker.util.msgpack.MsgPackUtil.encodeMsgPack;
import static org.camunda.tngp.broker.util.msgpack.MsgPackUtil.utf8;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.util.msgpack.POJO.POJOEnum;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ObjectMappingTest
{
    public static final DirectBuffer BUF1 = new UnsafeBuffer("foo".getBytes(StandardCharsets.UTF_8));
    public static final DirectBuffer BUF2 = new UnsafeBuffer("bar".getBytes(StandardCharsets.UTF_8));
    public static final MutableDirectBuffer MSGPACK_BUF;

    static
    {
        MSGPACK_BUF = encodeMsgPack((w) ->
        {
            w.writeMapHeader(1);
            w.writeString(BUF1);
            w.writeInteger(123123L);
        });
    }

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldSerializePOJO()
    {
        // given
        final POJO pojo = new POJO();
        pojo.setEnum(POJOEnum.BAR);
        pojo.setLong(456456L);
        pojo.setInt(123);
        pojo.setString(BUF1);
        pojo.setBinary(BUF2);
        pojo.setPacked(MSGPACK_BUF);

        final int writeLength = pojo.getLength();

        // when
        final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[writeLength]);
        pojo.write(resultBuffer, 0);

        // then
        final Map<String, Object> msgPackMap = MsgPackUtil.asMap(resultBuffer, 0, resultBuffer.capacity());
        assertThat(msgPackMap).hasSize(6);
        assertThat(msgPackMap).contains(
                entry("enumProp", POJOEnum.BAR.toString()),
                entry("longProp", 456456L),
                entry("intProp", 123L),
                entry("stringProp", "foo"),
                entry("binaryProp", BUF2.byteArray())
        );

        @SuppressWarnings("unchecked")
        final Map<String, Object> packedProp = (Map<String, Object>) msgPackMap.get("packedProp");
        assertThat(packedProp).containsExactly(entry("foo", 123123L));
    }

    @Test
    public void shouldDeserializePOJO()
    {
        // given
        final POJO pojo = new POJO();

        final DirectBuffer buffer = encodeMsgPack((w) ->
        {
            w.writeMapHeader(6);

            w.writeString(utf8("enumProp"));
            w.writeString(utf8(POJOEnum.BAR.toString()));

            w.writeString(utf8("binaryProp"));
            w.writeBinary(BUF1);

            w.writeString(utf8("stringProp"));
            w.writeString(BUF2);

            w.writeString(utf8("packedProp"));
            w.writeRaw(MSGPACK_BUF);

            w.writeString(utf8("longProp"));
            w.writeInteger(88888L);

            w.writeString(utf8("intProp"));
            w.writeInteger(123L);
        });

        // when
        pojo.wrap(buffer);

        // then
        assertThat(pojo.getEnum()).isEqualByComparingTo(POJOEnum.BAR);
        assertThat(pojo.getLong()).isEqualTo(88888L);
        assertThat(pojo.getInt()).isEqualTo(123);
        assertThatBuffer(pojo.getPacked()).hasBytes(MSGPACK_BUF);
        assertThatBuffer(pojo.getBinary()).hasBytes(BUF1);
        assertThatBuffer(pojo.getString()).hasBytes(BUF2);
    }


    @Test
    public void shouldNotDeserializePOJOWithWrongValueType()
    {
        // given
        final POJO pojo = new POJO();

        final DirectBuffer buffer = encodeMsgPack((w) ->
        {
            w.writeMapHeader(1);

            w.writeString(utf8("stringProp"));
            w.writeFloat(123123.123123d);
        });

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Could not deserialize object. Deserialization stuck at offset 13");

        // when
        pojo.wrap(buffer);

    }

    @Test
    public void shouldNotDeserializePOJOWithWrongKeyType()
    {
        // given
        final POJO pojo = new POJO();

        final DirectBuffer buffer = encodeMsgPack((w) ->
        {
            w.writeMapHeader(1);

            w.writeInteger(123123L);
            w.writeFloat(123123.123123d);
        });

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Could not deserialize object. Deserialization stuck at offset 2");

        // when
        pojo.wrap(buffer);
    }

    @Test
    public void shouldNotDeserializePOJOFromNonMap()
    {
        // given
        final POJO pojo = new POJO();

        final DirectBuffer buffer = encodeMsgPack((w) ->
        {
            w.writeString(utf8("stringProp"));
            w.writeFloat(123123.123123d);
        });

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Could not deserialize object. Deserialization stuck at offset 1");

        // when
        pojo.wrap(buffer);
    }

    @Test
    public void shouldFailDeserializationWithMissingRequiredValues()
    {
        // given
        final POJO pojo = new POJO();

        final DirectBuffer buf1 = encodeMsgPack((w) -> w.writeMapHeader(0));
        pojo.wrap(buf1);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Property has no valid value");

        // when
        pojo.getLong();
    }

    @Test
    public void shouldFailDeserializationWithOversizedIntegerValue()
    {
        // given
        final POJO pojo = new POJO();

        final DirectBuffer buffer = encodeMsgPack((w) ->
        {
            w.writeMapHeader(1);

            w.writeString(utf8("intProp"));
            w.writeInteger(Integer.MAX_VALUE + 1L);
        });

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Could not deserialize object.");

        // when
        pojo.wrap(buffer);
    }

    @Test
    public void shouldFailDeserializationWithUndersizedIntegerValue()
    {
        // given
        final POJO pojo = new POJO();

        final DirectBuffer buffer = encodeMsgPack((w) ->
        {
            w.writeMapHeader(6);

            w.writeString(utf8("enumProp"));
            w.writeString(utf8(POJOEnum.BAR.toString()));

            w.writeString(utf8("binaryProp"));
            w.writeBinary(BUF1);

            w.writeString(utf8("stringProp"));
            w.writeString(BUF2);

            w.writeString(utf8("packedProp"));
            w.writeRaw(MSGPACK_BUF);

            w.writeString(utf8("longProp"));
            w.writeInteger(88888L);

            w.writeString(utf8("intProp"));
            w.writeInteger(Integer.MIN_VALUE - 1L);
        });

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Could not deserialize object.");

        // when
        pojo.wrap(buffer);
    }

    @Test
    public void shouldFailSerializationWithMissingRequiredValues()
    {
        // given
        final POJO pojo = new POJO();

        final UnsafeBuffer buf = new UnsafeBuffer(new byte[1024]);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Cannot write property; neither value, nor default value specified");

        // when
        pojo.write(buf, 0);
    }

    @Test
    public void shouldFailLengthEstimationWithMissingRequiredValues()
    {
        // given
        final POJO pojo = new POJO();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Property has no valid value");

        // when
        pojo.getLength();
    }

    public void shouldDeserializeWithReusedPOJO()
    {
        // given
        final POJO pojo = new POJO();

        final DirectBuffer buf1 = encodeMsgPack((w) ->
        {
            w.writeMapHeader(5);

            w.writeString(utf8("enumProp"));
            w.writeString(utf8(POJOEnum.BAR.toString()));

            w.writeString(utf8("binaryProp"));
            w.writeBinary(BUF1);

            w.writeString(utf8("stringProp"));
            w.writeString(BUF2);

            w.writeString(utf8("packedProp"));
            w.writeRaw(MSGPACK_BUF);

            w.writeString(utf8("longProp"));
            w.writeInteger(88888L);

            w.writeString(utf8("intProp"));
            w.writeInteger(123);
        });
        pojo.wrap(buf1);

        final DirectBuffer buf2 = encodeMsgPack((w) ->
        {
            w.writeMapHeader(5);

            w.writeString(utf8("enumProp"));
            w.writeString(utf8(POJOEnum.FOO.toString()));

            w.writeString(utf8("binaryProp"));
            w.writeBinary(BUF2);

            w.writeString(utf8("stringProp"));
            w.writeString(BUF1);

            w.writeString(utf8("packedProp"));
            w.writeRaw(MSGPACK_BUF);

            w.writeString(utf8("longProp"));
            w.writeInteger(7777L);

            w.writeString(utf8("intProp"));
            w.writeInteger(456);
        });

        // when
        pojo.reset();
        pojo.wrap(buf2);

        // then
        assertThat(pojo.getEnum()).isEqualByComparingTo(POJOEnum.FOO);
        assertThat(pojo.getLong()).isEqualTo(7777L);
        assertThat(pojo.getInt()).isEqualTo(456);
        assertThatBuffer(pojo.getPacked()).hasBytes(MSGPACK_BUF);
        assertThatBuffer(pojo.getBinary()).hasBytes(BUF2);
        assertThatBuffer(pojo.getString()).hasBytes(BUF1);
    }
}
