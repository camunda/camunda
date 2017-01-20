package org.camunda.tngp.broker.util.msgpack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.broker.util.msgpack.POJO.POJOEnum;
import org.camunda.tngp.msgpack.spec.MsgPackReader;
import org.camunda.tngp.msgpack.spec.MsgPackWriter;
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
        pojo.setString(BUF1);
        pojo.setBinary(BUF2);
        pojo.setPacked(MSGPACK_BUF);

        final int writeLength = pojo.getLength();

        // when
        final UnsafeBuffer resultBuffer = new UnsafeBuffer(new byte[writeLength]);
        pojo.write(resultBuffer, 0);

        // then
        final MsgPackReader reader = new MsgPackReader();
        reader.wrap(resultBuffer, 0, resultBuffer.capacity());
        assertThat(reader.readMapHeader()).isEqualTo(5);

        // this is stricter as necessary: a hard order of properties is not required
        assertThatBuffer(reader.readToken().getValueBuffer()).hasBytes(utf8("enumProp"));
        assertThatBuffer(reader.readToken().getValueBuffer()).hasBytes(utf8(POJOEnum.BAR.toString()));

        assertThatBuffer(reader.readToken().getValueBuffer()).hasBytes(utf8("longProp"));
        assertThat(reader.readToken().getIntegerValue()).isEqualTo(456456L);

        assertThatBuffer(reader.readToken().getValueBuffer()).hasBytes(utf8("stringProp"));
        assertThatBuffer(reader.readToken().getValueBuffer()).hasBytes(BUF1.byteArray());

        assertThatBuffer(reader.readToken().getValueBuffer()).hasBytes(utf8("binaryProp"));
        assertThatBuffer(reader.readToken().getValueBuffer()).hasBytes(BUF2.byteArray());

        assertThatBuffer(reader.readToken().getValueBuffer()).hasBytes(utf8("packedProp"));
        assertThat(reader.readMapHeader()).isEqualTo(1);
        assertThatBuffer(reader.readToken().getValueBuffer()).hasBytes(BUF1.byteArray());
        assertThat(reader.readToken().getIntegerValue()).isEqualTo(123123L);
    }

    @Test
    public void shouldDeserializePOJO()
    {
        // given
        final POJO pojo = new POJO();

        final DirectBuffer buffer = encodeMsgPack((w) ->
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
        });

        // when
        pojo.wrap(buffer);

        // then
        assertThat(pojo.getEnum()).isEqualByComparingTo(POJOEnum.BAR);
        assertThat(pojo.getLong()).isEqualTo(88888L);
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



    protected static DirectBuffer utf8(String value)
    {
        return new UnsafeBuffer(value.getBytes(StandardCharsets.UTF_8));
    }

    protected static MutableDirectBuffer encodeMsgPack(Consumer<MsgPackWriter> arg)
    {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024]);
        final MsgPackWriter writer = new MsgPackWriter();
        writer.wrap(buffer, 0);
        arg.accept(writer);
        buffer.wrap(buffer, 0, writer.getOffset());
        return buffer;
    }

}
