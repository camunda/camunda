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
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
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
 * {@link LegacyBigRecord} it replaces: same bytes in both directions, same failures and the same
 * string when a property without a default is unset.
 */
final class BigRecordTest {

  private static final List<String> REQUIRED =
      List.of("key", "parentKey", "partitionId", "active", "kind", "name", "data");

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
    assertThat(generated)
        .isEqualTo(variant.filledExcept(""))
        .hasSameHashCodeAs(variant.filledExcept(""));
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
    assertThat(generated)
        .isEqualTo(variant.filledExcept(""))
        .hasToString(legacyExcept("").toString());
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
    assertThat(generated.toString()).doesNotContain("\"key\":", "\"active\":", "\"data\":");
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
    final var legacyLengthFailure = catchThrowable(legacy::getLength);
    final var lengthFailure = catchThrowable(generated::getLength);

    // then
    assertThat(failure)
        .isExactlyInstanceOf(legacyFailure.getClass())
        .hasMessage(legacyFailure.getMessage())
        .hasMessageContaining("Property '%s' is invalid".formatted(unset));
    assertThat(lengthFailure)
        .isExactlyInstanceOf(legacyLengthFailure.getClass())
        .hasMessage(legacyLengthFailure.getMessage());
  }

  @ParameterizedTest
  @MethodSource("unsetCases")
  void shouldFailToReadLikeTheLegacyRecordWhenARequiredKeyIsMissing(
      final Variant variant, final String missing) {
    // given a message of a fully set record, without one key
    final var message = withoutKey(write(legacyExcept("")), missing);

    // when
    final var legacyFailure =
        catchThrowable(
            () -> new LegacyBigRecord().wrap(new UnsafeBuffer(message), 0, message.length));
    final var failure =
        catchThrowable(
            () -> variant.filledExcept("").wrap(new UnsafeBuffer(message), 0, message.length));

    // then
    assertThat(failure.getCause())
        .isExactlyInstanceOf(legacyFailure.getCause().getClass())
        .hasMessage(legacyFailure.getCause().getMessage())
        .hasMessage("Property '%s' has no valid value".formatted(missing));
  }

  @ParameterizedTest
  @MethodSource("unsetCases")
  void shouldPrintCopyAndCompareLikeTheLegacyRecordWhenARequiredPropertyIsUnset(
      final Variant variant, final String unset) {
    // given
    final var legacy = legacyExcept(unset);
    final var generated = variant.filledExcept(unset);
    final var target = variant.filledExcept("");

    // when
    copy(target, generated);
    final var legacyFailure = catchThrowable(() -> legacy.equals(legacyExcept(unset)));
    final var failure = catchThrowable(() -> generated.equals(variant.filledExcept(unset)));

    // then like ObjectValue, the unset property is left out of the string
    assertThat(generated.toString())
        .isEqualTo(legacy.toString())
        .doesNotContain("\"%s\"".formatted(unset));
    assertThat(target).hasToString(generated.toString());
    assertThat(failure)
        .isExactlyInstanceOf(legacyFailure.getClass())
        .hasMessage(legacyFailure.getMessage());
    assertThat(catchThrowable(() -> variant.filledExcept("").equals(generated)))
        .hasMessage(legacyFailure.getMessage());
    assertThat(generated.hashCode()).isNotEqualTo(variant.filledExcept("").hashCode());
  }

  @ParameterizedTest
  @EnumSource(Variant.class)
  void shouldFailToGetAnUnsetLongOrIntLikeTheLegacyRecord(final Variant variant) {
    // given
    final var legacy = legacyExcept("key");
    final var generated = (BigRecord) variant.filledExcept("key");
    final var noPartition = (BigRecord) variant.filledExcept("partitionId");

    // when
    final var legacyFailure = catchThrowable(legacy::getKey);
    final var failure = catchThrowable(generated::getKey);
    final var intFailure = catchThrowable(noPartition::getPartitionId);

    // then the holder only throws an IllegalStateException: it does not know the property key
    if (variant == Variant.HOLDER) {
      assertThat(failure)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Expected a value or default value to be specified, but has nothing");
      assertThat(intFailure).isInstanceOf(IllegalStateException.class);
    } else {
      assertThat(failure)
          .isExactlyInstanceOf(legacyFailure.getClass())
          .hasMessage(legacyFailure.getMessage());
      assertThat(intFailure)
          .isExactlyInstanceOf(legacyFailure.getClass())
          .hasMessageContaining("'partitionId'");
    }
  }

  /** A non-nullable enum has a default, but cannot be written once it is assigned null. */
  @ParameterizedTest
  @EnumSource(Variant.class)
  void shouldFailToWriteANullEnumThatHasADefault(final Variant variant) throws Exception {
    // given
    final var generated = variant.filledExcept("");
    final var state = generated.getClass().getDeclaredField("state");
    state.setAccessible(true);
    state.set(generated, null);

    // when
    final var failure = catchThrowable(() -> write(generated));
    final var lengthFailure = catchThrowable(generated::getLength);

    // then
    assertThat(failure)
        .hasMessage(
            "Property 'state' is invalid: Expected a value or default value to be set before writing, but has nothing");
    assertThat(lengthFailure)
        .hasMessage(
            "Property 'state' is invalid: Expected a value or default value to be specified, but has nothing");
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

  /** The same map without the entry of one key. */
  private static byte[] withoutKey(final byte[] message, final String key) {
    final var reader = new MsgPackReader();
    reader.wrap(new UnsafeBuffer(message), 0, message.length);
    final int size = reader.readMapHeader();
    final var out = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(out, 0);
    writer.writeMapHeader(size - 1);
    for (int i = 0; i < size; i++) {
      final int start = reader.getOffset();
      final int keyLength = reader.readStringLength();
      final var name = reader.getBuffer().getStringWithoutLengthUtf8(reader.getOffset(), keyLength);
      reader.skipBytes(keyLength);
      reader.skipValue();
      if (!name.equals(key)) {
        writer.writeRaw(reader.getBuffer(), start, reader.getOffset() - start);
      }
    }
    return Arrays.copyOf(out.byteArray(), writer.getOffset());
  }
}
