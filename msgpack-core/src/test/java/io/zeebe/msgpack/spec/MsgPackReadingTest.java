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

import static io.zeebe.msgpack.spec.MsgPackUtil.toByte;
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
public class MsgPackReadingTest {

  @Parameters(name = "{0}")
  public static Iterable<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {
            "positive fixint",
            given((b) -> b.add(0x7f)),
            doAssert((r) -> assertThat(r.readInteger()).isEqualTo(0x7fL))
          },
          {
            "fixmap",
            given((b) -> b.add(0x8f)),
            doAssert((r) -> assertThat(r.readMapHeader()).isEqualTo(15))
          },
          {
            "fixarray",
            given((b) -> b.add(0x9f)),
            doAssert((r) -> assertThat(r.readArrayHeader()).isEqualTo(15))
          },
          {
            "fixstr",
            given((b) -> b.add(0xbf)),
            doAssert((r) -> assertThat(r.readStringLength()).isEqualTo(31))
          },
          {
            "false",
            given((b) -> b.add(0xc2)),
            doAssert((r) -> assertThat(r.readBoolean()).isFalse())
          },
          {
            "true", given((b) -> b.add(0xc3)), doAssert((r) -> assertThat(r.readBoolean()).isTrue())
          },
          {
            "bin 8",
            given((b) -> b.add(0xc4, 0x7f)),
            doAssert((r) -> assertThat(r.readBinaryLength()).isEqualTo(Byte.MAX_VALUE))
          },
          {
            "bin 8 (> 127)",
            given((b) -> b.add(0xc4, 0xff)),
            doAssert((r) -> assertThat(r.readBinaryLength()).isEqualTo(255))
          },
          {
            "bin 16",
            given((b) -> b.add(0xc5, 0x7f, 0xff)),
            doAssert((r) -> assertThat(r.readBinaryLength()).isEqualTo(Short.MAX_VALUE))
          },
          {
            "bin 16 (> 32768)",
            given((b) -> b.add(0xc5, 0xFF, 0xFF)),
            doAssert((r) -> assertThat(r.readBinaryLength()).isEqualTo((1 << 16) - 1))
          },
          {
            "bin 32",
            given((b) -> b.add(0xc6, 0x7f, 0xff, 0xff, 0xff)),
            doAssert((r) -> assertThat(r.readBinaryLength()).isEqualTo(Integer.MAX_VALUE))
          },
          {
            /*
             * note: 123123.12f is not the same as 123123.12d; in fact, float
             * is not able to represent 123123.12 exactly but only a different value x
             * that requires the least amount of rounding (round-to-nearest rule).
             *
             * Double is able to represent the same exact value, but due to its higher precision,
             * 123123.12d != 123123.12f. For the same reason, Float.toString(123123.12f)
             * and Double.toString((double) 123123.12f) return different values (as toString always
             * prints as many digits required to uniquely identify the exact value).
             * (see Float#toString and Float#valueOf for details).
             */
            "float 32",
            given((b) -> b.add(0xca).add(toByte(123123.12f))),
            doAssert((r) -> assertThat(r.readFloat()).isEqualTo(123123.12f))
          },
          {
            "float 64",
            given((b) -> b.add(0xcb).add(toByte(123123.123d))),
            doAssert((r) -> assertThat(r.readFloat()).isEqualTo(123123.123d))
          },
          {
            "uint 8",
            given((b) -> b.add(0xcc, 0xff)),
            doAssert((r) -> assertThat(r.readInteger()).isEqualTo((1 << 8) - 1))
          },
          {
            "uint 16",
            given((b) -> b.add(0xcd, 0xff, 0xff)),
            doAssert((r) -> assertThat(r.readInteger()).isEqualTo((1 << 16) - 1))
          },
          {
            "uint 32",
            given((b) -> b.add(0xce, 0xff, 0xff, 0xff, 0xff)),
            doAssert((r) -> assertThat(r.readInteger()).isEqualTo((1L << 32) - 1))
          },
          {
            "uint 64",
            given((b) -> b.add(0xcf, 0x7f, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff)),
            doAssert((r) -> assertThat(r.readInteger()).isEqualTo(Long.MAX_VALUE))
          },
          {
            "int 8",
            given((b) -> b.add(0xd0, 0x80)),
            doAssert((r) -> assertThat(r.readInteger()).isEqualTo(Byte.MIN_VALUE))
          },
          {
            "int 16",
            given((b) -> b.add(0xd1, 0x80, 0x00)),
            doAssert((r) -> assertThat(r.readInteger()).isEqualTo(Short.MIN_VALUE))
          },
          {
            "int 32",
            given((b) -> b.add(0xd2, 0x80, 0x00, 0x00, 0x00)),
            doAssert((r) -> assertThat(r.readInteger()).isEqualTo(Integer.MIN_VALUE))
          },
          {
            "int 64",
            given((b) -> b.add(0xd3, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)),
            doAssert((r) -> assertThat(r.readInteger()).isEqualTo(Long.MIN_VALUE))
          },
          {
            "str 8",
            given((b) -> b.add(0xd9, 0xff)),
            doAssert((r) -> assertThat(r.readStringLength()).isEqualTo((1 << 8) - 1))
          },
          {
            "str 16",
            given((b) -> b.add(0xda, 0xff, 0xff)),
            doAssert((r) -> assertThat(r.readStringLength()).isEqualTo((1 << 16) - 1))
          },
          {
            "str 32",
            given((b) -> b.add(0xdb, 0x7f, 0xff, 0xff, 0xff)),
            doAssert((r) -> assertThat(r.readStringLength()).isEqualTo(Integer.MAX_VALUE))
          },
          {
            "array 16",
            given((b) -> b.add(0xdc, 0xff, 0xff)),
            doAssert((r) -> assertThat(r.readArrayHeader()).isEqualTo((1 << 16) - 1))
          },
          {
            "array 32",
            given((b) -> b.add(0xdd, 0x7f, 0xff, 0xff, 0xff)),
            doAssert((r) -> assertThat(r.readArrayHeader()).isEqualTo(Integer.MAX_VALUE))
          },
          {
            "map 16",
            given((b) -> b.add(0xde, 0xff, 0xff)),
            doAssert((r) -> assertThat(r.readMapHeader()).isEqualTo(0xffff))
          },
          {
            "map 32",
            given((b) -> b.add(0xdf, 0x7f, 0xff, 0xff, 0xff)),
            doAssert((r) -> assertThat(r.readMapHeader()).isEqualTo(Integer.MAX_VALUE))
          },
          {
            "negative fixint",
            given((b) -> b.add(0xe0)),
            doAssert((r) -> assertThat(r.readInteger()).isEqualTo(-32))
          },
          {
            "zero length fixstr",
            given((b) -> b.add(0xa0)),
            doAssert((r) -> assertThat(r.readStringLength()).isEqualTo(0))
          },
        });
  }

  @Parameter(0)
  public String name;

  @Parameter(1)
  public Consumer<ByteArrayBuilder> given;

  @Parameter(2)
  public Consumer<MsgPackReader> assertion;

  @Test
  public void shouldReadMsgPack() {
    // given
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    given.accept(builder);

    final byte[] givenBytes = builder.value;
    final DirectBuffer buf = new UnsafeBuffer(givenBytes);

    final MsgPackReader reader = new MsgPackReader();
    reader.wrap(buf, 0, buf.capacity());

    // when/then
    assertion.accept(reader);
    assertThat(reader.getOffset()).isEqualTo(givenBytes.length);
  }

  protected static Consumer<ByteArrayBuilder> given(Consumer<ByteArrayBuilder> arg) {
    return arg;
  }

  protected static Consumer<MsgPackReader> doAssert(Consumer<MsgPackReader> arg) {
    return arg;
  }
}
