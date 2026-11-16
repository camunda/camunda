/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.real;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A big record with every supported field type, compiled by the build's annotation processor in
 * three variants (holder, boxed, sentinel for the required longs and ints) and compared with the
 * {@link LegacyBigRecord} it replaces: same bytes in both directions, same failures when a required
 * property is unset.
 */
final class BigRecordTest {

  private static final List<String> REQUIRED =
      List.of("key", "parentKey", "partitionId", "active", "kind", "name");

  enum Variant {
    HOLDER(HolderBigRecord::new),
    BOXED(BoxedBigRecord::new),
    SENTINEL(SentinelBigRecord::new);

    private final Supplier<? extends MsgpackBufferValue<?>> factory;

    Variant(final Supplier<? extends MsgpackBufferValue<?>> factory) {
      this.factory = factory;
    }

    MsgpackBufferValue<?> filledExcept(final String skipped) {
      final var record = factory.get();
      ((BigRecord) record).fillExcept(skipped);
      return record;
    }
  }

  @ParameterizedTest
  @EnumSource(Variant.class)
  void shouldStoreTheSameBytesAsTheLegacyRecord(final Variant variant) {
    // given
    final var legacy = legacyExcept("");
    final var generated = variant.filledExcept("");

    // when
    final var bytes = write(generated);

    // then
    assertThat(bytes).isEqualTo(write(legacy));
    assertThat(generated.getLength()).isEqualTo(legacy.getLength()).isEqualTo(bytes.length);
  }

  @ParameterizedTest
  @EnumSource(Variant.class)
  void shouldReadWhatTheLegacyRecordWrote(final Variant variant) {
    // given
    final var bytes = write(legacyExcept(""));
    final var generated = variant.filledExcept("key");

    // when
    generated.wrap(new UnsafeBuffer(bytes), 0, bytes.length);

    // then
    assertThat(write(generated)).isEqualTo(bytes);
    assertThat(generated).isEqualTo(variant.filledExcept(""));
  }

  @ParameterizedTest
  @EnumSource(Variant.class)
  void shouldBeReadByTheLegacyRecord(final Variant variant) {
    // given
    final var bytes = write(variant.filledExcept(""));
    final var legacy = legacyExcept("key");

    // when
    legacy.wrap(new UnsafeBuffer(bytes), 0, bytes.length);

    // then
    assertThat(write(legacy)).isEqualTo(bytes);
  }

  @ParameterizedTest
  @EnumSource(Variant.class)
  void shouldCopyDeeply(final Variant variant) {
    // given
    final var source = variant.filledExcept("");
    final var expected = write(source);
    final var target = variant.filledExcept("key");

    // when
    copy(target, source);
    source.reset();

    // then
    assertThat(write(target)).isEqualTo(expected);
  }

  @ParameterizedTest
  @EnumSource(Variant.class)
  void shouldBeUnsetAfterAReset(final Variant variant) {
    // given
    final var generated = variant.filledExcept("");

    // when
    generated.reset();

    // then
    assertThat(catchThrowable(() -> write(generated))).hasMessageContaining("is invalid");
  }

  @ParameterizedTest
  @MethodSource("unsetCases")
  void shouldFailToWriteLikeTheLegacyRecordWhenARequiredPropertyIsUnset(
      final Variant variant, final String unset) {
    // given
    final var legacy = legacyExcept(unset);
    final var generated = variant.filledExcept(unset);

    // when
    final var legacyFailure = catchThrowable(() -> write(legacy));
    final var failure = catchThrowable(() -> write(generated));

    // then
    assertThat(failure)
        .isExactlyInstanceOf(legacyFailure.getClass())
        .hasMessage(legacyFailure.getMessage())
        .hasMessageContaining("Property '%s' is invalid".formatted(unset));
  }

  @ParameterizedTest
  @MethodSource("unsetCases")
  void shouldFailToGetTheLengthLikeTheLegacyRecordWhenARequiredPropertyIsUnset(
      final Variant variant, final String unset) {
    // given
    final var legacy = legacyExcept(unset);
    final var generated = variant.filledExcept(unset);

    // when
    final var legacyFailure = catchThrowable(legacy::getLength);
    final var failure = catchThrowable(generated::getLength);

    // then
    assertThat(failure)
        .isExactlyInstanceOf(legacyFailure.getClass())
        .hasMessage(legacyFailure.getMessage());
  }

  @ParameterizedTest
  @EnumSource(Variant.class)
  void shouldFailToGetAnUnsetLongLikeTheLegacyRecord(final Variant variant) {
    // given
    final var legacy = legacyExcept("key");
    final var generated = (BigRecord) variant.filledExcept("key");

    // when
    final var legacyFailure = catchThrowable(legacy::getKey);
    final var failure = catchThrowable(generated::getKey);

    // then the holder only throws an IllegalStateException: it does not know the property key
    if (variant == Variant.HOLDER) {
      assertThat(failure).isInstanceOf(IllegalStateException.class);
    } else {
      assertThat(failure)
          .isExactlyInstanceOf(legacyFailure.getClass())
          .hasMessage(legacyFailure.getMessage());
    }
  }

  @ParameterizedTest
  @EnumSource(Variant.class)
  void shouldFailToGetAnUnsetIntLikeTheLegacyRecord(final Variant variant) {
    // given
    final var legacy = legacyExcept("partitionId");
    final var generated = (BigRecord) variant.filledExcept("partitionId");

    // when
    final var legacyFailure = catchThrowable(legacy::getPartitionId);
    final var failure = catchThrowable(generated::getPartitionId);

    // then the holder only throws an IllegalStateException: it does not know the property key
    if (variant == Variant.HOLDER) {
      assertThat(failure).isInstanceOf(IllegalStateException.class);
    } else {
      assertThat(failure)
          .isExactlyInstanceOf(legacyFailure.getClass())
          .hasMessage(legacyFailure.getMessage());
    }
  }

  static Stream<Arguments> unsetCases() {
    return Stream.of(Variant.values())
        .flatMap(v -> REQUIRED.stream().map(field -> Arguments.of(v, field)));
  }

  private static LegacyBigRecord legacyExcept(final String skipped) {
    final var legacy = new LegacyBigRecord();
    legacy.fillExcept(skipped);
    return legacy;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void copy(final MsgpackBufferValue target, final MsgpackBufferValue source) {
    target.copyFrom(source);
  }

  private static byte[] write(final BufferWriter value) {
    final var buffer = new ExpandableArrayBuffer();
    final int length = value.write(buffer, 0);
    return Arrays.copyOf(buffer.byteArray(), length);
  }
}
