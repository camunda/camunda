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
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Data written by another version, or by an {@code UnpackedObject}, is a map whose entry order is
 * not the declaration order of the generated codec. These tests write the bytes by hand, so they do
 * not depend on the code they check.
 */
final class GeneratedCodecKeyOrderTest {

  private static final String IMPORTS =
      """
      package sample;
      import io.camunda.zeebe.msgpack.pojo.*;
      import java.util.*;
      import org.agrona.DirectBuffer;
      """;

  /** The map {"a": 1} */
  private static final DirectBuffer DOCUMENT =
      new UnsafeBuffer(new byte[] {(byte) 0x81, (byte) 0xa1, 'a', 0x01});

  private static Result compiled;

  @BeforeAll
  static void compileSample() {
    compiled =
        compile(
            new Source("sample.Kind", "package sample; public enum Kind { ONE, TWO }"),
            new Source(
                "sample.Inner",
                IMPORTS
                    + """
                    @MsgpackPojo
                    public final class Inner extends MsgpackBufferValue<Inner> {
                      int value = 3;
                      @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();
                    }
                    """),
            new Source(
                "sample.Sample",
                IMPORTS
                    + """
                    @MsgpackPojo
                    public final class Sample extends MsgpackBufferValue<Sample> {
                      long key = -1L;
                      int count = 7;
                      boolean flag = true;
                      DirectBuffer name = Decoding.EMPTY;
                      @MsgpackProperty(binary = true) DirectBuffer payload = Decoding.EMPTY;
                      @MsgpackProperty(document = true) DirectBuffer doc = new org.agrona.concurrent.UnsafeBuffer(new byte[] {(byte) 0x80});
                      Kind kind = Kind.TWO;
                      final List<DirectBuffer> ids = new ArrayList<>();
                      final List<Long> versions = new ArrayList<>();
                      final Inner inner = new Inner();
                      @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();
                    }
                    """));
    assertThat(compiled.errors()).isEmpty();
  }

  @Test
  void shouldReadKeysInReverseDeclarationOrder() throws Exception {
    // given
    final var entries = allEntries();
    Collections.reverse(entries);
    final var bytes = encode(entries);

    // when
    final var sample = read(bytes);

    // then
    assertAllFieldsAreSet(sample);
  }

  @Test
  void shouldReadSwappedNeighbours() throws Exception {
    // given every pair of neighbouring entries is swapped
    final var entries = allEntries();
    for (int i = 0; i + 1 < entries.size(); i += 2) {
      Collections.swap(entries, i, i + 1);
    }

    // when
    final var sample = read(encode(entries));

    // then
    assertAllFieldsAreSet(sample);
  }

  @Test
  void shouldReadNestedKeysInSwappedOrder() throws Exception {
    // given
    final var entries = allEntries();
    entries.removeIf(e -> e.key().equals("inner"));
    entries.add(
        new Entry(
            "inner",
            w -> {
              w.writeMapHeader(2);
              w.writeString(utf8("zz-newer-version"));
              w.writeMapHeader(0);
              w.writeString(utf8("value"));
              w.writeInteger(9);
            }));

    // when
    final var sample = read(encode(entries));

    // then
    assertThat(get(get(sample, "inner"), "value")).isEqualTo(9);
  }

  @Test
  void shouldKeepTheDefaultOfEveryMissingKey() throws Exception {
    // given
    final var bytes =
        encode(
            List.of(
                new Entry("name", w -> w.writeString(utf8("only"))),
                new Entry("count", w -> w.writeInteger(1))));

    // when
    final var sample = read(bytes);

    // then
    assertThat(get(sample, "name")).isEqualTo(utf8("only"));
    assertThat(get(sample, "count")).isEqualTo(1);
    assertThat(get(sample, "key")).isEqualTo(-1L);
    assertThat(get(sample, "flag")).isEqualTo(true);
    assertThat(get(sample, "doc")).isEqualTo(new UnsafeBuffer(new byte[] {(byte) 0x80}));
    assertThat(get(sample, "kind").toString()).isEqualTo("TWO");
    assertThat((List<?>) get(sample, "ids")).isEmpty();
    assertThat((List<?>) get(sample, "versions")).isEmpty();
    assertThat(get(get(sample, "inner"), "value")).isEqualTo(3);
  }

  @Test
  void shouldResetBeforeReadingSoNothingOfThePreviousValueRemains() throws Exception {
    // given an instance holding a full value
    final var sample = read(encode(allEntries()));

    // when it is read again from a message that has a single key
    final var only = encode(List.of(new Entry("count", w -> w.writeInteger(2))));
    ((MsgpackBufferValue<?>) sample).wrap(new UnsafeBuffer(only), 0, only.length);

    // then
    assertThat(get(sample, "count")).isEqualTo(2);
    assertThat(get(sample, "key")).isEqualTo(-1L);
    assertThat(get(sample, "name")).isEqualTo(new UnsafeBuffer(new byte[0]));
    assertThat((List<?>) get(sample, "ids")).isEmpty();
    assertThat((List<?>) get(sample, "versions")).isEmpty();
    assertThat(get(get(sample, "inner"), "value")).isEqualTo(3);
  }

  @Test
  void shouldWriteUnknownKeysBackWhereverTheyWereInTheMessage() throws Exception {
    // given unknown keys before, between and after the known ones
    final Consumer<MsgPackWriter> nestedUnknown =
        w -> {
          w.writeMapHeader(2);
          w.writeString(utf8("a"));
          w.writeArrayHeader(2);
          w.writeInteger(-70_000);
          w.writeNil();
          w.writeString(utf8("b"));
          w.writeBinary(new UnsafeBuffer(new byte[] {1, 2, 3}));
        };
    final var entries =
        new ArrayList<>(
            List.of(
                new Entry("unknownFirst", nestedUnknown),
                new Entry("key", w -> w.writeInteger(5)),
                new Entry("unknownMiddle", w -> w.writeFloat(1.5)),
                new Entry("name", w -> w.writeString(utf8("n"))),
                new Entry("unknownLast", w -> w.writeBoolean(true))));
    final var bytes = encode(entries);

    // when
    final var sample = read(bytes);
    final var written = new ExpandableArrayBuffer();
    final int length = ((MsgpackBufferValue<?>) sample).write(written, 0);

    // then known keys are read
    assertThat(get(sample, "key")).isEqualTo(5L);
    assertThat(get(sample, "name")).isEqualTo(utf8("n"));

    // and every unknown entry is written back with identical bytes
    final var before = entriesOf(new UnsafeBuffer(bytes), bytes.length);
    final var after = entriesOf(written, length);
    for (final var key : List.of("unknownFirst", "unknownMiddle", "unknownLast")) {
      assertThat(after.get(key)).as(key).isEqualTo(before.get(key));
    }
  }

  // ---------------------------------------------------------------- helpers

  private static List<Entry> allEntries() {
    return new ArrayList<>(
        List.of(
            new Entry("key", w -> w.writeInteger(42)),
            new Entry("count", w -> w.writeInteger(8)),
            new Entry("flag", w -> w.writeBoolean(false)),
            new Entry("name", w -> w.writeString(utf8("zeebe"))),
            new Entry("payload", w -> w.writeBinary(utf8("bin"))),
            new Entry("doc", w -> w.writeBinary(DOCUMENT)),
            new Entry("kind", w -> w.writeString(utf8("ONE"))),
            new Entry(
                "ids",
                w -> {
                  w.writeArrayHeader(2);
                  w.writeString(utf8("a"));
                  w.writeString(utf8("b"));
                }),
            new Entry(
                "versions",
                w -> {
                  w.writeArrayHeader(3);
                  w.writeInteger(1);
                  w.writeInteger(300);
                  w.writeInteger(5_000_000_000L);
                }),
            new Entry(
                "inner",
                w -> {
                  w.writeMapHeader(1);
                  w.writeString(utf8("value"));
                  w.writeInteger(9);
                })));
  }

  private static void assertAllFieldsAreSet(final Object sample) throws Exception {
    assertThat(get(sample, "key")).isEqualTo(42L);
    assertThat(get(sample, "count")).isEqualTo(8);
    assertThat(get(sample, "flag")).isEqualTo(false);
    assertThat(get(sample, "name")).isEqualTo(utf8("zeebe"));
    assertThat(get(sample, "payload")).isEqualTo(utf8("bin"));
    assertThat(get(sample, "doc")).isEqualTo(DOCUMENT);
    assertThat(get(sample, "kind").toString()).isEqualTo("ONE");
    assertThat(get(sample, "ids")).isEqualTo(List.of(utf8("a"), utf8("b")));
    assertThat(get(sample, "versions")).isEqualTo(List.of(1L, 300L, 5_000_000_000L));
    assertThat(get(get(sample, "inner"), "value")).isEqualTo(9);
  }

  private static Object read(final byte[] bytes) throws Exception {
    final var sample = compiled.load("sample.Sample").getConstructor().newInstance();
    ((MsgpackBufferValue<?>) sample).wrap(new UnsafeBuffer(bytes), 0, bytes.length);
    return sample;
  }

  private static byte[] encode(final List<Entry> entries) {
    final var buffer = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(buffer, 0);
    writer.writeMapHeader(entries.size());
    for (final var entry : entries) {
      writer.writeString(utf8(entry.key()));
      entry.value().accept(writer);
    }
    return Arrays.copyOf(buffer.byteArray(), writer.getOffset());
  }

  /** The encoded value of every top-level entry by key. */
  private static Map<String, List<Byte>> entriesOf(final DirectBuffer buffer, final int length) {
    final var reader = new MsgPackReader().wrap(buffer, 0, length);
    final var result = new LinkedHashMap<String, List<Byte>>();
    final int size = reader.readMapHeader();
    for (int i = 0; i < size; i++) {
      final int keyLength = reader.readStringLength();
      final var key = reader.getBuffer().getStringWithoutLengthUtf8(reader.getOffset(), keyLength);
      reader.skipBytes(keyLength);
      final int start = reader.getOffset();
      reader.skipValue();
      final var value = new ArrayList<Byte>();
      for (int p = start; p < reader.getOffset(); p++) {
        value.add(buffer.getByte(p));
      }
      result.put(key, value);
    }
    return result;
  }

  private static DirectBuffer utf8(final String s) {
    return new UnsafeBuffer(s.getBytes(StandardCharsets.UTF_8));
  }

  private record Entry(String key, Consumer<MsgPackWriter> value) {}
}
