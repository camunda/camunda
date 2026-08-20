/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.spec;

import static io.camunda.zeebe.msgpack.spec.MsgPackUtil.toByte;
import static io.camunda.zeebe.test.util.BufferAssert.assertThatBuffer;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackUtil.CheckedConsumer;
import io.camunda.zeebe.msgpack.spec.MsgPackUtil.CheckedToIntFunction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class MsgPackWriterTest {

  protected static final long LONG_POS_5_BIT = longOfLength(5);
  protected static final long LONG_NEG_5_BIT = -longOfLength(5);
  protected static final DirectBuffer BYTE_5 = toBuffer("foooo");
  protected static final DirectBuffer BYTE_32 = toBuffer("aaaa_aaaa_aaaa_aaaa_aaaa_aaaa_aa");
  protected static final int BUFFER_CAPACITY = 1024;
  protected static final int WRITE_OFFSET = 123;

  @Parameter(0)
  public String name;

  @Parameter(1)
  public CheckedToIntFunction<MsgPackWriter> actualValueWriter;

  @Parameter(2)
  public CheckedConsumer<ByteArrayBuilder> expectedValueWriter;

  protected final MutableDirectBuffer actualValueBuffer =
      new UnsafeBuffer(new byte[BUFFER_CAPACITY]);

  @Parameters(name = "{0}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {
            "positive fixint",
            actual((w) -> w.writeInteger(LONG_POS_5_BIT)),
            expect((b) -> b.add((byte) LONG_POS_5_BIT))
          },
          {"fixmap", actual((w) -> w.writeMapHeader(15)), expect((b) -> b.add(0x8f))},
          {"fixarray", actual((w) -> w.writeArrayHeader(15)), expect((b) -> b.add(0x9f))},
          {
            "fixstr",
            actual((w) -> w.writeString(toBuffer("foo"))),
            expect((b) -> b.add(0xa3).add(utf8("foo")))
          },
          {"nil", actual((w) -> w.writeNil()), expect((b) -> b.add(0xc0))},
          {"false", actual((w) -> w.writeBoolean(false)), expect((b) -> b.add(0xc2))},
          {"true", actual((w) -> w.writeBoolean(true)), expect((b) -> b.add(0xc3))},
          {"bin 8", actual((w) -> w.writeBinaryHeader(0xff)), expect((b) -> b.add(0xc4, 0xff))},
          {
            "bin 8 with data",
            actual((w) -> w.writeBinary(BYTE_5)),
            expect((b) -> b.add(0xc4, 5).add(BYTE_5.byteArray()))
          },
          {
            "bin 16",
            actual((w) -> w.writeBinaryHeader(0xffff)),
            expect((b) -> b.add(0xc5, 0xff, 0xff))
          },
          {
            "bin 32",
            actual((w) -> w.writeBinaryHeader(0x7fff_ffff)),
            expect((b) -> b.add(0xc6, 0x7f, 0xff, 0xff, 0xff))
          },
          {
            "float 32",
            actual((w) -> w.writeFloat(123.0d)),
            expect((b) -> b.add(0xca).add(toByte(123.0f)))
          },
          {
            "float 64",
            actual((w) -> w.writeFloat(Double.MAX_VALUE)),
            expect((b) -> b.add(0xcb).add(toByte(Double.MAX_VALUE)))
          },
          {"uint 8", actual((w) -> w.writeInteger((1 << 8) - 1)), expect((b) -> b.add(0xcc, 0xff))},
          {
            "uint 16",
            actual((w) -> w.writeInteger((1 << 16) - 1)),
            expect((b) -> b.add(0xcd, 0xff, 0xff))
          },
          {
            "uint 32",
            actual((w) -> w.writeInteger((1L << 32) - 1)),
            expect((b) -> b.add(0xce, 0xff, 0xff, 0xff, 0xff))
          },
          {
            "uint 64",
            actual((w) -> w.writeInteger(Long.MAX_VALUE)),
            expect((b) -> b.add(0xcf, 0x7f, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff))
          },
          {"int 8", actual((w) -> w.writeInteger(-(1 << 7))), expect((b) -> b.add(0xd0, 0x80))},
          {
            "int 16",
            actual((w) -> w.writeInteger(-(1 << 15))),
            expect((b) -> b.add(0xd1, 0x80, 0x00))
          },
          {
            "int 32",
            actual((w) -> w.writeInteger(-(1 << 31))),
            expect((b) -> b.add(0xd2, 0x80, 0x00, 0x00, 0x00))
          },
          {
            "int 64",
            actual((w) -> w.writeInteger(-(1L << 63))),
            expect((b) -> b.add(0xd3, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
          },
          {"str 8", actual((w) -> w.writeStringHeader(0xff)), expect((b) -> b.add(0xd9, 0xff))},
          {
            "str 8 with data",
            actual((w) -> w.writeString(BYTE_32)),
            expect((b) -> b.add(0xd9, 0x20).add(BYTE_32.byteArray()))
          },
          {
            "str 16",
            actual((w) -> w.writeStringHeader(0xffff)),
            expect((b) -> b.add(0xda, 0xff, 0xff))
          },
          {
            "str 32",
            actual((w) -> w.writeStringHeader(0x7fff_ffff)),
            expect((b) -> b.add(0xdb, 0x7f, 0xff, 0xff, 0xff))
          },
          {
            "array 16",
            actual((w) -> w.writeArrayHeader(0xffff)),
            expect((b) -> b.add(0xdc, 0xff, 0xff))
          },
          {
            "array 32",
            actual((w) -> w.writeArrayHeader(0x7fff_ffff)),
            expect((b) -> b.add(0xdd, 0x7f, 0xff, 0xff, 0xff))
          },
          {
            "map 16",
            actual((w) -> w.writeMapHeader(0xffff)),
            expect((b) -> b.add(0xde, 0xff, 0xff))
          },
          {
            "map 32",
            actual((w) -> w.writeMapHeader(0x7fff_ffff)),
            expect((b) -> b.add(0xdf, 0x7f, 0xff, 0xff, 0xff))
          },
          {
            "negative fixint",
            actual((w) -> w.writeInteger(LONG_NEG_5_BIT)),
            expect((b) -> b.add((byte) LONG_NEG_5_BIT))
          },
        });
  }

  @Before
  public void setUp() {
    Arrays.fill(actualValueBuffer.byteArray(), (byte) 0);
  }

  @Test
  public void testWriteMessage() throws Exception {
    // given
    final MsgPackWriter writer = new MsgPackWriter();
    writer.wrap(actualValueBuffer, WRITE_OFFSET);

    final ByteArrayBuilder builder = new ByteArrayBuilder();
    expectedValueWriter.accept(builder);
    final byte[] expectedValue = builder.value;

    // when
    final var len = actualValueWriter.apply(writer);

    // then
    assertThat(writer.getOffset()).isEqualTo(WRITE_OFFSET + expectedValue.length);
    assertThatBuffer(actualValueBuffer).hasBytes(expectedValue, WRITE_OFFSET);
    assertThat(len).isEqualTo(expectedValue.length);
  }

  // helping the compiler with recognizing lamdas
  protected static CheckedConsumer<ByteArrayBuilder> expect(
      final CheckedConsumer<ByteArrayBuilder> arg) {
    return arg;
  }

  protected static CheckedToIntFunction<MsgPackWriter> actual(
      final CheckedToIntFunction<MsgPackWriter> arg) {
    return arg;
  }

  protected static long longOfLength(final int bits) {
    return 1L << (bits - 1);
  }

  protected static DirectBuffer toBuffer(final String value) {
    return new UnsafeBuffer(value.getBytes(StandardCharsets.UTF_8));
  }

  protected static byte[] utf8(final String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
