/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.msgpack.MsgpackPropertyException;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.msgpack.value.BaseValue;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class DecodingTest {

  @Test
  void shouldReturnTheValueWhenItIsSet() {
    // given
    final String value = "set";

    // when
    final String result = Decoding.required(value, "key");

    // then
    assertThat(result).isSameAs(value);
  }

  @Test
  void shouldFailLikeAnUnsetLegacyPropertyWhenTheValueIsNull() {
    // given
    final var legacy = new LongProperty("key");
    final Throwable legacyFailure =
        org.assertj.core.api.Assertions.catchThrowable(legacy::getValue);

    // when
    final Throwable failure =
        org.assertj.core.api.Assertions.catchThrowable(() -> Decoding.required(null, "key"));

    // then
    assertThat(failure)
        .isExactlyInstanceOf(MsgpackPropertyException.class)
        .isExactlyInstanceOf(legacyFailure.getClass())
        .hasMessage(legacyFailure.getMessage());
    assertThat(((MsgpackPropertyException) failure).getProperty()).isEqualTo("key");
    assertThatThrownBy(() -> Decoding.required(null, "key"))
        .hasMessage(
            "Property 'key' is invalid: Expected a value or default value to be specified, but has nothing");
  }

  @Test
  void shouldHashANullBufferAsZeroAndAnEmptyBufferAsOne() {
    // then
    assertThat(Decoding.contentHash(null)).isZero();
    assertThat(Decoding.contentHash(new UnsafeBuffer(new byte[0]))).isEqualTo(1);
  }

  @Test
  void shouldHashTheBytesOfABufferLikeArraysHashCode() {
    // given
    final byte[] bytes = {1, -2, 3, 127, -128};

    // when
    final int hash = Decoding.contentHash(new UnsafeBuffer(bytes));

    // then the sign of a byte counts and so does its position
    assertThat(hash).isEqualTo(java.util.Arrays.hashCode(bytes));
    assertThat(hash)
        .isNotEqualTo(Decoding.contentHash(new UnsafeBuffer(new byte[] {-2, 1, 3, 127, -128})));
  }

  @Test
  void shouldResetTheTargetOfACopyBeforeReadingIntoIt() {
    // given
    final var source = new RecordingValue();
    final var target = new RecordingValue();

    // when
    Decoding.copyValue(source, target);

    // then
    assertThat(target.events).containsExactly("reset", "read");
  }

  @Test
  void shouldRejectAValueThatEndsAfterTheBuffer() {
    // given a string header of 5 bytes, and only 1 byte of payload
    final var buffer = new UnsafeBuffer(new byte[] {(byte) 0xa5, 'a'});
    final var reader = new MsgPackReader();
    reader.wrap(buffer, 0, buffer.capacity());

    // then
    assertThatThrownBy(() -> Decoding.skipValue(reader))
        .hasMessage("Expected a value that ends at offset 6, but the input has only 2 bytes");
  }

  @Test
  void shouldSkipAValueThatEndsAtTheEndOfTheBuffer() {
    // given
    final var buffer = new UnsafeBuffer(new byte[] {(byte) 0xa2, 'a', 'b'});
    final var reader = new MsgPackReader();
    reader.wrap(buffer, 0, buffer.capacity());

    // when
    Decoding.skipValue(reader);

    // then
    assertThat(reader.getOffset()).isEqualTo(3);
  }

  /** Writes and reads a nil, and records what is done to it. */
  private static final class RecordingValue extends BaseValue {
    private final List<String> events = new ArrayList<>();

    @Override
    public void writeJSON(final StringBuilder builder) {
      builder.append("null");
    }

    @Override
    public int write(final MsgPackWriter writer) {
      return writer.writeNil();
    }

    @Override
    public void read(final MsgPackReader reader) {
      events.add("read");
      reader.skipValue();
    }

    @Override
    public int getEncodedLength() {
      return 1;
    }

    @Override
    public void reset() {
      events.add("reset");
    }
  }
}
