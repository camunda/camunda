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
import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.set;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.Recyclable;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.pojo.Codecs;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.Result;
import io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.Source;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.collections.IntArrayList;
import org.agrona.collections.LongArrayList;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Checks the operations of the generated {@code <Name>Ops} and what {@link MsgpackBufferValue}
 * delegates to them, on a class with every kind of property. Messages are written by hand, so the
 * tests do not depend on the code they check.
 */
final class GeneratedOpsTest {

  private static final String IMPORTS =
      """
      package sample;
      import io.camunda.zeebe.msgpack.pojo.*;
      import java.util.*;
      import org.agrona.DirectBuffer;
      import org.agrona.collections.IntArrayList;
      import org.agrona.collections.LongArrayList;
      import org.agrona.concurrent.UnsafeBuffer;
      """;

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
                      @Override
                      protected io.camunda.zeebe.msgpack.pojo.MsgpackCodec<Inner> codec() {
                        return InnerCodec.INSTANCE;
                      }

                      @Override
                      protected io.camunda.zeebe.msgpack.pojo.MsgpackOps<Inner> ops() {
                        return InnerOps.INSTANCE;
                      }

                      int value = 3;
                      @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();
                    }
                    """),
            new Source(
                "sample.Legacy",
                """
                package sample;
                import io.camunda.zeebe.msgpack.UnpackedObject;
                import io.camunda.zeebe.msgpack.property.IntegerProperty;
                public final class Legacy extends UnpackedObject {
                  private final IntegerProperty value = new IntegerProperty("value", 3);
                  public Legacy() {
                    super(1);
                    declareProperty(value);
                  }
                }
                """),
            new Source(
                "sample.Secret",
                IMPORTS
                    + """
                    @MsgpackPojo
                    public final class Secret extends MsgpackBufferValue<Secret> {
                      @Override
                      protected io.camunda.zeebe.msgpack.pojo.MsgpackCodec<Secret> codec() {
                        return SecretCodec.INSTANCE;
                      }

                      @Override
                      protected io.camunda.zeebe.msgpack.pojo.MsgpackOps<Secret> ops() {
                        return SecretOps.INSTANCE;
                      }

                      long id = 1;
                      @MsgpackSanitized DirectBuffer password = Decoding.EMPTY;
                      @MsgpackSanitized int pin = 4;
                      @MsgpackSanitized final List<DirectBuffer> codes = new ArrayList<>();
                      @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();
                    }
                    """),
            new Source(
                "sample.Holder",
                IMPORTS
                    + """
                    @MsgpackPojo
                    public final class Holder extends MsgpackBufferValue<Holder> {
                      @Override
                      protected io.camunda.zeebe.msgpack.pojo.MsgpackCodec<Holder> codec() {
                        return HolderCodec.INSTANCE;
                      }

                      @Override
                      protected io.camunda.zeebe.msgpack.pojo.MsgpackOps<Holder> ops() {
                        return HolderOps.INSTANCE;
                      }

                      final Secret secret = new Secret();
                      final List<Secret> secrets = new ArrayList<>();
                      @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();
                    }
                    """),
            new Source(
                "sample.Sample",
                IMPORTS
                    + """
                    @MsgpackPojo
                    public final class Sample extends MsgpackBufferValue<Sample> {
                      @Override
                      protected io.camunda.zeebe.msgpack.pojo.MsgpackCodec<Sample> codec() {
                        return SampleCodec.INSTANCE;
                      }

                      @Override
                      protected io.camunda.zeebe.msgpack.pojo.MsgpackOps<Sample> ops() {
                        return SampleOps.INSTANCE;
                      }

                      long key = -1L;
                      int count = 7;
                      boolean flag = true;
                      DirectBuffer name = Decoding.EMPTY;
                      @MsgpackProperty(binary = true) DirectBuffer payload = Decoding.EMPTY;
                      @MsgpackProperty(raw = true) DirectBuffer raw = new UnsafeBuffer(new byte[] {(byte) 0xc0});
                      @MsgpackProperty(document = true) DirectBuffer doc = new UnsafeBuffer(new byte[] {(byte) 0x80});
                      final UnsafeBuffer fixed = new UnsafeBuffer(new byte[0]);
                      Kind kind = Kind.TWO;
                      final List<DirectBuffer> ids = new ArrayList<>();
                      final List<Long> versions = new ArrayList<>();
                      final IntArrayList counts = new IntArrayList();
                      final LongArrayList keys = new LongArrayList();
                      final Inner inner = new Inner();
                      final Legacy legacy = new Legacy();
                      final List<Inner> items = new ArrayList<>();
                      final List<Legacy> legacyItems = new ArrayList<>();
                      @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();
                      @MsgpackIgnore int ignored;
                    }
                    """));
    assertThat(compiled.errors()).isEmpty();
  }

  @Test
  void shouldCopyEveryKindOfProperty() throws Exception {
    // given
    final var source = read(message(0));
    final var target = instance();

    // when
    copy(source, target);

    // then the target stores exactly what the source stores
    assertThat(bytes(target)).isEqualTo(bytes(source));
    assertThat(length(target)).isEqualTo(length(source)).isEqualTo(bytes(source).length);
    assertThat(get(target, "key")).isEqualTo(42L);
    assertThat(get(get(target, "inner"), "value")).isEqualTo(9);
  }

  @Test
  void shouldNotShareAnythingWithTheSource() throws Exception {
    // given
    final var buffer = new UnsafeBuffer(message(0));
    final var source = instance();
    wrap(source, buffer);
    final var expected = bytes(source);
    final var target = instance();

    // when the source is copied, and then its buffer is reused and the source itself is reset
    copy(source, target);
    buffer.setMemory(0, buffer.capacity(), (byte) 0xc1);
    ((MsgpackBufferValue<?>) source).reset();

    // then the target still stores the copied value
    assertThat(bytes(target)).isEqualTo(expected);
  }

  @Test
  void shouldKeepNothingOfWhatTheTargetHeldBefore() throws Exception {
    // given a target that holds another value, with longer lists and other keys
    final var target = read(message(1));
    final var source = read(message(0));

    // when
    copy(source, target);

    // then
    assertThat(bytes(target)).isEqualTo(bytes(source));
  }

  @Test
  void shouldCopyAnInstanceThatHoldsOnlyDefaults() throws Exception {
    // given
    final var source = instance();
    final var target = read(message(1));

    // when
    copy(source, target);

    // then
    assertThat(bytes(target)).isEqualTo(bytes(instance()));
  }

  @Test
  void shouldLeaveStateThatIsNotStoredAlone() throws Exception {
    // given
    final var source = read(message(0));
    final var target = read(message(1));
    set(source, "ignored", 5);
    set(target, "ignored", 9);

    // when
    copy(source, target);

    // then
    assertThat(get(target, "ignored")).isEqualTo(9);
  }

  @Test
  void shouldKeepTheValueWhenCopiedOntoItself() throws Exception {
    // given
    final var source = read(message(0));
    final var expected = bytes(source);

    // when
    copy(source, source);

    // then
    assertThat(bytes(source)).isEqualTo(expected);
  }

  @Test
  void shouldCopyThroughTheOpsOfTheClass() throws Exception {
    // given
    final var type = compiled.load("sample.Sample");
    @SuppressWarnings({"unchecked", "rawtypes"})
    final MsgpackOps<Recyclable> ops = (MsgpackOps) Codecs.opsOf((Class) type);
    final var source = read(message(0));
    final var target = instance();

    // when
    ops.copy((Recyclable) source, (Recyclable) target);

    // then
    assertThat(bytes(target)).isEqualTo(bytes(source));
  }

  @Test
  void shouldBeEqualToAnIndependentInstanceWithTheSameContent() throws Exception {
    // given two instances read from different buffers
    final var a = read(message(0));
    final var b = read(message(0));

    // then
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotSameAs(b);
  }

  @Test
  void shouldBeEqualToACopy() throws Exception {
    // given
    final var source = read(message(0));
    final var target = read(message(1));

    // when
    copy(source, target);

    // then
    assertThat(target).isEqualTo(source).hasSameHashCodeAs(source);
  }

  @Test
  void shouldDifferWhenAnyStoredPropertyDiffers() throws Exception {
    // given
    final var mutations = mutations();
    assertThat(mutations).hasSize(19);

    for (final var mutation : mutations.entrySet()) {
      final var a = read(message(0));
      final var b = read(message(0));

      // when
      mutation.getValue().accept(b);

      // then
      assertThat(a).as("%s changed", mutation.getKey()).isNotEqualTo(b);
      assertThat(b).as("%s changed", mutation.getKey()).isNotEqualTo(a);
    }
  }

  @Test
  void shouldIgnoreStateThatIsNotStoredWhenComparing() throws Exception {
    // given
    final var a = read(message(0));
    final var b = read(message(0));

    // when
    set(b, "ignored", 5);

    // then
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
  }

  @Test
  void shouldNotBeEqualToNullOrAnotherClass() throws Exception {
    // given
    final var sample = read(message(0));

    // then
    assertThat(sample).isNotEqualTo(null).isNotEqualTo("sample");
    assertThat(sample).isNotEqualTo(compiled.load("sample.Inner").getConstructor().newInstance());
  }

  @Test
  void shouldCompareThroughTheOpsOfTheClass() throws Exception {
    // given
    final var type = compiled.load("sample.Sample");
    @SuppressWarnings({"unchecked", "rawtypes"})
    final MsgpackOps<Recyclable> ops = (MsgpackOps) Codecs.opsOf((Class) type);
    final var a = (Recyclable) read(message(0));
    final var b = (Recyclable) read(message(0));
    final var other = (Recyclable) read(message(1));

    // then
    assertThat(ops.equals(a, b)).isTrue();
    assertThat(ops.hashCode(a)).isEqualTo(ops.hashCode(b));
    assertThat(ops.equals(a, other)).isFalse();
    assertThat(ops.equals(a, a)).isTrue();
  }

  @Test
  void shouldCompareBuffersByBytesWhateverTheirClass() throws Exception {
    for (final var field : List.of("name", "payload", "raw", "doc")) {
      // given
      final var a = instance();
      final var b = instance();
      final var expandable = new ExpandableArrayBuffer(1);
      expandable.putByte(0, (byte) 0x80);
      set(a, field, expandable);
      set(b, field, new UnsafeBuffer(new byte[] {(byte) 0x80}));

      // then
      assertThat(a).as(field).isEqualTo(b).hasSameHashCodeAs(b);
      assertThat(b).as(field).isEqualTo(a);
    }
  }

  @Test
  void shouldCompareBufferListsByBytesWhateverTheirClass() throws Exception {
    // given
    final var a = instance();
    final var b = instance();
    final var expandable = new ExpandableArrayBuffer(1);
    expandable.putByte(0, (byte) 'x');
    ((List<DirectBuffer>) get(a, "ids")).add(expandable);
    ((List<DirectBuffer>) get(b, "ids")).add(utf8("x"));

    // then
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
  }

  @Test
  void shouldPrintTheStoredPropertiesAsJson() throws Exception {
    // given
    final var sample = read(message(0));
    final var base64 = java.util.Base64.getEncoder();

    // when
    final var string = sample.toString();

    // then the format of ObjectValue, with the unknown entries last
    assertThat(string)
        .isEqualTo(
            "{\"key\":42,\"count\":8,\"flag\":false,\"name\":\"zeebe\",\"payload\":\""
                + base64.encodeToString("bin".getBytes(StandardCharsets.UTF_8))
                + "\",\"raw\":[packed value (length=3)],\"doc\":\""
                + base64.encodeToString(
                    encoded(
                        d -> {
                          d.writeMapHeader(1);
                          d.writeString(utf8("a"));
                          d.writeInteger(1);
                        }))
                + "\",\"fixed\":\"fx\",\"kind\":\"ONE\",\"ids\":[\"a\",\"b\"],"
                + "\"versions\":[1,300,5000000000],\"counts\":[1,70000],"
                + "\"keys\":[5,-6000000000],"
                + "\"inner\":{\"value\":9,\"itemFuture\":[packed value (length=1)]},"
                + "\"legacy\":{\"value\":11},"
                + "\"items\":[{\"value\":1},{\"value\":2,\"itemFuture\":[packed value (length=1)]}],"
                + "\"legacyItems\":[{\"value\":5}],"
                + "\"future\":[packed value (length=1)]}");
  }

  @Test
  void shouldPrintTheDefaultsOfANewInstance() throws Exception {
    // when
    final var string = instance().toString();

    // then
    assertThat(string)
        .startsWith("{\"key\":-1,\"count\":7,\"flag\":true,\"name\":\"\",")
        .contains("\"doc\":\"gA==\"")
        .contains("\"kind\":\"TWO\"")
        .endsWith("\"items\":[],\"legacyItems\":[]}");
  }

  @Test
  void shouldMaskSanitizedFieldsInTheString() throws Exception {
    // given
    final var secret = secret("hunter2", 1234, "a", "b");

    // when
    final var string = secret.toString();

    // then
    assertThat(string)
        .isEqualTo("{\"id\":7,\"password\":\"***\",\"pin\":\"***\",\"codes\":\"***\"}")
        .doesNotContain("hunter2");
  }

  @Test
  void shouldMaskSanitizedFieldsOfNestedValues() throws Exception {
    // given a holder of a secret and of a list of secrets
    final var holder = compiled.load("sample.Holder").getConstructor().newInstance();
    final var secret = secret("hunter2", 1234);
    copy(secret, get(holder, "secret"));
    @SuppressWarnings("unchecked")
    final var secrets = (List<Object>) get(holder, "secrets");
    secrets.add(secret);

    // when
    final var string = holder.toString();

    // then
    assertThat(string)
        .isEqualTo(
            "{\"secret\":{\"id\":7,\"password\":\"***\",\"pin\":\"***\",\"codes\":\"***\"},"
                + "\"secrets\":[{\"id\":7,\"password\":\"***\",\"pin\":\"***\",\"codes\":\"***\"}]}");
  }

  @Test
  void shouldStillStoreSanitizedFields() throws Exception {
    // given
    final var secret = secret("hunter2", 1234, "a");

    // when it is written and read back
    final var read = compiled.load("sample.Secret").getConstructor().newInstance();
    wrap(read, new UnsafeBuffer(bytes(secret)));

    // then the value is there, only the string hides it
    assertThat(get(read, "password")).isEqualTo(utf8("hunter2"));
    assertThat(get(read, "pin")).isEqualTo(1234);
    assertThat(read).isEqualTo(secret);
  }

  @Test
  void shouldPrintThroughTheOpsOfTheClass() throws Exception {
    // given
    final var type = compiled.load("sample.Sample");
    @SuppressWarnings({"unchecked", "rawtypes"})
    final MsgpackOps<Recyclable> ops = (MsgpackOps) Codecs.opsOf((Class) type);
    final var sample = read(message(0));

    // then
    assertThat(ops.toString((Recyclable) sample)).isEqualTo(sample.toString());
  }

  private static Object secret(final String password, final int pin, final String... codes)
      throws Exception {
    final var secret = compiled.load("sample.Secret").getConstructor().newInstance();
    set(secret, "id", 7L);
    set(secret, "password", utf8(password));
    set(secret, "pin", pin);
    @SuppressWarnings("unchecked")
    final var list = (List<DirectBuffer>) get(secret, "codes");
    for (final var code : codes) {
      list.add(utf8(code));
    }
    return secret;
  }

  /** One change per stored property of the sample, each applied to an instance of message 0. */
  @SuppressWarnings("unchecked")
  private static Map<String, ThrowingConsumer<Object>> mutations() {
    final var m = new LinkedHashMap<String, ThrowingConsumer<Object>>();
    m.put("key", x -> set(x, "key", 43L));
    m.put("count", x -> set(x, "count", 9));
    m.put("flag", x -> set(x, "flag", true));
    m.put("name", x -> set(x, "name", utf8("other")));
    m.put("payload", x -> set(x, "payload", utf8("other")));
    m.put("raw", x -> set(x, "raw", new UnsafeBuffer(new byte[] {1})));
    m.put(
        "doc",
        x ->
            set(
                x,
                "doc",
                new UnsafeBuffer(
                    encoded(
                        d -> {
                          d.writeMapHeader(1);
                          d.writeString(utf8("a"));
                          d.writeInteger(5);
                        }))));
    m.put("fixed", x -> ((UnsafeBuffer) get(x, "fixed")).wrap(new byte[] {1}));
    m.put("kind", x -> set(x, "kind", compiled.load("sample.Kind").getEnumConstants()[1]));
    m.put("ids", x -> ((List<DirectBuffer>) get(x, "ids")).add(utf8("z")));
    m.put("versions", x -> ((List<Long>) get(x, "versions")).add(9L));
    m.put("counts", x -> ((IntArrayList) get(x, "counts")).addInt(3));
    m.put("keys", x -> ((LongArrayList) get(x, "keys")).addLong(3));
    m.put("inner", x -> set(get(x, "inner"), "value", 99));
    m.put(
        "legacy",
        x ->
            ((UnpackedObject) get(x, "legacy"))
                .wrap(new UnsafeBuffer(encoded(w -> item(w, 99, false)))));
    m.put(
        "items",
        x ->
            ((List<Object>) get(x, "items"))
                .add(compiled.load("sample.Inner").getConstructor().newInstance()));
    m.put(
        "legacyItems",
        x ->
            ((List<Object>) get(x, "legacyItems"))
                .add(compiled.load("sample.Legacy").getConstructor().newInstance()));
    m.put(
        "unknown",
        x -> {
          final var entry =
              encoded(
                  w -> {
                    w.writeString(utf8("zz"));
                    w.writeInteger(1);
                  });
          ((UnknownProperties) get(x, "unknown")).append(new UnsafeBuffer(entry), 0, entry.length);
        });
    m.put("item of a list", x -> set(((List<Object>) get(x, "items")).get(0), "value", 99));
    return m;
  }

  // ---------------------------------------------------------------- helpers

  /** A message with every key in declaration order; variant 1 has other values and no unknowns. */
  private static byte[] message(final int variant) {
    final boolean v0 = variant == 0;
    return encoded(
        w -> {
          w.writeMapHeader(v0 ? 18 : 17);
          entry(w, "key", () -> w.writeInteger(v0 ? 42 : -5));
          entry(w, "count", () -> w.writeInteger(v0 ? 8 : 70_000));
          entry(w, "flag", () -> w.writeBoolean(!v0));
          entry(w, "name", () -> w.writeString(utf8(v0 ? "zeebe" : "a much longer name")));
          entry(w, "payload", () -> w.writeBinary(utf8(v0 ? "bin" : "other")));
          entry(w, "raw", () -> w.writeInteger(v0 ? 256 : 1));
          entry(
              w,
              "doc",
              () -> {
                w.writeBinary(
                    new UnsafeBuffer(
                        encoded(
                            d -> {
                              d.writeMapHeader(1);
                              d.writeString(utf8("a"));
                              d.writeInteger(v0 ? 1 : 2);
                            })));
              });
          entry(w, "fixed", () -> w.writeString(utf8(v0 ? "fx" : "fixed value")));
          entry(w, "kind", () -> w.writeString(utf8(v0 ? "ONE" : "TWO")));
          entry(
              w,
              "ids",
              () -> {
                w.writeArrayHeader(v0 ? 2 : 3);
                w.writeString(utf8("a"));
                w.writeString(utf8("b"));
                if (!v0) {
                  w.writeString(utf8("c"));
                }
              });
          entry(
              w,
              "versions",
              () -> {
                w.writeArrayHeader(3);
                w.writeInteger(1);
                w.writeInteger(300);
                w.writeInteger(v0 ? 5_000_000_000L : 2);
              });
          entry(
              w,
              "counts",
              () -> {
                w.writeArrayHeader(v0 ? 2 : 1);
                w.writeInteger(1);
                if (v0) {
                  w.writeInteger(70_000);
                }
              });
          entry(
              w,
              "keys",
              () -> {
                w.writeArrayHeader(2);
                w.writeInteger(5);
                w.writeInteger(v0 ? -6_000_000_000L : 6);
              });
          entry(w, "inner", () -> item(w, v0 ? 9 : 10, v0));
          entry(w, "legacy", () -> item(w, v0 ? 11 : 12, false));
          entry(
              w,
              "items",
              () -> {
                w.writeArrayHeader(v0 ? 2 : 3);
                item(w, 1, false);
                item(w, 2, v0);
                if (!v0) {
                  item(w, 3, false);
                }
              });
          entry(
              w,
              "legacyItems",
              () -> {
                w.writeArrayHeader(1);
                item(w, v0 ? 5 : 6, false);
              });
          if (v0) {
            entry(w, "future", () -> w.writeInteger(7));
          }
        });
  }

  /** A map {@code {"value": value}}, with an unknown key if requested. */
  private static void item(final MsgPackWriter w, final int value, final boolean withUnknown) {
    w.writeMapHeader(withUnknown ? 2 : 1);
    w.writeString(utf8("value"));
    w.writeInteger(value);
    if (withUnknown) {
      w.writeString(utf8("itemFuture"));
      w.writeInteger(1);
    }
  }

  private static void entry(final MsgPackWriter w, final String key, final Runnable value) {
    w.writeString(utf8(key));
    value.run();
  }

  private static Object instance() throws ReflectiveOperationException {
    return compiled.load("sample.Sample").getConstructor().newInstance();
  }

  private static Object read(final byte[] message) throws ReflectiveOperationException {
    final var sample = instance();
    wrap(sample, new UnsafeBuffer(message));
    return sample;
  }

  private static void wrap(final Object sample, final DirectBuffer buffer) {
    ((MsgpackBufferValue<?>) sample).wrap(buffer, 0, buffer.capacity());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void copy(final Object source, final Object target) {
    ((MsgpackBufferValue) target).copyFrom((MsgpackBufferValue) source);
  }

  private static int length(final Object sample) {
    return ((MsgpackBufferValue<?>) sample).getLength();
  }

  private static byte[] bytes(final Object sample) {
    final var buffer = new ExpandableArrayBuffer();
    final int written = ((MsgpackBufferValue<?>) sample).write(buffer, 0);
    return Arrays.copyOf(buffer.byteArray(), written);
  }

  private static byte[] encoded(final Consumer<MsgPackWriter> body) {
    final var buffer = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(buffer, 0);
    body.accept(writer);
    return Arrays.copyOf(buffer.byteArray(), writer.getOffset());
  }

  private static DirectBuffer utf8(final String s) {
    return new UnsafeBuffer(s.getBytes(StandardCharsets.UTF_8));
  }
}
