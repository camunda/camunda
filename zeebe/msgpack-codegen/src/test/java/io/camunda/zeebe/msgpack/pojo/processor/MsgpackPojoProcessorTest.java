/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.compile;
import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.encoded;
import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.get;
import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.pojo;
import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.pojoClass;
import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.set;
import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.source;
import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.utf8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.msgpack.Recyclable;
import io.camunda.zeebe.msgpack.pojo.Codecs;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.Result;
import io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.Source;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.util.buffer.BufferReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.collections.IntArrayList;
import org.agrona.collections.LongArrayList;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Compiles small classes in memory with the processor and checks what it accepts and rejects. */
final class MsgpackPojoProcessorTest {

  private static final DirectBuffer EMPTY_DOCUMENT = new UnsafeBuffer(new byte[] {(byte) 0x80});

  @Test
  void shouldStoreAKeyThatNeedsEscapingInJava() throws Exception {
    // given a key with a quote, a backslash, a newline and a non-ASCII character
    final var key = "a\"b\\c\ndé";
    final var codec =
        codecOf(
            """
            @Key("a\\"b\\\\c\\ndé") long key = 1L;
            @Key("r\\"aw") @Raw DirectBuffer raw = Decoding.EMPTY;
            """);
    final var message =
        encoded(
            w -> {
              w.writeMapHeader(1);
              w.writeString(utf8(key));
              w.writeInteger(42);
            });
    final var target = codec.newInstance();

    // when
    codec.read(target, message);
    final ThrowingCallable writeEmptyRaw = () -> codec.write(target);

    // then the key is read and the raw key is named as it is stored
    assertThat(get(target, "key")).isEqualTo(42L);
    assertThatThrownBy(writeEmptyRaw).hasMessageContaining("'r\"aw'");
  }

  @Test
  void shouldKeepTheDefaultOfAMissingEnum() throws Exception {
    // given
    final var codec = codecOf("Kind kind = Kind.TWO; int count = 1;", enumKind());
    final var read = codec.newInstance();
    read.reset();

    // when
    codec.read(read, mapWithOneEntry("count", 2));

    // then
    assertThat(get(read, "kind")).hasToString("TWO");
  }

  @Test
  void shouldNameTheObjectAndTheNestedPropertyThatCouldNotBeRead() throws Exception {
    // given an inner value that is not an integer
    final var codec = codecOf("final Inner inner = new Inner();", inner());
    final var message =
        encoded(
            w -> {
              w.writeMapHeader(1);
              w.writeString(utf8("inner"));
              w.writeMapHeader(1);
              w.writeString(utf8("value"));
              w.writeString(utf8("not a number"));
            });
    final var sample = (BufferReader) codec.newInstance();

    // when
    final ThrowingCallable wrap = () -> sample.wrap(new UnsafeBuffer(message), 0, message.length);

    // then
    assertThatThrownBy(wrap)
        .hasMessageStartingWith("Could not deserialize object [Sample]")
        .hasMessageContaining("of length " + message.length)
        .cause()
        .hasMessage("Could not read property 'inner'")
        .cause()
        .hasMessage("Could not read property 'value'");
  }

  @Test
  void shouldIgnoreFieldsMarkedAsIgnored() {
    // given a field of a type that would be rejected
    final var source = pojo("Sample", "long key; @Ignore Thread ignored;");

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors()).isEmpty();
  }

  @Test
  void shouldGenerateACodecThatRoundTripsAllSupportedTypes() throws Exception {
    // given
    final var codec =
        codecOf(
            """
            long key = -1L;
            int count = 7;
            boolean flag;
            DirectBuffer name = Decoding.EMPTY;
            @Binary DirectBuffer payload = Decoding.EMPTY;
            @Key("theKind") Kind kind;
            final List<DirectBuffer> ids = new ArrayList<>();
            final List<Long> versions = new ArrayList<>();
            final Inner inner = new Inner();
            """,
            enumKind(),
            inner());
    final var source = codec.newInstance();
    set(source, "key", 42L);
    set(source, "name", utf8("zeebe"));
    set(source, "flag", true);
    set(source, "payload", utf8("bin"));
    set(source, "kind", codec.result().load("sample.Kind").getEnumConstants()[1]);
    ((List<DirectBuffer>) get(source, "ids")).add(utf8("a"));
    ((List<DirectBuffer>) get(source, "ids")).add(utf8("b"));
    ((List<Long>) get(source, "versions")).addAll(List.of(5L, 300L, 70_000L, 5_000_000_000L, -1L));
    set(get(source, "inner"), "value", 9);

    // when
    final var bytes = codec.write(source);
    final var read = codec.newInstance();
    read.reset();
    codec.read(read, bytes);

    // then
    assertThat(bytes.length).isEqualTo(codec.codec().getLength(source));
    assertThat(get(read, "key")).isEqualTo(42L);
    assertThat(get(read, "count")).isEqualTo(7);
    assertThat(get(read, "flag")).isEqualTo(true);
    assertThat(get(read, "name")).isEqualTo(utf8("zeebe"));
    assertThat(get(read, "payload")).isEqualTo(utf8("bin"));
    assertThat(bytes)
        .as("the payload is stored as msgpack bin8, not as a string")
        .containsSequence((byte) 0xc4, (byte) 3, (byte) 'b', (byte) 'i', (byte) 'n');
    assertThat(get(read, "kind")).isEqualTo(get(source, "kind"));
    assertThat(get(read, "ids")).isEqualTo(List.of(utf8("a"), utf8("b")));
    assertThat(get(read, "versions")).isEqualTo(List.of(5L, 300L, 70_000L, 5_000_000_000L, -1L));
    assertThat(get(get(read, "inner"), "value")).isEqualTo(9);
  }

  @Test
  void shouldGenerateOpsThatRestoreTheFieldInitializers() throws Exception {
    // given
    final var codec =
        codecOf(
            """
            long key = -1L;
            int count = 7;
            DirectBuffer name = Decoding.EMPTY;
            Kind kind;
            final List<DirectBuffer> ids = new ArrayList<>();
            final Inner inner = new Inner();
            """,
            enumKind(),
            inner());
    final var sample = codec.newInstance();
    set(sample, "key", 5L);
    set(sample, "count", 1);
    set(sample, "name", utf8("dirty"));
    set(sample, "kind", codec.result().load("sample.Kind").getEnumConstants()[0]);
    ((List<DirectBuffer>) get(sample, "ids")).add(utf8("a"));
    set(get(sample, "inner"), "value", 9);

    // when
    sample.reset();

    // then
    assertThat(get(sample, "key")).isEqualTo(-1L);
    assertThat(get(sample, "count")).isEqualTo(7);
    assertThat(get(sample, "name")).isEqualTo(utf8(""));
    assertThat(get(sample, "kind")).isNull();
    assertThat((List<?>) get(sample, "ids")).isEmpty();
    assertThat(get(get(sample, "inner"), "value")).isEqualTo(3);
  }

  @ParameterizedTest
  @ValueSource(longs = {(1L << 32) + 5, Integer.MIN_VALUE - 1L})
  void shouldRejectAnIntegerOutOfTheIntRange(final long value) throws Exception {
    // given
    final var codec = codecOf("int count;");
    final var message = mapWithOneEntry("count", value);

    // when
    final ThrowingCallable read = () -> codec.read(codec.newInstance(), message);

    // then
    assertThatThrownBy(read)
        .hasMessage("Could not read property 'count'")
        .cause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("int");
  }

  @Test
  void shouldRejectAnUnknownEnumName() throws Exception {
    // given a name with the length of a constant
    final var codec = codecOf("Kind kind = Kind.ONE;", enumKind());
    final var message =
        encoded(
            w -> {
              w.writeMapHeader(1);
              w.writeString(utf8("kind"));
              w.writeString(utf8("SIX"));
            });

    // when
    final ThrowingCallable read = () -> codec.read(codec.newInstance(), message);

    // then
    assertThatThrownBy(read)
        .hasMessage("Could not read property 'kind'")
        .cause()
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'SIX'");
  }

  @Test
  void shouldReadKeysInAnyOrder() throws Exception {
    // given a message with the keys in an order that is not the declaration order
    final var codec = codecOf("long key; int count = 7; DirectBuffer name = Decoding.EMPTY;");
    final var message =
        encoded(
            w -> {
              w.writeMapHeader(3);
              w.writeString(utf8("name"));
              w.writeString(utf8("zeebe"));
              w.writeString(utf8("key"));
              w.writeInteger(42);
              w.writeString(utf8("count"));
              w.writeInteger(3);
            });
    final var read = codec.newInstance();
    read.reset();

    // when
    codec.read(read, message);

    // then
    assertThat(get(read, "key")).isEqualTo(42L);
    assertThat(get(read, "count")).isEqualTo(3);
    assertThat(get(read, "name")).isEqualTo(utf8("zeebe"));
  }

  @Test
  void shouldSkipUnknownPropertiesIfThePojoDoesNotKeepThem() throws Exception {
    // given a class that does not keep unknown properties, and a message with an unknown key
    final var codec =
        codecOf(
            source(
                "Sample",
                pojoClass("Sample", "long key; int count = 7;")
                    .replace("@MsgpackPojo", "@MsgpackPojo(unknownProperties = false)")));
    final var message =
        encoded(
            w -> {
              w.writeMapHeader(3);
              w.writeString(utf8("key"));
              w.writeInteger(1);
              w.writeString(utf8("unknown"));
              w.writeString(utf8("value"));
              w.writeString(utf8("count"));
              w.writeInteger(2);
            });
    final var read = codec.newInstance();
    read.reset();

    // when
    codec.read(read, message);
    final var written = codec.write(read);

    // then the unknown key is skipped, and it is not written back
    assertThat(get(read, "key")).isEqualTo(1L);
    assertThat(get(read, "count")).isEqualTo(2);
    assertThat(written)
        .isEqualTo(
            encoded(
                w -> {
                  w.writeMapHeader(2);
                  w.writeString(utf8("key"));
                  w.writeInteger(1);
                  w.writeString(utf8("count"));
                  w.writeInteger(2);
                }));
    assertThat(codec.codec().getLength(read)).isEqualTo(written.length);
  }

  @Test
  void shouldWrapAFinalUnsafeBufferInPlace() throws Exception {
    // given
    final var codec = codecOf("final UnsafeBuffer name = new UnsafeBuffer(0, 0);");
    final var source = codec.newInstance();
    ((UnsafeBuffer) get(source, "name")).wrap(utf8("zeebe"));
    final var bytes = codec.write(source);
    final var read = codec.newInstance();
    final var field = get(read, "name");

    // when
    codec.read(read, bytes);

    // then
    assertThat(get(read, "name")).isSameAs(field).isEqualTo(utf8("zeebe"));

    // when
    read.reset();

    // then
    assertThat(get(read, "name")).isSameAs(field);
    assertThat(((DirectBuffer) get(read, "name")).capacity()).isZero();
  }

  @Test
  void shouldAcceptAListOfTypeAnnotatedBuffers() throws Exception {
    // given
    final var codec = codecOf("final List<@Nullable DirectBuffer> ids = new ArrayList<>();");
    final var source = codec.newInstance();
    ((List<DirectBuffer>) get(source, "ids")).add(utf8("a"));
    ((List<DirectBuffer>) get(source, "ids")).add(utf8("b"));
    final var read = codec.newInstance();

    // when
    codec.read(read, codec.write(source));

    // then
    assertThat(get(read, "ids")).isEqualTo(List.of(utf8("a"), utf8("b")));
  }

  @Test
  void shouldFailToResetABufferFieldWithoutDefault() throws Exception {
    // given
    final var sample = codecOf("DirectBuffer name;").newInstance();

    // when
    final ThrowingCallable reset = sample::reset;

    // then
    assertThatThrownBy(reset)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("name")
        .hasMessageContaining("has no default");
  }

  @Test
  void shouldAcceptANestedPojoCompiledSeparately(@TempDir final Path classes) throws Exception {
    // given the nested pojo is only available as a class file, like one from another module
    final var previous = compile(inner());
    assertThat(previous.errors()).isEmpty();
    previous.writeTo(classes);
    final var source = pojo("Sample", "final Inner inner = new Inner();");

    // when
    final var result = compile(classes, source);

    // then
    assertThat(result.errors()).isEmpty();
  }

  @Test
  void shouldRoundTripAgronaPrimitiveLists() throws Exception {
    // given
    final var codec =
        codecOf(
            """
            final IntArrayList counts = new IntArrayList();
            final LongArrayList keys = new LongArrayList();
            """);
    final var source = codec.newInstance();
    ((IntArrayList) get(source, "counts")).addInt(Integer.MIN_VALUE);
    ((IntArrayList) get(source, "counts")).addInt(7);
    ((LongArrayList) get(source, "keys")).addLong(Long.MAX_VALUE);
    final var bytes = codec.write(source);
    final var read = codec.newInstance();
    ((LongArrayList) get(read, "keys")).addLong(-1L);

    // when
    codec.read(read, bytes);

    // then
    assertThat(bytes.length).isEqualTo(codec.codec().getLength(source));
    assertThat(((IntArrayList) get(read, "counts")).toIntArray())
        .containsExactly(Integer.MIN_VALUE, 7);
    assertThat(((LongArrayList) get(read, "keys")).toLongArray()).containsExactly(Long.MAX_VALUE);
  }

  @Test
  void shouldWarnToUseAnAgronaListForBoxedIntegers() {
    // given
    final var source =
        pojo(
            "Sample",
            """
            final List<Integer> counts = new ArrayList<>();
            final List<Long> keys = new ArrayList<>();
            final IntArrayList unboxedCounts = new IntArrayList();
            final LongArrayList unboxedKeys = new LongArrayList();
            final List<List<Long>> nested = new ArrayList<>();
            """);

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors()).isEmpty();
    assertThat(result.warnings())
        .containsExactlyInAnyOrder(
            "Field counts boxes every element, so reading it allocates: use IntArrayList instead",
            "Field keys boxes every element, so reading it allocates: use LongArrayList instead");
  }

  @Test
  void shouldStoreAnIntArrayListLikeAnArrayPropertyOfIntegers() throws Exception {
    // given the generated class and the ArrayProperty it replaces
    final var codec = codecOf("final IntArrayList ids = new IntArrayList();");
    final var generated = codec.newInstance();
    final var ids = (IntArrayList) get(generated, "ids");
    final int[] values = {Integer.MIN_VALUE, -1, 0, 127, 128, 70_000, Integer.MAX_VALUE};
    final var twin = new IdsTwin();
    for (final int value : values) {
      ids.addInt(value);
      twin.ids.add().setValue(value);
    }

    // when
    final var bytes = codec.write(generated);
    final var twinBytes = new byte[twin.getLength()];
    twin.write(new UnsafeBuffer(twinBytes), 0);

    // then both write the same bytes and each reads the other's
    assertThat(bytes).isEqualTo(twinBytes);
    final var read = codec.newInstance();
    codec.read(read, twinBytes);
    assertThat(((IntArrayList) get(read, "ids")).toIntArray()).containsExactly(values);
    final var twinRead = new IdsTwin();
    twinRead.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
    final var readValues = new ArrayList<Integer>();
    twinRead.ids.forEach(v -> readValues.add(v.getValue()));
    assertThat(readValues).containsExactly(Arrays.stream(values).boxed().toArray(Integer[]::new));
  }

  @Test
  void shouldStoreADocumentAsABinaryAndReadItBack() throws Exception {
    // given
    final var codec = documentCodec();
    final var source = codec.newInstance();
    final var document = mapWithOneEntry("a", 1);
    set(source, "doc", new UnsafeBuffer(document));

    // when
    final var bytes = codec.write(source);
    final var read = codec.newInstance();
    codec.read(read, bytes);

    // then the document is a binary holding the map
    assertThat(bytes).isEqualTo(documentMessage(document));
    assertThat(get(read, "doc")).isEqualTo(new UnsafeBuffer(document));
  }

  @Test
  void shouldReadAnEmptyOrNilDocumentAsTheEmptyMap() throws Exception {
    // given
    final var codec = documentCodec();
    final var empty = documentMessage(new byte[0]);
    final var nil = documentMessage(encoded(MsgPackWriter::writeNil));

    // when - then
    for (final var message : List.of(empty, nil)) {
      final var read = codec.newInstance();
      set(read, "doc", utf8("x"));
      codec.read(read, message);
      assertThat(get(read, "doc")).isEqualTo(EMPTY_DOCUMENT);
    }
  }

  @Test
  void shouldRejectADocumentThatIsNotAMap() throws Exception {
    // given a document holding the integer 1
    final var codec = documentCodec();
    final var message = documentMessage(encoded(w -> w.writeInteger(1)));

    // when
    final ThrowingCallable read = () -> codec.read(codec.newInstance(), message);

    // then
    assertThatThrownBy(read)
        .hasMessageContaining("Could not read property 'doc'")
        .cause()
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("root level object");
  }

  @Test
  void shouldRestoreTheDefaultDocumentOnReset() throws Exception {
    // given
    final var instance = documentCodec().newInstance();
    set(instance, "doc", new UnsafeBuffer(mapWithOneEntry("a", 1)));

    // when
    instance.reset();

    // then
    assertThat(get(instance, "doc")).isEqualTo(EMPTY_DOCUMENT);
  }

  @Test
  void shouldWrapAFinalUnsafeDocumentInPlace() throws Exception {
    // given
    final var codec =
        codecOf("@Document final UnsafeBuffer doc = new UnsafeBuffer(new byte[] {(byte) 0x80});");
    final var read = codec.newInstance();
    final var field = get(read, "doc");
    final var document = mapWithOneEntry("a", 1);

    // when
    codec.read(read, documentMessage(document));

    // then
    assertThat(get(read, "doc")).isSameAs(field).isEqualTo(new UnsafeBuffer(document));
  }

  @Test
  void shouldKeepUnknownPropertiesNextToADocument() throws Exception {
    // given a message with a key of a newer version before the document
    final var codec = documentCodec();
    final var message =
        encoded(
            w -> {
              w.writeMapHeader(2);
              w.writeString(utf8("future"));
              w.writeInteger(5);
              w.writeString(utf8("doc"));
              w.writeBinary(new UnsafeBuffer(mapWithOneEntry("a", 1)));
            });
    final var read = codec.newInstance();

    // when
    codec.read(read, message);

    // then the document is read and the unknown entry is written back after it
    assertThat(get(read, "doc")).isEqualTo(new UnsafeBuffer(mapWithOneEntry("a", 1)));
    final var written = codec.write(read);
    assertThat(written).hasSize(message.length);
    assertThat(written)
        .containsSequence(
            encoded(
                w -> {
                  w.writeString(utf8("future"));
                  w.writeInteger(5);
                }));
  }

  // ---------------------------------------------------------------- helpers

  private static SampleCodec codecOf(final String fields, final Source... others) {
    final var sources = new ArrayList<>(List.of(others));
    sources.add(pojo("Sample", fields));
    return codecOf(sources.toArray(Source[]::new));
  }

  /** The codec of the class {@code sample.Sample}, which is one of the sources. */
  private static SampleCodec codecOf(final Source... sources) {
    final var result = compile(sources);
    assertThat(result.errors()).isEmpty();
    try {
      final var type = result.load("sample.Sample");
      @SuppressWarnings("unchecked")
      final var codec = (MsgpackCodec<Recyclable>) Codecs.of(type.asSubclass(Recyclable.class));
      return new SampleCodec(result, type, codec);
    } catch (final ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

  private static SampleCodec documentCodec() {
    return codecOf("@Document DirectBuffer doc = new UnsafeBuffer(new byte[] {(byte) 0x80});");
  }

  /** The message {@code {"doc": <binary>}} with the given document bytes. */
  private static byte[] documentMessage(final byte[] document) {
    return encoded(
        w -> {
          w.writeMapHeader(1);
          w.writeString(utf8("doc"));
          w.writeBinary(new UnsafeBuffer(document));
        });
  }

  private static byte[] mapWithOneEntry(final String key, final long value) {
    return encoded(
        w -> {
          w.writeMapHeader(1);
          w.writeString(utf8(key));
          w.writeInteger(value);
        });
  }

  private static Source inner() {
    return pojo("Inner", "int value = 3;");
  }

  private static Source enumKind() {
    return new Source("sample.Kind", "package sample; public enum Kind { ONE, TWO }");
  }

  /** The implementation a field of integers had before: an ArrayProperty of IntegerValue. */
  private static final class IdsTwin extends io.camunda.zeebe.msgpack.UnpackedObject {
    private final io.camunda.zeebe.msgpack.property.ArrayProperty<
            io.camunda.zeebe.msgpack.value.IntegerValue>
        ids =
            new io.camunda.zeebe.msgpack.property.ArrayProperty<>(
                "ids", io.camunda.zeebe.msgpack.value.IntegerValue::new);

    IdsTwin() {
      super(1);
      declareProperty(ids);
    }
  }

  /** A compiled sample class with its codec, for tests that read and write bytes. */
  private record SampleCodec(Result result, Class<?> type, MsgpackCodec<Recyclable> codec) {

    Recyclable newInstance(final String className) throws ReflectiveOperationException {
      return (Recyclable) result.load(className).getConstructor().newInstance();
    }

    Recyclable newInstance() throws ReflectiveOperationException {
      return (Recyclable) type.getConstructor().newInstance();
    }

    byte[] write(final Object value) {
      final var bytes = new ExpandableArrayBuffer();
      final int length = codec.write(new MsgPackWriter().wrap(bytes, 0), (Recyclable) value);
      return Arrays.copyOf(bytes.byteArray(), length);
    }

    void read(final Object target, final byte[] message) {
      codec.read(
          new MsgPackReader().wrap(new UnsafeBuffer(message), 0, message.length),
          (Recyclable) target);
    }
  }
}
