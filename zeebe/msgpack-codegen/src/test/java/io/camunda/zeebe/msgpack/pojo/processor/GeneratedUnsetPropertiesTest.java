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
import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.copy;
import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.encoded;
import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.get;
import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.pojo;
import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.set;
import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.utf8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.RequiredInt;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.Result;
import io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.Source;
import java.util.ArrayList;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Properties without a default, compiled in memory: every kind of them in one class, and what only
 * a {@code @Required} field does (its initializer is the sentinel for unset, so that value can be
 * neither written nor read). {@code BigRecordTest} compares the unset semantics with the legacy
 * record.
 */
final class GeneratedUnsetPropertiesTest {

  private static final String UNSET_ON_WRITE =
      "Expected a value or default value to be set before writing, but has nothing";

  private static Result compiled;

  @BeforeAll
  static void compileSample() {
    compiled =
        compile(
            pojo(
                "Sentinels",
                """
                @Required long key;
                @Required long version = -1L;
                @Required int position = Integer.MIN_VALUE;
                """),
            pojo(
                "RequiredBuffers",
                """
                @Required final UnsafeBuffer name = new UnsafeBuffer(Decoding.UNSET);
                @Required @Binary final UnsafeBuffer payload = new UnsafeBuffer(Decoding.UNSET);
                """),
            pojo(
                "WrongInitializer",
                "@Required final UnsafeBuffer name = new UnsafeBuffer(new byte[0]);"),
            new Source("sample.Kind", "package sample; public enum Kind { ONE, TWO }"),
            pojo(
                "Unset",
                """
                final RequiredLong key = new RequiredLong();
                final RequiredInt count = new RequiredInt();
                @Required long sentinel;
                @Nullable Long boxedLong;
                @Nullable Integer boxedInt;
                @Nullable Boolean flag;
                @Nullable DirectBuffer name;
                @Binary @Nullable DirectBuffer payload;
                @Nullable Kind kind;
                """),
            pojo(
                "Optional",
                """
                @Nullable Long first;
                @Nullable Long second;
                @Nullable Long third;
                """));
    assertThat(compiled.errors()).isEmpty();
  }

  @Test
  void shouldCheckEachFieldAgainstItsOwnSentinel() throws Exception {
    // given fields of the same type, each holding the sentinel of another one
    final var value = newInstance();
    set(value, "key", Long.MIN_VALUE);
    set(value, "version", 0L);
    set(value, "position", -1);

    // when
    final var bytes = bytes(value);
    final var read = newInstance();
    read.wrap(new UnsafeBuffer(bytes), 0, bytes.length);

    // then the values are written and read back, as none is the sentinel of its own field
    assertThat(read).isEqualTo(value);

    // when a field holds its own sentinel
    set(value, "version", -1L);

    // then only that field is unset
    assertThat(catchThrowable(() -> bytes(value)))
        .hasMessage("Property 'version' is invalid: " + UNSET_ON_WRITE);
  }

  @Test
  void shouldFailToReadAValueEqualToTheSentinel() throws Exception {
    // given a message, written by a legacy property that accepts any value, holding the sentinel
    final var message =
        encoded(
            w -> {
              w.writeMapHeader(3);
              w.writeString(utf8("key"));
              w.writeInteger(1);
              w.writeString(utf8("version"));
              w.writeInteger(-1);
              w.writeString(utf8("position"));
              w.writeInteger(2);
            });

    // when
    final var failure =
        catchThrowable(() -> newInstance().wrap(new UnsafeBuffer(message), 0, message.length));

    // then
    assertThat(failure.getCause()).hasMessage("Property 'version' has no valid value");
  }

  @Test
  void shouldTreatARequiredBufferAsUnsetUntilItHoldsAValue() throws Exception {
    // given a new instance, whose buffers still hold the sentinel
    final var value = newInstance("sample.RequiredBuffers");

    // then it cannot be written
    assertThat(catchThrowable(() -> bytes(value)))
        .hasMessage("Property 'name' is invalid: " + UNSET_ON_WRITE);

    // when both hold a value, an empty one included
    set(value, "name", new UnsafeBuffer(new byte[0]));
    set(value, "payload", utf8("bin"));
    final var bytes = bytes(value);
    final var read = newInstance("sample.RequiredBuffers");
    read.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
    final var copy = newInstance("sample.RequiredBuffers");
    copy(read, copy);

    // then they are written, read and copied
    assertThat(read).isEqualTo(value);
    assertThat(copy).isEqualTo(value);
  }

  @Test
  void shouldKeepARequiredBufferUnsetWhenCopyingAndResetting() throws Exception {
    // given a value that holds data and an unset one
    final var unset = newInstance("sample.RequiredBuffers");
    final var target = newInstance("sample.RequiredBuffers");
    set(target, "name", utf8("abc"));
    set(target, "payload", utf8("bin"));

    // when the unset one is copied over the other
    copy(unset, target);

    // then the target is unset again
    assertThat(catchThrowable(() -> bytes(target)))
        .hasMessage("Property 'name' is invalid: " + UNSET_ON_WRITE);

    // when a value that holds data is reset
    set(target, "name", utf8("abc"));
    set(target, "payload", utf8("bin"));
    target.reset();

    // then it is unset too
    assertThat(catchThrowable(() -> bytes(target)))
        .hasMessage("Property 'name' is invalid: " + UNSET_ON_WRITE);
  }

  @Test
  void shouldFailToReadAMessageWithoutARequiredBuffer() throws Exception {
    // given a message that has the name but not the payload
    final var message =
        encoded(
            w -> {
              w.writeMapHeader(1);
              w.writeString(utf8("name"));
              w.writeString(utf8("abc"));
            });

    // when
    final var failure =
        catchThrowable(
            () ->
                newInstance("sample.RequiredBuffers")
                    .wrap(new UnsafeBuffer(message), 0, message.length));

    // then
    assertThat(failure.getCause()).hasMessageContaining("payload");
  }

  @Test
  void shouldRejectARequiredBufferWithoutTheUnsetInitializer() {
    // when the ops class is loaded
    final var failure =
        catchThrowable(
            () -> compiled.load("sample.WrongInitializerOps").getField("INSTANCE").get(null));

    // then
    assertThat(failure)
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .rootCause()
        .hasMessageContaining("new UnsafeBuffer(Decoding.UNSET)");
  }

  @Test
  void shouldRoundTripEveryKindOfPropertyWithoutADefault() throws Exception {
    // given
    final var value = newInstance("sample.Unset");
    ((RequiredLong) get(value, "key")).set(0L);
    ((RequiredInt) get(value, "count")).set(-3);
    set(value, "sentinel", 7L);
    set(value, "boxedLong", Long.MIN_VALUE);
    set(value, "boxedInt", Integer.MAX_VALUE);
    set(value, "flag", false);
    set(value, "name", utf8("abc"));
    set(value, "payload", utf8("bin"));
    set(value, "kind", compiled.load("sample.Kind").getEnumConstants()[1]);

    // when
    final var bytes = bytes(value);
    final var read = newInstance("sample.Unset");
    read.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
    final var copy = newInstance("sample.Unset");
    copy(read, copy);

    // then
    assertThat(read).isEqualTo(value).hasSameHashCodeAs(value).hasToString(value.toString());
    assertThat(copy).isEqualTo(value);
    assertThat(bytes(copy)).isEqualTo(bytes);

    // when
    read.reset();

    // then every property is unset again
    assertThat(read.toString()).isEqualTo("{}");
    assertThat(catchThrowable(() -> bytes(read))).hasMessageContaining("Property 'key' is invalid");
  }

  @Test
  void shouldPrintACommaOnlyBetweenPrintedProperties() throws Exception {
    // given a value with an unknown property, read from a message
    final var message =
        encoded(
            w -> {
              w.writeMapHeader(4);
              for (final var key : new String[] {"first", "second", "third", "u"}) {
                w.writeString(utf8(key));
                w.writeInteger(1);
              }
            });
    final var value = newInstance("sample.Optional");
    value.wrap(new UnsafeBuffer(message), 0, message.length);

    // when the properties are unset in turn: first, in the middle, last, then all
    final var printed = new ArrayList<String>();
    for (final var field : new String[] {"first", "third", "second"}) {
      set(value, field, null);
      printed.add(value.toString());
    }

    // then an unset property leaves no comma behind, also before the unknown property
    assertThat(printed)
        .containsExactly(
            "{\"second\":1,\"third\":1,\"u\":[packed value (length=1)]}",
            "{\"second\":1,\"u\":[packed value (length=1)]}",
            "{\"u\":[packed value (length=1)]}");
  }

  private static MsgpackBufferValue<?> newInstance(final String name)
      throws ReflectiveOperationException {
    return (MsgpackBufferValue<?>) compiled.load(name).getConstructor().newInstance();
  }

  private static MsgpackBufferValue<?> newInstance() throws ReflectiveOperationException {
    return (MsgpackBufferValue<?>) compiled.load("sample.Sentinels").getConstructor().newInstance();
  }
}
