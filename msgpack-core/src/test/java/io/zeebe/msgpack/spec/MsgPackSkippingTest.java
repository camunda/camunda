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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.internal.bytebuddy.utility.RandomString;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MsgPackSkippingTest {

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
          {"nil", given((b) -> b.add(0xc0))},
          {"false", given((b) -> b.add(0xc2))},
          {"true", given((b) -> b.add(0xc3))},
          {
            "bin 8", given((b) -> b.add(0xc4, 0x01, 0xff)) // length 1
          },
          {
            "bin 16", given((b) -> b.add(0xc5, 0x00, 0x01, 0xff)) // length 1
          },
          {
            "bin 32", given((b) -> b.add(0xc6, 0x00, 0x00, 0x00, 0x01, 0xff)) // length 1
          },
          {
            "ext 8", given((b) -> b.add(0xc7, 0x01, 0x00, 0xff)) // length 1
          },
          {
            "ext 16", given((b) -> b.add(0xc8, 0x00, 0x01, 0x00, 0xff)) // length 1
          },
          {
            "ext 32", given((b) -> b.add(0xc9, 0x00, 0x00, 0x00, 0x01, 0x00, 0xff)) // length 1
          },
          {"float 32", given((b) -> b.add(0xca).add(toByte(123123.12f)))},
          {"float 64", given((b) -> b.add(0xcb).add(toByte(123123.123d)))},
          {"uint 8", given((b) -> b.add(0xcc, 0xff))},
          {"uint 16", given((b) -> b.add(0xcd, 0xff, 0xff))},
          {"uint 32", given((b) -> b.add(0xce, 0xff, 0xff, 0xff, 0xff))},
          {"uint 64", given((b) -> b.add(0xcf, 0x7f, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff))},
          {"int 8", given((b) -> b.add(0xd0, 0x80))},
          {"int 16", given((b) -> b.add(0xd1, 0x80, 0x00))},
          {"int 32", given((b) -> b.add(0xd2, 0x80, 0x00, 0x00, 0x00))},
          {"int 64", given((b) -> b.add(0xd3, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))},
          {"fixext 1", given((b) -> b.add(0xd4, 0x00, 0xff))},
          {"fixext 2", given((b) -> b.add(0xd5, 0x00, 0xff, 0xff))},
          {"fixext 4", given((b) -> b.add(0xd6, 0x00, 0xff, 0xff, 0xff, 0xff))},
          {"fixext 8", given((b) -> b.add(0xd7, 0x00).add(new byte[8]))},
          {"fixext 16", given((b) -> b.add(0xd8, 0x00).add(new byte[16]))},
          {
            "str 8", given((b) -> b.add(0xd9, 0x01, 0xff)) // length 1
          },
          {
            "str 8 > 127",
            given(
                (b) -> {
                  final byte[] bytes = RandomString.make(130).getBytes();
                  bytes[0] = (byte) 0xd9;
                  bytes[1] = (byte) 0x80; // length 128
                  b.add(bytes);
                })
          },
          {
            "str 16", given((b) -> b.add(0xda, 0x00, 0x01, 0xff)) // length 1
          },
          {
            "str 16 > 2^15 - 1",
            given(
                (b) -> {
                  final byte[] bytes = RandomString.make((1 << 15) + 3).getBytes();
                  bytes[0] = (byte) 0xda;
                  bytes[1] = (byte) 0x80; // length 2^15 = 0x8000
                  bytes[2] = (byte) 0x00;
                  b.add(bytes);
                })
          },
          {
            "str 32", given((b) -> b.add(0xdb, 0x00, 0x00, 0x00, 0x01, 0xff)) // length 1
          },
          {
            "array 16", given((b) -> b.add(0xdc, 0x00, 0x01, 0xc0)) // length 1, value nil
          },
          {
            "array 32",
            given((b) -> b.add(0xdd, 0x00, 0x00, 0x00, 0x01, 0xc0)) // length 1, value nil
          },
          {
            "map 16",
            given((b) -> b.add(0xde, 0x00, 0x01, 0xc0, 0xc0)) // length 1, value nil => nil
          },
          {
            "map 32",
            given(
                (b) ->
                    b.add(0xdf, 0x00, 0x00, 0x00, 0x01, 0xc0, 0xc0)) // length 1, value nil => nil
          },
          {"negative fixint", given((b) -> b.add(0xe0))}
        });
  }

  @Parameter(0)
  public String name;

  @Parameter(1)
  public Consumer<ByteArrayBuilder> given;

  protected static Consumer<ByteArrayBuilder> given(Consumer<ByteArrayBuilder> arg) {
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

  protected static byte[] utf8(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
