/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.compile;
import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.get;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.Result;
import io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.Source;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A list nested to any depth with {@code Long}s as leaves is stored as arrays of integers. */
final class GeneratedLongListListTest {

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
            new Source(
                "sample.Paths",
                """
                package sample;
                import io.camunda.zeebe.msgpack.pojo.*;
                import java.util.*;
                @MsgpackPojo
                public final class Paths extends MsgpackBufferValue<Paths> {
                  @Override
                  protected MsgpackCodec<Paths> codec() {
                    return PathsCodec.INSTANCE;
                  }

                  @Override
                  protected MsgpackOps<Paths> ops() {
                    return PathsOps.INSTANCE;
                  }

                  final List<List<Long>> path = new ArrayList<>();
                  @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();
                }
                """),
            new Source(
                "sample.Deep",
                """
                package sample;
                import io.camunda.zeebe.msgpack.pojo.*;
                import java.util.*;
                @MsgpackPojo
                public final class Deep extends MsgpackBufferValue<Deep> {
                  @Override
                  protected MsgpackCodec<Deep> codec() {
                    return DeepCodec.INSTANCE;
                  }

                  @Override
                  protected MsgpackOps<Deep> ops() {
                    return DeepOps.INSTANCE;
                  }

                  final List<List<List<Long>>> deep = new ArrayList<>();
                  @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();
                }
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
    assertThat(paths.toString()).contains("[[1,300],[],[70000]]");
  }

  @Test
  void shouldCopyWithoutSharingTheInnerLists() throws Exception {
    // given
    final var message = MESSAGE;
    final var source = instance();
    source.wrap(new UnsafeBuffer(message), 0, message.length);
    final var target = instance();

    // when
    copy(target, source);
    @SuppressWarnings("unchecked")
    final var sourcePath = (List<List<Long>>) get(source, "path");
    sourcePath.get(0).add(7L);

    // then
    assertThat(get(target, "path")).isEqualTo(PATH);
    assertThat(target).isNotEqualTo(source).hasSameHashCodeAs(instanceOf(message));
  }

  @Test
  void shouldRoundTripAndCopyDeeperNesting() throws Exception {
    // given
    final var expected =
        List.of(List.of(List.of(1L, 2L), List.<Long>of()), List.of(List.of(70000L)));
    final var source = instance("sample.Deep");
    @SuppressWarnings("unchecked")
    final var deep = (List<List<List<Long>>>) get(source, "deep");
    deep.addAll(expected);

    // when
    final var written = new UnsafeBuffer(new byte[source.getLength()]);
    source.write(written, 0);
    final var read = instance("sample.Deep");
    read.wrap(written, 0, written.capacity());
    final var copy = instance("sample.Deep");
    copy(copy, read);

    // then
    assertThat(get(read, "deep")).isEqualTo(expected);
    assertThat(get(copy, "deep")).isEqualTo(expected);
    assertThat(copy).isEqualTo(source).hasSameHashCodeAs(source);
    assertThat(copy.toString()).contains("[[[1,2],[]],[[70000]]]");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void copy(final MsgpackBufferValue target, final MsgpackBufferValue source) {
    target.copyFrom(source);
  }

  private static MsgpackBufferValue<?> instanceOf(final byte[] message) throws Exception {
    final var paths = instance();
    paths.wrap(new UnsafeBuffer(message), 0, message.length);
    return paths;
  }

  private static MsgpackBufferValue<?> instance() throws ReflectiveOperationException {
    return instance("sample.Paths");
  }

  private static MsgpackBufferValue<?> instance(final String name)
      throws ReflectiveOperationException {
    return (MsgpackBufferValue<?>) compiled.load(name).getConstructor().newInstance();
  }
}
