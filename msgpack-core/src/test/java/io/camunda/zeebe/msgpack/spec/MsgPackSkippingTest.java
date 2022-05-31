/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.msgpack.spec;

import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.ARRAY16;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.ARRAY32;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.BIN16;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.BIN32;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.BIN8;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.EXT16;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.EXT32;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.EXT8;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.FALSE;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.FIXEXT1;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.FIXEXT16;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.FIXEXT2;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.FIXEXT4;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.FIXEXT8;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.FLOAT32;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.FLOAT64;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.INT16;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.INT32;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.INT64;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.INT8;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.MAP16;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.MAP32;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.NEGFIXINT_PREFIX;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.NIL;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.STR16;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.STR32;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.STR8;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.TRUE;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.UINT16;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.UINT32;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.UINT64;
import static io.camunda.zeebe.msgpack.spec.MsgPackCodes.UINT8;
import static io.camunda.zeebe.msgpack.spec.MsgPackUtil.toByte;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class MsgPackSkippingTest {

  @Parameter(0)
  public String name;

  @Parameter(1)
  public Consumer<ByteArrayBuilder> given;

  @Parameters(name = "{0}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {"positive fixint", given((b) -> b.add(0x7f))},
          {
            "fixmap", given((b) -> b.add(0x81, 0xc0, 0xc0)) // map entry: nil => nil
          },
          {
            "fixarray", given((b) -> b.add(0x91, 0xc0)) // array entry: nil
          },
          {"fixstr", given((b) -> b.add(0xa3).add(utf8("foo")))},
          {"nil", given((b) -> b.add(NIL))},
          {"false", given((b) -> b.add(FALSE))},
          {"true", given((b) -> b.add(TRUE))},
          {
            "bin 8", given((b) -> b.add(BIN8, 0x01, 0xff)) // length 1
          },
          {
            "bin 8 > 127",
            given(
                (b) -> {
                  final byte[] bytes = randomString(130);
                  bytes[0] = BIN8;
                  bytes[1] = (byte) 0x80; // length 128
                  b.add(bytes);
                })
          },
          {
            "bin 16", given((b) -> b.add(BIN16, 0x00, 0x01, 0xff)) // length 1
          },
          {
            "bin 16 > 2^15 - 1",
            given(
                (b) -> {
                  final byte[] bytes = randomString((1 << 15) + 3);
                  bytes[0] = BIN16;
                  bytes[1] = (byte) 0x80; // length 2^15 = 0x8000
                  bytes[2] = (byte) 0x00;
                  b.add(bytes);
                })
          },
          {
            "bin 32", given((b) -> b.add(BIN32, 0x00, 0x00, 0x00, 0x01, 0xff)) // length 1
          },
          {
            "ext 8", given((b) -> b.add(EXT8, 0x01, 0x00, 0xff)) // length 1
          },
          {
            "ext 8 > 127",
            given(
                (b) -> {
                  final byte[] bytes = randomString(131);
                  bytes[0] = EXT8;
                  bytes[1] = (byte) 0x80; // length 128
                  b.add(bytes);
                })
          },
          {
            "ext 16", given((b) -> b.add(EXT16, 0x00, 0x01, 0x00, 0xff)) // length 1
          },
          {
            "ext 16 > 2^15 - 1",
            given(
                (b) -> {
                  final byte[] bytes = randomString((1 << 15) + 4);
                  bytes[0] = EXT16;
                  bytes[1] = (byte) 0x80; // length 2^15 = 0x8000
                  bytes[2] = (byte) 0x00;
                  b.add(bytes);
                })
          },
          {
            "ext 32", given((b) -> b.add(EXT32, 0x00, 0x00, 0x00, 0x01, 0x00, 0xff)) // length 1
          },
          {"float 32", given((b) -> b.add(FLOAT32).add(toByte(123123.12f)))},
          {"float 64", given((b) -> b.add(FLOAT64).add(toByte(123123.123d)))},
          {"uint 8", given((b) -> b.add(UINT8, 0xff))},
          {"uint 16", given((b) -> b.add(UINT16, 0xff, 0xff))},
          {"uint 32", given((b) -> b.add(UINT32, 0xff, 0xff, 0xff, 0xff))},
          {"uint 64", given((b) -> b.add(UINT64, 0x7f, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff))},
          {"int 8", given((b) -> b.add(INT8, 0x80))},
          {"int 16", given((b) -> b.add(INT16, 0x80, 0x00))},
          {"int 32", given((b) -> b.add(INT32, 0x80, 0x00, 0x00, 0x00))},
          {"int 64", given((b) -> b.add(INT64, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))},
          {"fixext 1", given((b) -> b.add(FIXEXT1, 0x00, 0xff))},
          {"fixext 2", given((b) -> b.add(FIXEXT2, 0x00, 0xff, 0xff))},
          {"fixext 4", given((b) -> b.add(FIXEXT4, 0x00, 0xff, 0xff, 0xff, 0xff))},
          {"fixext 8", given((b) -> b.add(FIXEXT8, 0x00).add(new byte[8]))},
          {"fixext 16", given((b) -> b.add(FIXEXT16, 0x00).add(new byte[16]))},
          {
            "str 8", given((b) -> b.add(STR8, 0x01, 0xff)) // length 1
          },
          {
            "str 8 > 127",
            given(
                (b) -> {
                  final byte[] bytes = randomString(130);
                  bytes[0] = STR8;
                  bytes[1] = (byte) 0x80; // length 128
                  b.add(bytes);
                })
          },
          {
            "str 16", given((b) -> b.add(STR16, 0x00, 0x01, 0xff)) // length 1
          },
          {
            "str 16 > 2^15 - 1",
            given(
                (b) -> {
                  final byte[] bytes = randomString((1 << 15) + 3);
                  bytes[0] = STR16;
                  bytes[1] = (byte) 0x80; // length 2^15 = 0x8000
                  bytes[2] = (byte) 0x00;
                  b.add(bytes);
                })
          },
          {
            "str 32", given((b) -> b.add(STR32, 0x00, 0x00, 0x00, 0x01, 0xff)) // length 1
          },
          {
            "array 16", given((b) -> b.add(ARRAY16, 0x00, 0x01, 0xc0)) // length 1, value nil
          },
          {
            "array 16 > 2^15 - 1",
            given(
                (b) -> {
                  final byte[] bytes = randomString((1 << 15) + 3);
                  bytes[0] = ARRAY16;
                  bytes[1] = (byte) 0x80; // length 2^15 = 0x8000
                  bytes[2] = (byte) 0x00;
                  b.add(bytes);
                })
          },
          {
            "array 32",
            given((b) -> b.add(ARRAY32, 0x00, 0x00, 0x00, 0x01, 0xc0)) // length 1, value nil
          },
          {
            "map 16",
            given((b) -> b.add(MAP16, 0x00, 0x01, 0xc0, 0xc0)) // length 1, value nil => nil
          },
          {
            "map 16 > 2^15 - 1",
            given(
                (b) -> {
                  final byte[] bytes = randomString((1 << 16) + 3);
                  bytes[0] = MAP16;
                  bytes[1] = (byte) 0x80; // length 2^15 = 0x8000
                  bytes[2] = (byte) 0x00;
                  b.add(bytes);
                })
          },
          {
            "map 32",
            given(
                (b) ->
                    b.add(MAP32, 0x00, 0x00, 0x00, 0x01, 0xc0, 0xc0)) // length 1, value nil => nil
          },
          {"negative fixint", given((b) -> b.add(NEGFIXINT_PREFIX))}
        });
  }

  protected static Consumer<ByteArrayBuilder> given(final Consumer<ByteArrayBuilder> arg) {
    return arg;
  }

  @Test
  public void skipValue() {
    // given
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    given.accept(builder);

    final DirectBuffer buffer = new UnsafeBuffer(builder.value);

    final MsgPackReader reader = new MsgPackReader();
    reader.wrap(buffer, 0, buffer.capacity());

    // when
    reader.skipValue();

    // then
    assertThat(reader.getOffset()).isEqualTo(buffer.capacity());
  }

  protected static byte[] utf8(final String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] randomString(final int length) {
    return RandomStringUtils.randomAlphanumeric(length).getBytes();
  }
}
