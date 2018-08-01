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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class MsgPackWriterMiscTest {

  @Test
  public void testEncodedMapHeaderLength() {
    assertThat(MsgPackWriter.getEncodedMapHeaderLenght(0x0f)).isEqualTo(1);
    assertThat(MsgPackWriter.getEncodedMapHeaderLenght(0xffff)).isEqualTo(3);
    assertThat(MsgPackWriter.getEncodedMapHeaderLenght(0x7fff_ffff)).isEqualTo(5);
  }

  @Test
  public void testEncodedArayHeaderLength() {
    assertThat(MsgPackWriter.getEncodedArrayHeaderLenght(0x0f)).isEqualTo(1);
    assertThat(MsgPackWriter.getEncodedArrayHeaderLenght(0xffff)).isEqualTo(3);
    assertThat(MsgPackWriter.getEncodedArrayHeaderLenght(0x7fff_ffff)).isEqualTo(5);
  }

  @Test
  public void testEncodedBinaryValueLength() {
    assertThat(MsgPackWriter.getEncodedBinaryValueLength(0xff)).isEqualTo(2 + 0xff);
    assertThat(MsgPackWriter.getEncodedBinaryValueLength(0xffff)).isEqualTo(3 + 0xffff);
    assertThat(MsgPackWriter.getEncodedBinaryValueLength(0x7fff_fffa)).isEqualTo(5 + 0x7fff_fffa);
  }

  @Test
  public void testEncodedBooleanValueLength() {
    assertThat(MsgPackWriter.getEncodedBooleanValueLength()).isEqualTo(1);
  }

  @Test
  public void testEncodedLongValueLength() {
    assertThat(MsgPackWriter.getEncodedLongValueLength(0x7f)).isEqualTo(1);
    assertThat(MsgPackWriter.getEncodedLongValueLength(0xff)).isEqualTo(2);
    assertThat(MsgPackWriter.getEncodedLongValueLength(0xffff)).isEqualTo(3);
    assertThat(MsgPackWriter.getEncodedLongValueLength(0xffff_ffffL)).isEqualTo(5);
    assertThat(MsgPackWriter.getEncodedLongValueLength(0x7fff_ffff_ffff_ffffL)).isEqualTo(9);
    assertThat(MsgPackWriter.getEncodedLongValueLength(-0x20)).isEqualTo(1);
    assertThat(MsgPackWriter.getEncodedLongValueLength(Byte.MIN_VALUE)).isEqualTo(2);
    assertThat(MsgPackWriter.getEncodedLongValueLength(Short.MIN_VALUE)).isEqualTo(3);
    assertThat(MsgPackWriter.getEncodedLongValueLength(Integer.MIN_VALUE)).isEqualTo(5);
    assertThat(MsgPackWriter.getEncodedLongValueLength(Long.MIN_VALUE)).isEqualTo(9);
  }

  @Test
  public void testEncodedStringHeaderLength() {
    assertThat(MsgPackWriter.getEncodedStringHeaderLength(0x1f)).isEqualTo(1);
    assertThat(MsgPackWriter.getEncodedStringHeaderLength(0xff)).isEqualTo(2);
    assertThat(MsgPackWriter.getEncodedStringHeaderLength(0xffff)).isEqualTo(3);
    assertThat(MsgPackWriter.getEncodedStringHeaderLength(0x7fff_ffff)).isEqualTo(5);
  }

  @Test
  public void testEncodedStringLength() {
    assertThat(MsgPackWriter.getEncodedStringLength(0x1f)).isEqualTo(1 + 0x1f);
    assertThat(MsgPackWriter.getEncodedStringLength(0xff)).isEqualTo(2 + 0xff);
    assertThat(MsgPackWriter.getEncodedStringLength(0xffff)).isEqualTo(3 + 0xffff);
    assertThat(MsgPackWriter.getEncodedStringLength(0x7fff_fffa)).isEqualTo(5 + 0x7fff_fffa);
  }
}
