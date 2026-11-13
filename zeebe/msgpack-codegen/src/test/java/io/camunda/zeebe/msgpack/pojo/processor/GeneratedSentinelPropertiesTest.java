/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.compile;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.Result;
import io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.Source;
import java.util.Arrays;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Compares {@code @MsgpackRequired} long and int fields, whose initializer is the sentinel for
 * unset, with the legacy properties without a default they replace.
 */
final class GeneratedSentinelPropertiesTest {

  private static Result compiled;

  @BeforeAll
  static void compileSamples() {
    compiled =
        compile(
            new Source("sample.Kind", "package sample; public enum Kind { ONE, TWO }"),
            new Source(
                "sample.Legacy",
                """
                package sample;
                import io.camunda.zeebe.msgpack.UnpackedObject;
                import io.camunda.zeebe.msgpack.property.*;
                public final class Legacy extends UnpackedObject {
                  private final LongProperty key = new LongProperty("key");
                  private final IntegerProperty count = new IntegerProperty("count");
                  private final EnumProperty<Kind> kind = new EnumProperty<>("kind", Kind.class);
                  public Legacy() {
                    super(3);
                    declareProperty(key).declareProperty(count).declareProperty(kind);
                  }
                  public void setCount(final int value) {
                    count.setValue(value);
                  }
                  public Legacy fillExcept(final String skipped) {
                    if (!skipped.equals("key")) { key.setValue(Long.MIN_VALUE); }
                    if (!skipped.equals("count")) { count.setValue(-3); }
                    if (!skipped.equals("kind")) { kind.setValue(Kind.TWO); }
                    return this;
                  }
                }
                """),
            new Source(
                "sample.Sample",
                """
                package sample;
                import io.camunda.zeebe.msgpack.pojo.*;
                import org.jspecify.annotations.Nullable;
                @MsgpackPojo
                public final class Sample extends MsgpackBufferValue<Sample> {
                  @Override
                  protected MsgpackCodec<Sample> codec() {
                    return SampleCodec.INSTANCE;
                  }

                  @Override
                  protected MsgpackOps<Sample> ops() {
                    return SampleOps.INSTANCE;
                  }

                  @MsgpackRequired long key = -1L;
                  @MsgpackRequired int count = -1;
                  @Nullable Kind kind;
                  @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();

                  public Sample fillExcept(final String skipped) {
                    if (!skipped.equals("key")) { key = Long.MIN_VALUE; }
                    if (!skipped.equals("count")) { count = -3; }
                    if (!skipped.equals("kind")) { kind = Kind.TWO; }
                    return this;
                  }
                }
                """));
    assertThat(compiled.errors()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"key", "count", "kind"})
  void shouldFailToWriteLikeTheLegacyPropertyWhenUnset(final String unset) throws Exception {
    // given
    final var legacy = legacy(unset);
    final var sample = sample(unset);

    // when
    final var legacyFailure = catchThrowable(() -> write(legacy));
    final var failure = catchThrowable(() -> write(sample));

    // then
    assertThat(failure)
        .isExactlyInstanceOf(legacyFailure.getClass())
        .hasMessage(legacyFailure.getMessage())
        .hasMessage(
            "Property '%s' is invalid: Expected a value or default value to be set before writing, but has nothing"
                .formatted(unset));
  }

  @ParameterizedTest
  @ValueSource(strings = {"key", "count", "kind"})
  void shouldFailToGetTheLengthLikeTheLegacyPropertyWhenUnset(final String unset) throws Exception {
    // given
    final var legacy = legacy(unset);
    final var sample = sample(unset);

    // when
    final var legacyFailure = catchThrowable(legacy::getLength);
    final var failure = catchThrowable(sample::getLength);

    // then
    assertThat(failure)
        .isExactlyInstanceOf(legacyFailure.getClass())
        .hasMessage(legacyFailure.getMessage())
        .hasMessage(
            "Property '%s' is invalid: Expected a value or default value to be specified, but has nothing"
                .formatted(unset));
  }

  @Test
  void shouldStoreTheSameBytesAsTheLegacyProperties() throws Exception {
    // given
    final var legacy = legacy("");
    final var sample = sample("");

    // when
    final var bytes = write(sample);

    // then
    assertThat(bytes).isEqualTo(write(legacy));
    assertThat(sample.getLength()).isEqualTo(legacy.getLength()).isEqualTo(bytes.length);
  }

  @Test
  void shouldReadWhatTheLegacyPropertiesWrote() throws Exception {
    // given
    final var bytes = write(legacy(""));

    // when
    final var sample = unsetSample();
    sample.wrap(new UnsafeBuffer(bytes), 0, bytes.length);

    // then
    assertThat(write(sample)).isEqualTo(bytes);
    assertThat(sample).hasToString(legacy("").toString());
  }

  @Test
  void shouldFailToReadLikeTheLegacyPropertyWhenTheKeyIsMissing() throws Exception {
    // given a message that is empty
    final var empty = new byte[] {(byte) 0x80};

    // when
    final var legacyFailure =
        catchThrowable(() -> unsetLegacy().wrap(new UnsafeBuffer(empty), 0, empty.length));
    final var failure =
        catchThrowable(() -> unsetSample().wrap(new UnsafeBuffer(empty), 0, empty.length));

    // then both name the first unset property, key
    assertThat(failure.getCause())
        .isExactlyInstanceOf(legacyFailure.getCause().getClass())
        .hasMessage(legacyFailure.getCause().getMessage())
        .hasMessage("Property 'key' has no valid value");
  }

  @Test
  void shouldFailToWriteAValueEqualToTheSentinel() throws Exception {
    // given
    final var sample = sample("");
    final var key = sample.getClass().getDeclaredField("key");
    key.setAccessible(true);
    key.setLong(sample, -1L);

    // when
    final var failure = catchThrowable(() -> write(sample));

    // then
    assertThat(failure)
        .hasMessage(
            "Property 'key' is invalid: Expected a value or default value to be set before writing, but has nothing");
  }

  @Test
  void shouldFailToReadAValueEqualToTheSentinel() throws Exception {
    // given a legacy value that holds the sentinel, which the legacy property accepts
    final var legacy = legacy("");
    legacy.getClass().getMethod("setCount", int.class).invoke(legacy, -1);
    final var bytes = write(legacy);

    // when
    final var failure =
        catchThrowable(() -> unsetSample().wrap(new UnsafeBuffer(bytes), 0, bytes.length));

    // then
    assertThat(failure.getCause()).hasMessage("Property 'count' has no valid value");
  }

  @Test
  void shouldBeEqualToANewInstanceAfterAReset() throws Exception {
    // given
    final var sample = sample("");

    // when
    sample.reset();

    // then
    assertThat(sample).isEqualTo(unsetSample()).hasSameHashCodeAs(unsetSample());
  }

  @Test
  void shouldPrintUnsetPropertiesAndBeUnsetAfterAReset() throws Exception {
    // given
    final var sample = sample("");

    // when
    sample.reset();

    // then
    assertThat(sample.toString())
        .contains("\"key\":\"NO VALID WRITEABLE VALUE\"")
        .contains("\"count\":\"NO VALID WRITEABLE VALUE\"");
  }

  @Test
  void shouldCopyAndCompareUnsetPropertiesByValue() throws Exception {
    // given
    final var source = sample("count");
    final var target = sample("");

    // when
    copy(target, source);

    // then
    assertThat(target).isEqualTo(source).hasSameHashCodeAs(source);
    assertThat(target).isNotEqualTo(sample(""));
    assertThat(sample("")).isEqualTo(sample("")).hasSameHashCodeAs(sample(""));
    assertThat(sample("key")).isNotEqualTo(sample("count"));
  }

  @Test
  void shouldCheckEachFieldAgainstItsOwnSentinel() throws Exception {
    // given fields of the same type, each holding the sentinel of another one
    final var result =
        compile(
            pojo(
                """
                @MsgpackRequired long key;
                @MsgpackRequired long version = -1L;
                @MsgpackRequired long position = Long.MIN_VALUE;
                """));
    assertThat(result.errors()).isEmpty();
    final var type = result.load("sample.Bad");
    final var value = (MsgpackBufferValue<?>) type.getConstructor().newInstance();
    setLong(value, "key", Long.MIN_VALUE);
    setLong(value, "version", 0L);
    setLong(value, "position", -1L);

    // when
    final var bytes = write(value);
    final var read = (MsgpackBufferValue<?>) type.getConstructor().newInstance();
    read.wrap(new UnsafeBuffer(bytes), 0, bytes.length);

    // then the values are written and read back, as none is the sentinel of its own field
    assertThat(read).isEqualTo(value);

    // when a field holds its own sentinel
    setLong(value, "version", -1L);

    // then only that field is unset
    assertThat(catchThrowable(() -> write(value)))
        .hasMessage(
            "Property 'version' is invalid: Expected a value or default value to be set before writing, but has nothing");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "@MsgpackRequired boolean flag = false;",
        "@MsgpackRequired @Nullable Long flag;",
        "@MsgpackRequired org.agrona.@Nullable DirectBuffer flag;",
        "@MsgpackRequired final RequiredLong flag = new RequiredLong();"
      })
  void shouldRejectRequiredOnFieldsThatAreNotALongOrInt(final String field) {
    // when
    final var result = compile(pojo(field));

    // then
    assertThat(result.errors())
        .anyMatch(e -> e.contains("flag") && e.contains("@MsgpackRequired only applies"));
  }

  private static Source pojo(final String fields) {
    return new Source(
        "sample.Bad",
        """
        package sample;
        import io.camunda.zeebe.msgpack.pojo.*;
        import org.jspecify.annotations.Nullable;
        @MsgpackPojo
        public final class Bad extends MsgpackBufferValue<Bad> {
          @Override protected MsgpackCodec<Bad> codec() { return BadCodec.INSTANCE; }
          @Override protected MsgpackOps<Bad> ops() { return BadOps.INSTANCE; }
          %s
          @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();
        }
        """
            .formatted(fields));
  }

  private static void setLong(final Object target, final String field, final long value)
      throws ReflectiveOperationException {
    final var f = target.getClass().getDeclaredField(field);
    f.setAccessible(true);
    f.setLong(target, value);
  }

  private static Throwable catchThrowable(final ThrowingCallable callable) {
    return org.assertj.core.api.Assertions.catchThrowable(callable);
  }

  private static UnpackedObject legacy(final String skipped) throws ReflectiveOperationException {
    final var type = compiled.load("sample.Legacy");
    final var value = type.getConstructor().newInstance();
    type.getMethod("fillExcept", String.class).invoke(value, skipped);
    return (UnpackedObject) value;
  }

  private static UnpackedObject unsetLegacy() throws ReflectiveOperationException {
    return (UnpackedObject) compiled.load("sample.Legacy").getConstructor().newInstance();
  }

  private static MsgpackBufferValue<?> sample(final String skipped)
      throws ReflectiveOperationException {
    final var type = compiled.load("sample.Sample");
    final var value = type.getConstructor().newInstance();
    type.getMethod("fillExcept", String.class).invoke(value, skipped);
    return (MsgpackBufferValue<?>) value;
  }

  private static MsgpackBufferValue<?> unsetSample() throws ReflectiveOperationException {
    return (MsgpackBufferValue<?>) compiled.load("sample.Sample").getConstructor().newInstance();
  }

  private static byte[] write(final UnpackedObject value) {
    final var buffer = new ExpandableArrayBuffer();
    final int length = value.write(buffer, 0);
    return Arrays.copyOf(buffer.byteArray(), length);
  }

  private static byte[] write(final MsgpackBufferValue<?> value) {
    final var buffer = new ExpandableArrayBuffer();
    final int length = value.write(buffer, 0);
    return Arrays.copyOf(buffer.byteArray(), length);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void copy(final MsgpackBufferValue target, final MsgpackBufferValue source) {
    target.copyFrom(source);
  }
}
