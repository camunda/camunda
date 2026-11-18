/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.bytes;
import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.compile;
import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.get;
import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.pojo;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.Result;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Lists nested to any depth are stored as arrays of arrays, whatever the innermost elements. */
final class GeneratedNestedListsTest {

  // {"path": [[1, 300], [], [70000]]}
  private static final byte[] MESSAGE = {
    (byte) 0x81,
    (byte) 0xa4,
    'p',
    'a',
    't',
    'h',
    (byte) 0x93,
    (byte) 0x92,
    0x01,
    (byte) 0xcd,
    0x01,
    0x2c,
    (byte) 0x90,
    (byte) 0x91,
    (byte) 0xce,
    0x00,
    0x01,
    0x11,
    0x70
  };

  private static final List<List<Long>> PATH =
      List.of(List.of(1L, 300L), List.of(), List.of(70000L));

  private static Result compiled;

  @BeforeAll
  static void compileSample() {
    compiled =
        compile(
            pojo("Paths", "final List<List<Long>> path = new ArrayList<>();"),
            pojo(
                "Deep",
                """
                final List<List<List<Long>>> deep = new ArrayList<>();
                final List<List<List<Integer>>> ints = new ArrayList<>();
                final List<List<DirectBuffer>> names = new ArrayList<>();
                """));
    assertThat(compiled.errors()).isEmpty();
  }

  @Test
  void shouldRoundTripAListOfLists() throws Exception {
    // given
    final var message = MESSAGE;
    final var paths = instance();

    // when
    paths.wrap(new UnsafeBuffer(message), 0, message.length);
    final var written = new UnsafeBuffer(new byte[paths.getLength()]);
    paths.write(written, 0);

    // then
    assertThat(get(paths, "path")).isEqualTo(PATH);
    assertThat(written.byteArray()).isEqualTo(message);
  }

  @Test
  void shouldRoundTripDeeperNesting() throws Exception {
    // given
    final var expected =
        List.of(List.of(List.of(1L, 2L), List.<Long>of()), List.of(List.of(70000L)));
    final var source = instance("sample.Deep");
    @SuppressWarnings("unchecked")
    final var deep = (List<List<List<Long>>>) get(source, "deep");
    deep.addAll(expected);
    @SuppressWarnings("unchecked")
    final var ints = (List<List<List<Integer>>>) get(source, "ints");
    ints.add(List.of(List.of(1, -2, Integer.MAX_VALUE), List.of()));
    @SuppressWarnings("unchecked")
    final var names = (List<List<DirectBuffer>>) get(source, "names");
    names.add(List.of(new UnsafeBuffer("a".getBytes()), new UnsafeBuffer("bc".getBytes())));
    names.add(List.of());

    // when
    final var written = new UnsafeBuffer(new byte[source.getLength()]);
    source.write(written, 0);
    final var read = instance("sample.Deep");
    read.wrap(written, 0, written.capacity());

    // then
    assertThat(get(read, "deep")).isEqualTo(expected);
    assertThat(get(read, "ints")).isEqualTo(ints);
    assertThat(get(read, "names")).isEqualTo(names);
    assertThat(bytes(read)).isEqualTo(written.byteArray());
  }

  private static MsgpackBufferValue<?> instance() throws ReflectiveOperationException {
    return instance("sample.Paths");
  }

  private static MsgpackBufferValue<?> instance(final String name)
      throws ReflectiveOperationException {
    return (MsgpackBufferValue<?>) compiled.load(name).getConstructor().newInstance();
  }
}
