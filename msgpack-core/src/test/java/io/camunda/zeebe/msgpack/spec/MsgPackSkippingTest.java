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
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Execution(ExecutionMode.CONCURRENT)
final class MsgPackSkippingTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("provideTestCases")
  void skipValue(
      @SuppressWarnings("unused") final String testName, final Consumer<ByteArrayBuilder> given) {
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

  private static Consumer<ByteArrayBuilder> given(final Consumer<ByteArrayBuilder> arg) {
    return arg;
  }

  private static Stream<Arguments> provideTestCases() {
    return Stream.of(
        Arguments.of("positive fixint", given((b) -> b.add(0x7f))),
        Arguments.of(
            "fixmap", given((b) -> b.add(0x81, 0xc0, 0xc0)) // map entry: nil => nil
            ),
        Arguments.of(
            "fixarray", given((b) -> b.add(0x91, 0xc0)) // array entry: nil
            ),
        Arguments.of(
            "fixstr", given((b) -> b.add(0xa3).add("foo".getBytes(StandardCharsets.UTF_8)))),
        Arguments.of("nil", given((b) -> b.add(NIL))),
        Arguments.of("false", given((b) -> b.add(FALSE))),
        Arguments.of("true", given((b) -> b.add(TRUE))),
        Arguments.of(
            "bin 8", given((b) -> b.add(BIN8, 0x01, 0xff)) // length 1
            ),
        Arguments.of(
            "bin 8 > 127",
            given(
                (b) -> {
                  final byte[] bytes = randomString(130);
                  bytes[0] = BIN8;
                  bytes[1] = (byte) 0x80; // length 128
                  b.add(bytes);
                })),
        Arguments.of(
            "bin 16", given((b) -> b.add(BIN16, 0x00, 0x01, 0xff)) // length 1
            ),
        Arguments.of(
            "bin 16 > 2^15 - 1",
            given(
                (b) -> {
                  final byte[] bytes = randomString((1 << 15) + 3);
                  bytes[0] = BIN16;
                  bytes[1] = (byte) 0x80; // length 2^15 = 0x8000
                  bytes[2] = (byte) 0x00;
                  b.add(bytes);
                })),
        Arguments.of(
            "bin 32", given((b) -> b.add(BIN32, 0x00, 0x00, 0x00, 0x01, 0xff)) // length 1
            ),
        Arguments.of(
            "ext 8", given((b) -> b.add(EXT8, 0x01, 0x00, 0xff)) // length 1
            ),
        Arguments.of(
            "ext 8 > 127",
            given(
                (b) -> {
                  final byte[] bytes = randomString(131);
                  bytes[0] = EXT8;
                  bytes[1] = (byte) 0x80; // length 128
                  b.add(bytes);
                })),
        Arguments.of(
            "ext 16", given((b) -> b.add(EXT16, 0x00, 0x01, 0x00, 0xff)) // length 1
            ),
        Arguments.of(
            "ext 16 > 2^15 - 1",
            given(
                (b) -> {
                  final byte[] bytes = randomString((1 << 15) + 4);
                  bytes[0] = EXT16;
                  bytes[1] = (byte) 0x80; // length 2^15 = 0x8000
                  bytes[2] = (byte) 0x00;
                  b.add(bytes);
                })),
        Arguments.of(
            "ext 32", given((b) -> b.add(EXT32, 0x00, 0x00, 0x00, 0x01, 0x00, 0xff)) // length 1
            ),
        Arguments.of("float 32", given((b) -> b.add(FLOAT32).add(toByte(123123.12f)))),
        Arguments.of("float 64", given((b) -> b.add(FLOAT64).add(toByte(123123.123d)))),
        Arguments.of("uint 8", given((b) -> b.add(UINT8, 0xff))),
        Arguments.of("uint 16", given((b) -> b.add(UINT16, 0xff, 0xff))),
        Arguments.of("uint 32", given((b) -> b.add(UINT32, 0xff, 0xff, 0xff, 0xff))),
        Arguments.of(
            "uint 64", given((b) -> b.add(UINT64, 0x7f, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff))),
        Arguments.of("int 8", given((b) -> b.add(INT8, 0x80))),
        Arguments.of("int 16", given((b) -> b.add(INT16, 0x80, 0x00))),
        Arguments.of("int 32", given((b) -> b.add(INT32, 0x80, 0x00, 0x00, 0x00))),
        Arguments.of(
            "int 64", given((b) -> b.add(INT64, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))),
        Arguments.of("fixext 1", given((b) -> b.add(FIXEXT1, 0x00, 0xff))),
        Arguments.of("fixext 2", given((b) -> b.add(FIXEXT2, 0x00, 0xff, 0xff))),
        Arguments.of("fixext 4", given((b) -> b.add(FIXEXT4, 0x00, 0xff, 0xff, 0xff, 0xff))),
        Arguments.of("fixext 8", given((b) -> b.add(FIXEXT8, 0x00).add(new byte[8]))),
        Arguments.of("fixext 16", given((b) -> b.add(FIXEXT16, 0x00).add(new byte[16]))),
        Arguments.of(
            "str 8", given((b) -> b.add(STR8, 0x01, 0xff)) // length 1
            ),
        Arguments.of(
            "str 8 > 127",
            given(
                (b) -> {
                  final byte[] bytes = randomString(130);
                  bytes[0] = STR8;
                  bytes[1] = (byte) 0x80; // length 128
                  b.add(bytes);
                })),
        Arguments.of(
            "str 16", given((b) -> b.add(STR16, 0x00, 0x01, 0xff)) // length 1
            ),
        Arguments.of(
            "str 16 > 2^15 - 1",
            given(
                (b) -> {
                  final byte[] bytes = randomString((1 << 15) + 3);
                  bytes[0] = STR16;
                  bytes[1] = (byte) 0x80; // length 2^15 = 0x8000
                  bytes[2] = (byte) 0x00;
                  b.add(bytes);
                })),
        Arguments.of(
            "str 32", given((b) -> b.add(STR32, 0x00, 0x00, 0x00, 0x01, 0xff)) // length 1
            ),
        Arguments.of(
            "array 16", given((b) -> b.add(ARRAY16, 0x00, 0x01, 0xc0)) // length 1, value nil
            ),
        Arguments.of(
            "array 16 > 2^15 - 1",
            given(
                (b) -> {
                  final byte[] bytes = randomString((1 << 15) + 3);
                  bytes[0] = ARRAY16;
                  bytes[1] = (byte) 0x80; // length 2^15 = 0x8000
                  bytes[2] = (byte) 0x00;
                  b.add(bytes);
                })),
        Arguments.of(
            "array 32",
            given((b) -> b.add(ARRAY32, 0x00, 0x00, 0x00, 0x01, 0xc0)) // length 1, value nil
            ),
        Arguments.of(
            "map 16",
            given((b) -> b.add(MAP16, 0x00, 0x01, 0xc0, 0xc0)) // length 1, value nil => nil
            ),
        Arguments.of(
            "map 16 > 2^15 - 1",
            given(
                (b) -> {
                  final byte[] bytes = randomString((1 << 16) + 3);
                  bytes[0] = MAP16;
                  bytes[1] = (byte) 0x80; // length 2^15 = 0x8000
                  bytes[2] = (byte) 0x00;
                  b.add(bytes);
                })),
        Arguments.of(
            "map 32",
            given(
                (b) ->
                    b.add(MAP32, 0x00, 0x00, 0x00, 0x01, 0xc0, 0xc0)) // length 1, value nil => nil
            ),
        Arguments.of("negative fixint", given((b) -> b.add(NEGFIXINT_PREFIX))));
  }

  private static byte[] randomString(final int length) {
    return RandomStringUtils.randomAlphanumeric(length).getBytes();
  }
}
