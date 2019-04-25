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
package io.zeebe.msgpack.spec;

import static io.zeebe.msgpack.spec.MsgPackType.ARRAY;
import static io.zeebe.msgpack.spec.MsgPackType.BINARY;
import static io.zeebe.msgpack.spec.MsgPackType.BOOLEAN;
import static io.zeebe.msgpack.spec.MsgPackType.FLOAT;
import static io.zeebe.msgpack.spec.MsgPackType.INTEGER;
import static io.zeebe.msgpack.spec.MsgPackType.MAP;
import static io.zeebe.msgpack.spec.MsgPackType.NIL;
import static io.zeebe.msgpack.spec.MsgPackType.STRING;
import static io.zeebe.msgpack.spec.MsgPackUtil.toByte;
import static io.zeebe.test.util.BufferAssert.assertThatBuffer;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MsgPackReadTokenTest {

  @Parameter(0)
  public String name;

  @Parameter(1)
  public Consumer<ByteArrayBuilder> given;

  @Parameter(2)
  public MsgPackType expectedType;

  @Parameter(3)
  public Consumer<MsgPackToken> assertion;

  @Parameters(name = "{0}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {
            "positive fixint",
            given((b) -> b.add(0x7f)),
            INTEGER,
            doAssert((t) -> assertThat(t.getIntegerValue()).isEqualTo(0x7fL))
          },
          {
            "fixmap",
            given((b) -> b.add(0x8f)),
            MAP,
            doAssert((t) -> assertThat(t.getSize()).isEqualTo(15))
          },
          {
            "fixarray",
            given((b) -> b.add(0x9f)),
            ARRAY,
            doAssert((t) -> assertThat(t.getSize()).isEqualTo(15))
          },
          {
            "fixstr",
            given((b) -> b.add(0xa1, 0x22)),
            STRING,
            doAssert((t) -> assertThatBuffer(t.getValueBuffer()).hasBytes(new byte[] {0x22}))
          },
          {"nil", given((b) -> b.add(0xc0)), NIL, doAssert((t) -> {})},
          {
            "false",
            given((b) -> b.add(0xc2)),
            BOOLEAN,
            doAssert((t) -> assertThat(t.getBooleanValue()).isFalse())
          },
          {
            "true",
            given((b) -> b.add(0xc3)),
            BOOLEAN,
            doAssert((t) -> assertThat(t.getBooleanValue()).isTrue())
          },
          {
            "bin 8",
            given((b) -> b.add(0xc4, 0x01, 0x22)),
            BINARY,
            doAssert((r) -> assertThatBuffer(r.getValueBuffer()).hasBytes(new byte[] {0x22}))
          },
          {
            "bin 16",
            given((b) -> b.add(0xc5, 0x00, 0x01, 0x22)),
            BINARY,
            doAssert((r) -> assertThatBuffer(r.getValueBuffer()).hasBytes(new byte[] {0x22}))
          },
          {
            "bin 32",
            given((b) -> b.add(0xc6, 0x00, 0x00, 0x00, 0x01, 0x22)),
            BINARY,
            doAssert((r) -> assertThatBuffer(r.getValueBuffer()).hasBytes(new byte[] {0x22}))
          },
          {
            "float 32",
            given((b) -> b.add(0xca).add(toByte(123123.12f))),
            FLOAT,
            doAssert((t) -> assertThat(t.getFloatValue()).isEqualTo(123123.12f))
          },
          {
            "float 64",
            given((b) -> b.add(0xcb).add(toByte(123123.123d))),
            FLOAT,
            doAssert((t) -> assertThat(t.getFloatValue()).isEqualTo(123123.123d))
          },
          {
            "uint 8",
            given((b) -> b.add(0xcc, 0xff)),
            INTEGER,
            doAssert((t) -> assertThat(t.getIntegerValue()).isEqualTo((1 << 8) - 1))
          },
          {
            "uint 16",
            given((b) -> b.add(0xcd, 0xff, 0xff)),
            INTEGER,
            doAssert((t) -> assertThat(t.getIntegerValue()).isEqualTo((1 << 16) - 1))
          },
          {
            "uint 32",
            given((b) -> b.add(0xce, 0xff, 0xff, 0xff, 0xff)),
            INTEGER,
            doAssert((t) -> assertThat(t.getIntegerValue()).isEqualTo((1L << 32) - 1))
          },
          {
            "uint 64",
            given((b) -> b.add(0xcf, 0x7f, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff)),
            INTEGER,
            doAssert((t) -> assertThat(t.getIntegerValue()).isEqualTo(Long.MAX_VALUE))
          },
          {
            "int 8",
            given((b) -> b.add(0xd0, 0x80)),
            INTEGER,
            doAssert((t) -> assertThat(t.getIntegerValue()).isEqualTo(Byte.MIN_VALUE))
          },
          {
            "int 16",
            given((b) -> b.add(0xd1, 0x80, 0x00)),
            INTEGER,
            doAssert((t) -> assertThat(t.getIntegerValue()).isEqualTo(Short.MIN_VALUE))
          },
          {
            "int 32",
            given((b) -> b.add(0xd2, 0x80, 0x00, 0x00, 0x00)),
            INTEGER,
            doAssert((t) -> assertThat(t.getIntegerValue()).isEqualTo(Integer.MIN_VALUE))
          },
          {
            "int 64",
            given((b) -> b.add(0xd3, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
            INTEGER,
            doAssert((t) -> assertThat(t.getIntegerValue()).isEqualTo(Long.MIN_VALUE))
          },
          {
            "str 8",
            given((b) -> b.add(0xd9, 0x01, 0x22)),
            STRING,
            doAssert((t) -> assertThatBuffer(t.getValueBuffer()).hasBytes(new byte[] {0x22}))
          },
          {
            "str 16",
            given((b) -> b.add(0xda, 0x00, 0x01, 0x22)),
            STRING,
            doAssert((t) -> assertThatBuffer(t.getValueBuffer()).hasBytes(new byte[] {0x22}))
          },
          {
            "str 32",
            given((b) -> b.add(0xdb, 0x00, 0x00, 0x00, 0x01, 0x22)),
            STRING,
            doAssert((t) -> assertThatBuffer(t.getValueBuffer()).hasBytes(new byte[] {0x22}))
          },
          {
            "array 16",
            given((b) -> b.add(0xdc, 0xff, 0xff)),
            ARRAY,
            doAssert((t) -> assertThat(t.getSize()).isEqualTo(0xffff))
          },
          {
            "array 32",
            given((b) -> b.add(0xdd, 0x7f, 0xff, 0xff, 0xff)),
            ARRAY,
            doAssert((t) -> assertThat(t.getSize()).isEqualTo(Integer.MAX_VALUE))
          },
          {
            "map 16",
            given((b) -> b.add(0xde, 0xff, 0xff)),
            MAP,
            doAssert((t) -> assertThat(t.getSize()).isEqualTo(0xffff))
          },
          {
            "map 32",
            given((b) -> b.add(0xdf, 0x00, 0xff, 0xff, 0xff)),
            MAP,
            doAssert((t) -> assertThat(t.getSize()).isEqualTo(0x00ff_ffff))
          },
          {
            "negative fixint",
            given((b) -> b.add(0xe0)),
            INTEGER,
            doAssert((t) -> assertThat(t.getIntegerValue()).isEqualTo(-32))
          },
          {
            "zero length fixstr",
            given((b) -> b.add(0xa0)),
            STRING,
            doAssert((t) -> assertThatBuffer(t.getValueBuffer()).hasCapacity(0))
          },
        });
  }

  @Test
  public void shouldReadToken() {
    // given
    final MsgPackReader reader = new MsgPackReader();
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    given.accept(builder);
    final DirectBuffer buf = new UnsafeBuffer(builder.value);
    reader.wrap(buf, 0, buf.capacity());

    // when
    final MsgPackToken msgPackToken = reader.readToken();

    // then
    assertThat(reader.getOffset()).isEqualTo(buf.capacity());
    assertThat(msgPackToken.getType()).isEqualTo(expectedType);
    assertion.accept(msgPackToken);
  }

  protected static Consumer<ByteArrayBuilder> given(Consumer<ByteArrayBuilder> arg) {
    return arg;
  }

  protected static Consumer<MsgPackToken> doAssert(Consumer<MsgPackToken> arg) {
    return arg;
  }
}
