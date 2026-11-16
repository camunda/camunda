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
import io.camunda.zeebe.msgpack.value.StringValue;
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
                import org.agrona.DirectBuffer;
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
                  final List<List<List<Integer>>> ints = new ArrayList<>();
                  final List<List<DirectBuffer>> names = new ArrayList<>();
                  final List<List<Item>> items = new ArrayList<>();
                  final List<List<io.camunda.zeebe.msgpack.value.StringValue>> values = new ArrayList<>();
                  @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();
                }
                """),
            new Source(
                "sample.Item",
                """
                package sample;
                import io.camunda.zeebe.msgpack.pojo.*;
                @MsgpackPojo
                public final class Item extends MsgpackBufferValue<Item> {
                  @Override
                  protected MsgpackCodec<Item> codec() {
                    return ItemCodec.INSTANCE;
                  }

                  @Override
                  protected MsgpackOps<Item> ops() {
                    return ItemOps.INSTANCE;
                  }

                  long id;
                  @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();
                  public Item() {}
                  public Item(final long id) { this.id = id; }
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
    @SuppressWarnings("unchecked")
    final var ints = (List<List<List<Integer>>>) get(source, "ints");
    ints.add(List.of(List.of(1, -2, Integer.MAX_VALUE), List.of()));
    @SuppressWarnings("unchecked")
    final var names = (List<List<DirectBuffer>>) get(source, "names");
    names.add(List.of(new UnsafeBuffer("a".getBytes()), new UnsafeBuffer("bc".getBytes())));
    names.add(List.of());
    final var item = compiled.load("sample.Item").getConstructor(long.class);
    @SuppressWarnings("unchecked")
    final var items = (List<List<Object>>) get(source, "items");
    items.add(List.of(item.newInstance(7L), item.newInstance(8L)));
    @SuppressWarnings("unchecked")
    final var values = (List<List<StringValue>>) get(source, "values");
    final var value = new StringValue();
    value.wrap(new UnsafeBuffer("v".getBytes()));
    values.add(List.of(value));

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
    assertThat(read).isEqualTo(source).hasSameHashCodeAs(source);
    assertThat(copy.toString())
        .contains("[[[1,2],[]],[[70000]]]", "\"deep\"", "[[\"a\",\"bc\"],[]]", "[[{\"id\":7");
    assertThat(copy.toString()).contains("[[\"v\"]]");
    assertThat(get(copy, "ints")).isEqualTo(ints).isEqualTo(get(read, "ints"));
    assertThat(copy.toString()).contains("[[[1,-2,2147483647],[]]]");
    assertThat(get(copy, "values")).isEqualTo(values);
    assertThat(((List<?>) get(copy, "values")).get(0)).isNotSameAs(values.get(0));
    assertThat(((List<?>) get(copy, "items")).get(0)).isNotSameAs(items.get(0));
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
