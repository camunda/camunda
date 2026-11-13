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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.Result;
import io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.Source;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.util.Arrays;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Compares the properties without a default of a generated class with those of the {@link
 * UnpackedObject} they replace: both fail in the same way when a value is missing, and both store
 * the same bytes when it is set.
 */
final class GeneratedUnsetPropertiesTest {

  private static final String[] KEYS = {"key", "count", "name", "payload", "kind", "accepting"};

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
                import org.agrona.concurrent.UnsafeBuffer;
                public final class Legacy extends UnpackedObject {
                  private final LongProperty key = new LongProperty("key");
                  private final IntegerProperty count = new IntegerProperty("count");
                  private final StringProperty name = new StringProperty("name");
                  private final BinaryProperty payload = new BinaryProperty("payload");
                  private final EnumProperty<Kind> kind = new EnumProperty<>("kind", Kind.class);
                  private final BooleanProperty accepting = new BooleanProperty("accepting");
                  private final IntegerProperty other = new IntegerProperty("other", 5);
                  public Legacy() {
                    super(7);
                    declareProperty(key).declareProperty(count).declareProperty(name)
                        .declareProperty(payload).declareProperty(kind).declareProperty(accepting)
                        .declareProperty(other);
                  }
                  public Legacy fillExcept(final String skipped) {
                    if (!skipped.equals("key")) { key.setValue(7L); }
                    if (!skipped.equals("count")) { count.setValue(3); }
                    if (!skipped.equals("name")) { name.setValue("abc"); }
                    if (!skipped.equals("payload")) { payload.setValue(new UnsafeBuffer(new byte[] {1, 2})); }
                    if (!skipped.equals("kind")) { kind.setValue(Kind.TWO); }
                    if (!skipped.equals("accepting")) { accepting.setValue(false); }
                    other.setValue(9);
                    return this;
                  }
                }
                """),
            new Source(
                "sample.Sample",
                """
                package sample;
                import io.camunda.zeebe.msgpack.pojo.*;
                import org.agrona.DirectBuffer;
                import org.agrona.concurrent.UnsafeBuffer;
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

                  final RequiredLong key = new RequiredLong();
                  final RequiredInt count = new RequiredInt();
                  @Nullable DirectBuffer name;
                  @MsgpackProperty(binary = true) @Nullable DirectBuffer payload;
                  @Nullable Kind kind;
                  @Nullable Boolean accepting;
                  int other = 5;
                  @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();

                  public Sample fillExcept(final String skipped) {
                    if (!skipped.equals("key")) { key.set(7L); }
                    if (!skipped.equals("count")) { count.set(3); }
                    if (!skipped.equals("name")) { name = new UnsafeBuffer("abc".getBytes()); }
                    if (!skipped.equals("payload")) { payload = new UnsafeBuffer(new byte[] {1, 2}); }
                    if (!skipped.equals("kind")) { kind = Kind.TWO; }
                    if (!skipped.equals("accepting")) { accepting = false; }
                    other = 9;
                    return this;
                  }
                }
                """));
    assertThat(compiled.errors()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"key", "count", "name", "payload", "kind", "accepting"})
  void shouldFailToWriteLikeThePropertyTreeWhenAPropertyIsUnset(final String unset)
      throws Exception {
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
        .hasMessageContaining("Property '%s' is invalid".formatted(unset))
        .hasMessageContaining("to be set before writing, but has nothing");
  }

  @ParameterizedTest
  @ValueSource(strings = {"key", "count", "name", "payload", "kind", "accepting"})
  void shouldFailToGetTheLengthLikeThePropertyTreeWhenAPropertyIsUnset(final String unset)
      throws Exception {
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
        .hasMessageContaining("Property '%s' is invalid".formatted(unset));
  }

  @ParameterizedTest
  @ValueSource(strings = {"key", "count", "name", "payload", "kind", "accepting"})
  void shouldFailToReadLikeThePropertyTreeWhenTheKeyIsMissing(final String missing)
      throws Exception {
    // given a message of a fully set value, without one key
    final var full = write(sample(""));
    final var withoutKey = withoutKey(full, missing);

    // when
    final var legacyFailure =
        catchThrowable(
            () -> unsetLegacy().wrap(new UnsafeBuffer(withoutKey), 0, withoutKey.length));
    final var failure =
        catchThrowable(
            () -> unsetSample().wrap(new UnsafeBuffer(withoutKey), 0, withoutKey.length));

    // then
    assertThat(failure.getCause())
        .isExactlyInstanceOf(legacyFailure.getCause().getClass())
        .hasMessage(legacyFailure.getCause().getMessage())
        .hasMessage("Property '%s' has no valid value".formatted(missing));
  }

  @Test
  void shouldStoreTheSameBytesAsThePropertyTreeWhenEveryPropertyIsSet() throws Exception {
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
  void shouldReadWhatThePropertyTreeWrote() throws Exception {
    // given
    final var bytes = write(legacy(""));

    // when
    final var sample = unsetSample();
    sample.wrap(new UnsafeBuffer(bytes), 0, bytes.length);

    // then
    assertThat(write(sample)).isEqualTo(bytes);
    assertThat(sample).hasToString(legacy("").toString());
  }

  /**
   * ObjectValue leaves unset properties out of its string and keeps their comma, which is not valid
   * JSON; only BaseProperty#writeJSON prints the placeholder, and the generated class does as well.
   */
  @ParameterizedTest
  @ValueSource(strings = {"key", "count", "name", "payload", "kind", "accepting"})
  void shouldPrintAnUnsetPropertyAsNoValidWriteableValue(final String unset) throws Exception {
    // given
    final var sample = sample(unset);

    // when
    final var string = sample.toString();

    // then
    assertThat(string).contains("\"%s\":\"NO VALID WRITEABLE VALUE\"".formatted(unset));
    assertThat(string.split("NO VALID WRITEABLE VALUE", -1)).hasSize(2);
  }

  @Test
  void shouldBeUnsetAgainAfterAReset() throws Exception {
    // given
    final var sample = sample("");

    // when
    sample.reset();

    // then
    for (final var key : KEYS) {
      assertThat(sample.toString()).contains("\"%s\":\"NO VALID WRITEABLE VALUE\"".formatted(key));
    }
    assertThatThrownBy(sample::getLength).hasMessageContaining("Property 'key' is invalid");
  }

  @Test
  void shouldCopyUnsetAndSetPropertiesAlike() throws Exception {
    // given a target that is fully set, and a source in which one property is not
    final var source = sample("name");
    final var target = sample("");

    // when
    copy(target, source);

    // then the target is unset where the source is, and equal to it elsewhere
    assertThat(target.toString()).contains("\"name\":\"NO VALID WRITEABLE VALUE\"");
    assertThat(target).isEqualTo(source);
  }

  @Test
  void shouldTellApartUnsetAndSetValues() throws Exception {
    // given
    final var set = sample("");
    final var unset = sample("key");
    final var sameUnset = sample("key");
    final var otherUnset = sample("name");

    // then
    assertThat(set).isNotEqualTo(unset);
    assertThat(unset).isEqualTo(sameUnset).hasSameHashCodeAs(sameUnset);
    assertThat(unset).isNotEqualTo(otherUnset);
    assertThat(set).isEqualTo(sample("")).hasSameHashCodeAs(sample(""));
    assertThat(set.hashCode()).isNotEqualTo(unset.hashCode());
  }

  @Test
  void shouldTellApartAnUnsetHolderFromOneSetToZero() throws Exception {
    // given
    final var unset = sample("key");
    final var zero = sample("key");
    ((io.camunda.zeebe.msgpack.pojo.RequiredLong) field(zero, "key")).set(0L);

    // then
    assertThat(zero).isNotEqualTo(unset);
  }

  @Test
  void shouldFailToGetAnUnsetHolder() throws Exception {
    // given
    final var key = (io.camunda.zeebe.msgpack.pojo.RequiredLong) field(unsetSample(), "key");
    final var count = (io.camunda.zeebe.msgpack.pojo.RequiredInt) field(unsetSample(), "count");

    // when
    final ThrowingCallable getKey = key::get;
    final ThrowingCallable getCount = count::get;

    // then
    assertThatThrownBy(getKey)
        .hasMessage("Expected a value or default value to be specified, but has nothing");
    assertThatThrownBy(getCount).isInstanceOf(IllegalStateException.class);
    assertThat(key.isSet()).isFalse();
  }

  @Test
  void shouldRejectANullableOnAPrimitive() {
    // when
    final var result = compile(pojo("@Nullable long key;"));

    // then
    assertThat(result.errors()).anyMatch(e -> e.contains("key") && e.contains("@Nullable"));
  }

  @Test
  void shouldRejectANullableHolder() {
    // when
    final var result = compile(pojo("@Nullable final RequiredLong key = new RequiredLong();"));

    // then
    assertThat(result.errors()).anyMatch(e -> e.contains("key") && e.contains("@Nullable"));
  }

  @Test
  void shouldRejectAHolderThatIsNotFinal() {
    // when
    final var result = compile(pojo("RequiredLong key = new RequiredLong();"));

    // then
    assertThat(result.errors()).anyMatch(e -> e.contains("key") && e.contains("final"));
  }

  @Test
  void shouldRejectABoxedBooleanThatIsNotNullable() {
    // when
    final var result = compile(pojo("Boolean accepting;"));

    // then
    assertThat(result.errors()).anyMatch(e -> e.contains("accepting") && e.contains("@Nullable"));
  }

  @Test
  void shouldRejectANullableUnsafeBuffer() {
    // when
    final var result =
        compile(pojo("@Nullable final UnsafeBuffer name = new UnsafeBuffer(new byte[0]);"));

    // then
    assertThat(result.errors()).anyMatch(e -> e.contains("name") && e.contains("@Nullable"));
  }

  private static Source pojo(final String fields) {
    return new Source(
        "sample.Bad",
        """
        package sample;
        import io.camunda.zeebe.msgpack.pojo.*;
        import org.agrona.DirectBuffer;
        import org.agrona.concurrent.UnsafeBuffer;
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

  /** A new legacy twin with every property set except the skipped key, "" skips none. */
  private static UnpackedObject legacy(final String skipped) throws ReflectiveOperationException {
    final var type = compiled.load("sample.Legacy");
    final var value = type.getConstructor().newInstance();
    type.getMethod("fillExcept", String.class).invoke(value, skipped);
    return (UnpackedObject) value;
  }

  private static UnpackedObject unsetLegacy() throws ReflectiveOperationException {
    return (UnpackedObject) compiled.load("sample.Legacy").getConstructor().newInstance();
  }

  /** A new sample with every property set except the skipped key, "" skips none. */
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

  private static Object field(final Object sample, final String name)
      throws ReflectiveOperationException {
    return InMemoryCompiler.get(sample, name);
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

  /** The same map without one entry; every value of the sample is a single small entry. */
  private static byte[] withoutKey(final byte[] message, final String key) {
    final var reader = new io.camunda.zeebe.msgpack.spec.MsgPackReader();
    reader.wrap(new UnsafeBuffer(message), 0, message.length);
    final int size = reader.readMapHeader();
    final var out = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(out, 0);
    writer.writeMapHeader(size - 1);
    for (int i = 0; i < size; i++) {
      final int start = reader.getOffset();
      final int keyLength = reader.readStringLength();
      final int keyOffset = reader.getOffset();
      reader.skipBytes(keyLength);
      final var name = reader.getBuffer().getStringWithoutLengthUtf8(keyOffset, keyLength);
      reader.skipValue();
      if (!name.equals(key)) {
        writer.writeRaw(reader.getBuffer(), start, reader.getOffset() - start);
      }
    }
    return Arrays.copyOf(out.byteArray(), writer.getOffset());
  }
}
