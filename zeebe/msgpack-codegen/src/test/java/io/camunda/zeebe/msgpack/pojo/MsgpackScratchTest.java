/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.zeebe.msgpack.pojo.real.RawCommandHolder;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class MsgpackScratchTest {

  /**
   * After each call of a {@link MsgpackBufferValue} with a buffer of the caller, also one that
   * fails in the codec, the reader and writer of the thread no longer reference the buffer.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("codecCalls")
  void shouldNotReferenceTheCallerBufferAfterACodecCall(
      final String description, final boolean fails, final Consumer<MutableDirectBuffer> call) {
    // given the reader and writer of this thread, and a buffer of the caller
    final var writer = MsgpackScratch.acquire().writer(new UnsafeBuffer(new byte[8]), 0);
    final var reader = MsgpackScratch.acquire().reader(new UnsafeBuffer(new byte[8]), 0, 8);
    final var bytes = new byte[64];

    // when
    final var failure = catchThrowable(() -> call.accept(new UnsafeBuffer(bytes)));

    // then
    assertThat(failure != null).as("%s fails", description).isEqualTo(fails);
    assertThat(reader.getBuffer().byteArray()).isNotSameAs(bytes);
    final var before = bytes.clone();
    catchThrowable(() -> writer.writeInteger(1));
    assertThat(bytes).as("bytes after a write through the scratch writer").isEqualTo(before);
  }

  static Stream<Arguments> codecCalls() {
    return Stream.of(
        call("write", false, buffer -> new RawCommandHolder().write(buffer, 0)),
        call(
            "write beyond the end of the buffer",
            true,
            buffer -> new RawCommandHolder().write(buffer, buffer.capacity() - 4)),
        call(
            "read",
            false,
            buffer -> {
              final int length = new RawCommandHolder().write(buffer, 0);
              new RawCommandHolder().wrap(buffer, 0, length);
            }),
        call(
            "read of a value that is not a map",
            true,
            buffer -> new RawCommandHolder().wrap(buffer, 0, buffer.capacity())));
  }

  private static Arguments call(
      final String description, final boolean fails, final Consumer<MutableDirectBuffer> call) {
    return Arguments.of(description, fails, call);
  }
}
