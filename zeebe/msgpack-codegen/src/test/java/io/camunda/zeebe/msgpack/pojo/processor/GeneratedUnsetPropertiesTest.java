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
 * Properties without a default, compiled in memory: every kind of them in one class. {@code
 * BigRecordTest} compares the unset semantics with the legacy record.
 */
final class GeneratedUnsetPropertiesTest {

  private static Result compiled;

  @BeforeAll
  static void compileSample() {
    compiled =
        compile(
            new Source("sample.Kind", "package sample; public enum Kind { ONE, TWO }"),
            pojo(
                "Unset",
                """
                final RequiredLong key = new RequiredLong();
                final RequiredInt count = new RequiredInt();
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
  void shouldRoundTripEveryKindOfPropertyWithoutADefault() throws Exception {
    // given
    final var value = newInstance("sample.Unset");
    ((RequiredLong) get(value, "key")).set(0L);
    ((RequiredInt) get(value, "count")).set(-3);
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
  void shouldFailToReadAMessageWithoutAPropertyWithoutADefault() throws Exception {
    // given a message that has the first property but not the others
    final var message =
        encoded(
            w -> {
              w.writeMapHeader(1);
              w.writeString(utf8("first"));
              w.writeInteger(1);
            });

    // when
    final var failure =
        catchThrowable(
            () ->
                newInstance("sample.Optional").wrap(new UnsafeBuffer(message), 0, message.length));

    // then
    assertThat(failure.getCause()).hasMessage("Property 'second' has no valid value");
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
}
