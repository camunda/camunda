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
 * Compares {@code @Nullable Long} and {@code @Nullable Integer} fields, and a non-nullable enum
 * that is null, with the legacy properties they replace.
 */
final class GeneratedBoxedPropertiesTest {

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

                  @Nullable Long key;
                  @Nullable Integer count;
                  Kind kind = Kind.ONE;
                  @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();

                  public Sample fillExcept(final String skipped) {
                    if (!skipped.equals("key")) { key = Long.MIN_VALUE; }
                    if (!skipped.equals("count")) { count = -3; }
                    kind = skipped.equals("kind") ? null : Kind.TWO;
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
  void shouldPrintUnsetBoxedPropertiesAndBeUnsetAfterAReset() throws Exception {
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
  void shouldCopyAndCompareBoxedPropertiesByValue() throws Exception {
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
  void shouldRejectBoxedPropertiesThatAreNotNullable() {
    // when
    final var long1 = compile(pojo("Long key;"));
    final var int1 = compile(pojo("Integer count;"));

    // then
    assertThat(long1.errors()).anyMatch(e -> e.contains("key") && e.contains("@Nullable"));
    assertThat(int1.errors()).anyMatch(e -> e.contains("count") && e.contains("@Nullable"));
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
