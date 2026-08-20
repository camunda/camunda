/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public final class MsgPackWriterMiscTest {

  @Test
  public void testEncodedMapHeaderLength() {
    assertThat(MsgPackWriter.getEncodedMapHeaderLength(0x0f)).isEqualTo(1);
    assertThat(MsgPackWriter.getEncodedMapHeaderLength(0xffff)).isEqualTo(3);
    assertThat(MsgPackWriter.getEncodedMapHeaderLength(0x7fff_ffff)).isEqualTo(5);
  }

  @Test
  public void testEncodedArayHeaderLength() {
    assertThat(MsgPackWriter.getEncodedArrayHeaderLength(0x0f)).isEqualTo(1);
    assertThat(MsgPackWriter.getEncodedArrayHeaderLength(0xffff)).isEqualTo(3);
    assertThat(MsgPackWriter.getEncodedArrayHeaderLength(0x7fff_ffff)).isEqualTo(5);
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
