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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.msgpack.Recyclable;
import io.camunda.zeebe.msgpack.pojo.Codecs;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.Result;
import io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.Source;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.util.buffer.BufferReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.collections.IntArrayList;
import org.agrona.collections.LongArrayList;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Compiles small classes in memory with the processor and checks what it accepts and rejects. */
final class MsgpackPojoProcessorTest {

  private static final String UNKNOWN =
      "\n@MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();";

  private static final String IMPORTS =
      """
      package sample;
      import io.camunda.zeebe.msgpack.pojo.*;
      import java.util.*;
      import org.agrona.DirectBuffer;
      import org.agrona.concurrent.UnsafeBuffer;
      """;

  private static final DirectBuffer EMPTY_DOCUMENT = new UnsafeBuffer(new byte[] {(byte) 0x80});

  @Test
  void shouldRejectAPojoThatIsNotRecyclable() {
    // given
    final var source = pojo("@MsgpackPojo public final class Bad { long key; }");

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors())
        .anyMatch(e -> e.contains("must implement") && e.contains("Recyclable"));
  }

  @Test
  void shouldRejectAPrivateField() {
    // given
    final var source = pojo(valid("private long key;"));

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors())
        .anyMatch(e -> e.contains("key") && e.contains("must not be private"));
  }

  @Test
  void shouldRejectAFinalScalarField() {
    // given
    final var source = pojo(valid("final long key = 1L;"));

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors()).anyMatch(e -> e.contains("key") && e.contains("must not be final"));
  }

  @Test
  void shouldRejectNonFinalContainerFields() {
    // given
    final var fields =
        List.of(
            "List<DirectBuffer> key = new ArrayList<>();",
            "List<Long> key = new ArrayList<>();",
            "org.agrona.collections.IntArrayList key = new org.agrona.collections.IntArrayList();",
            "org.agrona.collections.LongArrayList key = new org.agrona.collections.LongArrayList();",
            "Inner key = new Inner();",
            "List<Inner> key = new ArrayList<>();");

    for (final var field : fields) {
      // when
      final var result = compile(inner(), pojo(valid(field)));

      // then
      assertThat(result.errors())
          .as(field)
          .anyMatch(e -> e.contains("key") && e.contains("must be final"));
    }
  }

  @Test
  void shouldRejectANonFinalUnsafeBuffer() {
    // given
    final var source =
        pojo(
            valid(
                "org.agrona.concurrent.UnsafeBuffer key = new org.agrona.concurrent.UnsafeBuffer(0, 0);"));

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors())
        .anyMatch(
            e -> e.contains("key") && e.contains("must be final") && e.contains("DirectBuffer"));
  }

  @Test
  void shouldRejectAnUnsupportedFieldType() {
    // given
    final var source = pojo(valid("java.util.Map<String, String> key;"));

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors()).anyMatch(e -> e.contains("Unsupported type") && e.contains("key"));
  }

  @Test
  void shouldRejectANestedTypeWithoutCodec() {
    // given
    final var source = pojo(valid("final StringBuilder key = new StringBuilder();"));

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors()).anyMatch(e -> e.contains("Unsupported type") && e.contains("key"));
  }

  @Test
  void shouldRejectAPojoWithoutUnknownPropertiesField() {
    // given
    final var source = pojo(withoutUnknown("long key;"));

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors())
        .anyMatch(e -> e.contains("needs a final @MsgpackUnknownProperties"));
  }

  @Test
  void shouldRejectTwoUnknownPropertiesFields() {
    // given
    final var source =
        pojo(
            valid(
                """
                long key;
                @MsgpackUnknownProperties final UnknownProperties first = new UnknownProperties();
                @MsgpackUnknownProperties final UnknownProperties second = new UnknownProperties();
                """));

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors())
        .anyMatch(e -> e.contains("first") && e.contains("second") && e.contains("both"));
  }

  @Test
  void shouldRejectAnUnknownPropertiesFieldOfAnotherType() {
    // given
    final var source =
        pojo(
            valid(
                "long key; @MsgpackUnknownProperties final List<Long> extra = new ArrayList<>();"));

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors())
        .anyMatch(e -> e.contains("extra") && e.contains("must be a final UnknownProperties"));
  }

  @Test
  void shouldRejectANonFinalUnknownPropertiesField() {
    // given
    final var source =
        pojo(
            valid(
                "long key; @MsgpackUnknownProperties UnknownProperties extra = new UnknownProperties();"));

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors())
        .anyMatch(e -> e.contains("extra") && e.contains("must be a final UnknownProperties"));
  }

  @Test
  void shouldStoreAKeyThatNeedsEscapingInJava() throws Exception {
    // given a key with a quote, a backslash, a newline and a non-ASCII character
    final var key = "a\"b\\c\nd\u00e9";
    final var result =
        compile(
            pojo(
                valid(
                    """
                    @MsgpackProperty("a\\"b\\\\c\\nd\u00e9") long key = 1L;
                    @MsgpackProperty(value = "r\\"aw", raw = true)
                    DirectBuffer raw = io.camunda.zeebe.msgpack.pojo.Decoding.EMPTY;
                    """)));
    assertThat(result.errors()).isEmpty();
    final var sampleClass = result.load("sample.Sample");
    @SuppressWarnings("unchecked")
    final var codec =
        (MsgpackCodec<Recyclable>) Codecs.of(sampleClass.asSubclass(Recyclable.class));
    final var bytes = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(bytes, 0);
    writer.writeMapHeader(1);
    writer.writeString(new UnsafeBuffer(key.getBytes(StandardCharsets.UTF_8)));
    writer.writeInteger(42);
    final int length = writer.getOffset();
    final var target = (Recyclable) sampleClass.getConstructor().newInstance();
    target.reset();

    // when
    codec.read(new MsgPackReader().wrap(bytes, 0, length), target);
    final ThrowingCallable writeEmptyRaw =
        () -> codec.write(new MsgPackWriter().wrap(new ExpandableArrayBuffer(), 0), target);

    // then the key is read and the raw key is named as it is stored
    assertThat(get(target, "key")).isEqualTo(42L);
    assertThatThrownBy(writeEmptyRaw).hasMessageContaining("'r\"aw'");
  }

  @Test
  void shouldRejectAMessageWithoutARequiredProperty() throws Exception {
    // given
    final var result =
        compile(pojo(valid("@MsgpackProperty(required = true) long key; int count = 1;")));
    assertThat(result.errors()).isEmpty();
    final var message = message("count", 2);

    // when
    final ThrowingCallable read = () -> read(result, message);

    // then
    assertThatThrownBy(read).hasMessageContaining("Property 'key' has no valid value");
  }

  @Test
  void shouldReadAMessageWithARequiredProperty() throws Exception {
    // given
    final var result =
        compile(pojo(valid("@MsgpackProperty(required = true) long key; int count = 1;")));
    assertThat(result.errors()).isEmpty();

    // when
    final var read = read(result, message("key", 7));

    // then
    assertThat(get(read, "key")).isEqualTo(7L);
    assertThat(get(read, "count")).isEqualTo(1);
  }

  @Test
  void shouldRejectMoreRequiredPropertiesThanItCanTrack() {
    // given
    final var fields = new StringBuilder();
    for (int i = 0; i <= Long.SIZE; i++) {
      fields.append("@MsgpackProperty(required = true) long f%d;%n".formatted(i));
    }

    // when
    final var result = compile(pojo(valid(fields.toString())));

    // then
    assertThat(result.errors()).anyMatch(e -> e.contains("more than 64 required properties"));
  }

  @Test
  void shouldRejectAMessageWithoutAnEnumThatHasNoDefault() throws Exception {
    // given
    final var result = compile(enumKind(), pojo(valid("Kind kind; int count = 1;")));
    assertThat(result.errors()).isEmpty();
    final var message = message("count", 2);

    // when
    final ThrowingCallable read = () -> read(result, message);

    // then
    assertThatThrownBy(read).hasMessageContaining("Property 'kind' has no valid value");
  }

  @Test
  void shouldKeepTheDefaultOfAMissingEnum() throws Exception {
    // given
    final var result = compile(enumKind(), pojo(valid("Kind kind = Kind.TWO; int count = 1;")));
    assertThat(result.errors()).isEmpty();

    // when
    final var read = read(result, message("count", 2));

    // then
    assertThat(get(read, "kind")).hasToString("TWO");
  }

  @Test
  void shouldNameTheObjectAndTheNestedPropertyThatCouldNotBeRead() throws Exception {
    // given an inner value that is not an integer
    final var result = compile(inner(), pojo(valid("final Inner inner = new Inner();")));
    assertThat(result.errors()).isEmpty();
    final var bytes = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(bytes, 0);
    writer.writeMapHeader(1);
    writer.writeString(buffer("inner"));
    writer.writeMapHeader(1);
    writer.writeString(buffer("value"));
    writer.writeString(buffer("not a number"));
    final int length = writer.getOffset();
    final var sample = (BufferReader) result.load("sample.Sample").getConstructor().newInstance();

    // when
    final ThrowingCallable wrap = () -> sample.wrap(bytes, 0, length);

    // then
    assertThatThrownBy(wrap)
        .hasMessageStartingWith("Could not deserialize object [Sample]")
        .hasMessageContaining("of length " + length)
        .cause()
        .hasMessage("Could not read property 'inner'")
        .cause()
        .hasMessage("Could not read property 'value'");
  }

  @Test
  void shouldIgnoreFieldsMarkedAsIgnored() {
    // given a field of a type that would be rejected
    final var source = pojo(valid("long key; @MsgpackIgnore Thread ignored;"));

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors()).isEmpty();
  }

  @Test
  void shouldGenerateACodecThatRoundTripsAllSupportedTypes() throws Exception {
    // given
    final var result =
        compile(
            enumKind(),
            inner(),
            pojo(
                valid(
                    """
                    long key = -1L;
                    int count = 7;
                    boolean flag;
                    DirectBuffer name = io.camunda.zeebe.msgpack.pojo.Decoding.EMPTY;
                    @MsgpackProperty(binary = true) DirectBuffer payload = io.camunda.zeebe.msgpack.pojo.Decoding.EMPTY;
                    @MsgpackProperty("theKind") Kind kind;
                    final List<DirectBuffer> ids = new ArrayList<>();
                    final List<Long> versions = new ArrayList<>();
                    final Inner inner = new Inner();
                    @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();

                    """)));
    assertThat(result.errors()).isEmpty();
    final var sampleClass = result.load("sample.Sample");
    final var source = (Recyclable) sampleClass.getConstructor().newInstance();
    set(source, "key", 42L);
    set(source, "name", buffer("zeebe"));
    set(source, "flag", true);
    set(source, "payload", buffer("bin"));
    set(source, "kind", result.load("sample.Kind").getEnumConstants()[1]);
    ((List<DirectBuffer>) get(source, "ids")).add(buffer("a"));
    ((List<DirectBuffer>) get(source, "ids")).add(buffer("b"));
    ((List<Long>) get(source, "versions")).addAll(List.of(5L, 300L, 70_000L, 5_000_000_000L, -1L));
    set(get(source, "inner"), "value", 9);

    // when
    @SuppressWarnings("unchecked")
    final MsgpackCodec<Recyclable> codec =
        (MsgpackCodec<Recyclable>) Codecs.of(sampleClass.asSubclass(Recyclable.class));
    final var bytes = new ExpandableArrayBuffer();
    final int length = codec.write(new MsgPackWriter().wrap(bytes, 0), source);
    final var read = (Recyclable) sampleClass.getConstructor().newInstance();
    read.reset();
    codec.read(new MsgPackReader().wrap(bytes, 0, length), read);

    // then
    assertThat(length).isEqualTo(codec.getLength(source));
    assertThat(get(read, "key")).isEqualTo(42L);
    assertThat(get(read, "count")).isEqualTo(7);
    assertThat(get(read, "flag")).isEqualTo(true);
    assertThat(get(read, "name")).isEqualTo(buffer("zeebe"));
    assertThat(get(read, "payload")).isEqualTo(buffer("bin"));
    assertThat(bytes.byteArray())
        .as("the payload is stored as msgpack bin8, not as a string")
        .containsSequence((byte) 0xc4, (byte) 3, (byte) 'b', (byte) 'i', (byte) 'n');
    assertThat(get(read, "kind")).isEqualTo(get(source, "kind"));
    assertThat(get(read, "ids")).isEqualTo(List.of(buffer("a"), buffer("b")));
    assertThat(get(read, "versions")).isEqualTo(List.of(5L, 300L, 70_000L, 5_000_000_000L, -1L));
    assertThat(get(get(read, "inner"), "value")).isEqualTo(9);
  }

  @Test
  void shouldGenerateAResetterThatRestoresTheFieldInitializers() throws Exception {
    // given
    final var result =
        compile(
            enumKind(),
            inner(),
            pojo(
                valid(
                    """
                    long key = -1L;
                    int count = 7;
                    DirectBuffer name = io.camunda.zeebe.msgpack.pojo.Decoding.EMPTY;
                    Kind kind;
                    final List<DirectBuffer> ids = new ArrayList<>();
                    final Inner inner = new Inner();
                    @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();

                    """)));
    assertThat(result.errors()).isEmpty();
    final var sample = (Recyclable) result.load("sample.Sample").getConstructor().newInstance();
    set(sample, "key", 5L);
    set(sample, "count", 1);
    set(sample, "name", buffer("dirty"));
    set(sample, "kind", result.load("sample.Kind").getEnumConstants()[0]);
    ((List<DirectBuffer>) get(sample, "ids")).add(buffer("a"));
    set(get(sample, "inner"), "value", 9);

    // when
    sample.reset();

    // then
    assertThat(get(sample, "key")).isEqualTo(-1L);
    assertThat(get(sample, "count")).isEqualTo(7);
    assertThat(get(sample, "name")).isEqualTo(buffer(""));
    assertThat(get(sample, "kind")).isNull();
    assertThat((List<?>) get(sample, "ids")).isEmpty();
    assertThat(get(get(sample, "inner"), "value")).isEqualTo(3);
  }

  @Test
  void shouldRejectAPojoThatIsNotTopLevel() {
    // given
    final var source =
        new Source(
            "sample.Outer",
            IMPORTS
                + """
                public class Outer {
                  @MsgpackPojo
                  public static final class Sample extends MsgpackBufferValue<Sample> {
                    long key;
                  }
                }
                """);

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors()).anyMatch(e -> e.contains("top-level"));
  }

  @Test
  void shouldRejectTwoFieldsWithTheSameKey() {
    // given
    final var source = pojo(valid("long key; @MsgpackProperty(\"key\") long other;"));

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors()).anyMatch(e -> e.contains("other") && e.contains("'key'"));
  }

  @Test
  void shouldRejectAFieldOfASuperclassThatIsNotStored() {
    // given
    final var source = superclassField("long inherited;");

    // when
    final var result = compile(source.base(), source.pojo());

    // then
    assertThat(result.errors())
        .anyMatch(e -> e.contains("inherited") && e.contains("would not be stored"));
  }

  @Test
  void shouldAcceptAnIgnoredFieldOfASuperclass() {
    // given
    final var source = superclassField("@MsgpackIgnore long inherited;");

    // when
    final var result = compile(source.base(), source.pojo());

    // then
    assertThat(result.errors()).isEmpty();
  }

  @Test
  void shouldRejectAnIntegerOutOfTheIntRange() throws Exception {
    // given
    final var result = compile(pojo(valid("int count;")));
    assertThat(result.errors()).isEmpty();
    final var sampleClass = result.load("sample.Sample");
    @SuppressWarnings("unchecked")
    final var codec =
        (MsgpackCodec<Recyclable>) Codecs.of(sampleClass.asSubclass(Recyclable.class));
    final var bytes = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(bytes, 0);
    writer.writeMapHeader(1);
    writer.writeString(new UnsafeBuffer("count".getBytes(StandardCharsets.UTF_8)));
    writer.writeInteger((1L << 32) + 5);
    final int length = writer.getOffset();
    final var target = (Recyclable) sampleClass.getConstructor().newInstance();

    // when
    final ThrowingCallable read =
        () -> codec.read(new MsgPackReader().wrap(bytes, 0, length), target);

    // then
    assertThatThrownBy(read)
        .hasMessage("Could not read property 'count'")
        .cause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("int");
  }

  @Test
  void shouldWrapAFinalUnsafeBufferInPlace() throws Exception {
    // given
    final var result =
        compile(
            pojo(
                valid(
                    "final org.agrona.concurrent.UnsafeBuffer name = new org.agrona.concurrent.UnsafeBuffer(0, 0);")));
    assertThat(result.errors()).isEmpty();
    final var sampleClass = result.load("sample.Sample");
    @SuppressWarnings("unchecked")
    final var codec =
        (MsgpackCodec<Recyclable>) Codecs.of(sampleClass.asSubclass(Recyclable.class));
    final var source = (Recyclable) sampleClass.getConstructor().newInstance();
    ((UnsafeBuffer) get(source, "name")).wrap(buffer("zeebe"));
    final var bytes = new ExpandableArrayBuffer();
    final int length = codec.write(new MsgPackWriter().wrap(bytes, 0), source);
    final var read = (Recyclable) sampleClass.getConstructor().newInstance();
    final var field = get(read, "name");

    // when
    codec.read(new MsgPackReader().wrap(bytes, 0, length), read);

    // then
    assertThat(get(read, "name")).isSameAs(field).isEqualTo(buffer("zeebe"));

    // when
    read.reset();

    // then
    assertThat(get(read, "name")).isSameAs(field);
    assertThat(((DirectBuffer) get(read, "name")).capacity()).isZero();
  }

  @Test
  void shouldAcceptAListOfTypeAnnotatedBuffers() throws Exception {
    // given
    final var result =
        compile(
            pojo(
                valid(
                    "final List<@org.jspecify.annotations.Nullable DirectBuffer> ids = new ArrayList<>();")));
    assertThat(result.errors()).isEmpty();
    final var sampleClass = result.load("sample.Sample");
    @SuppressWarnings("unchecked")
    final var codec =
        (MsgpackCodec<Recyclable>) Codecs.of(sampleClass.asSubclass(Recyclable.class));
    final var source = (Recyclable) sampleClass.getConstructor().newInstance();
    ((List<DirectBuffer>) get(source, "ids")).add(buffer("a"));
    ((List<DirectBuffer>) get(source, "ids")).add(buffer("b"));
    final var bytes = new ExpandableArrayBuffer();
    final int length = codec.write(new MsgPackWriter().wrap(bytes, 0), source);
    final var read = (Recyclable) sampleClass.getConstructor().newInstance();

    // when
    codec.read(new MsgPackReader().wrap(bytes, 0, length), read);

    // then
    assertThat(get(read, "ids")).isEqualTo(List.of(buffer("a"), buffer("b")));
  }

  @Test
  void shouldRoundTripAgronaPrimitiveLists() throws Exception {
    // given
    final var result =
        compile(
            pojo(
                valid(
                    """
                    final org.agrona.collections.IntArrayList counts =
                        new org.agrona.collections.IntArrayList();
                    final org.agrona.collections.LongArrayList keys =
                        new org.agrona.collections.LongArrayList();
                    """)));
    assertThat(result.errors()).isEmpty();
    final var sampleClass = result.load("sample.Sample");
    @SuppressWarnings("unchecked")
    final var codec =
        (MsgpackCodec<Recyclable>) Codecs.of(sampleClass.asSubclass(Recyclable.class));
    final var source = (Recyclable) sampleClass.getConstructor().newInstance();
    ((IntArrayList) get(source, "counts")).addInt(Integer.MIN_VALUE);
    ((IntArrayList) get(source, "counts")).addInt(7);
    ((LongArrayList) get(source, "keys")).addLong(Long.MAX_VALUE);
    final var bytes = new ExpandableArrayBuffer();
    final int length = codec.write(new MsgPackWriter().wrap(bytes, 0), source);
    final var read = (Recyclable) sampleClass.getConstructor().newInstance();
    ((LongArrayList) get(read, "keys")).addLong(-1L);

    // when
    codec.read(new MsgPackReader().wrap(bytes, 0, length), read);

    // then
    assertThat(length).isEqualTo(codec.getLength(source));
    assertThat(((IntArrayList) get(read, "counts")).toIntArray())
        .containsExactly(Integer.MIN_VALUE, 7);
    assertThat(((LongArrayList) get(read, "keys")).toLongArray()).containsExactly(Long.MAX_VALUE);
  }

  @Test
  void shouldRejectADocumentThatIsNotABuffer() {
    // given
    final var source = pojo(valid("@MsgpackProperty(document = true) long doc;"));

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors())
        .anyMatch(e -> e.contains("doc") && e.contains("document requires a DirectBuffer"));
  }

  @Test
  void shouldRejectADocumentThatIsAlsoRawOrBinary() {
    // given
    final var raw =
        pojo(
            valid(
                "@MsgpackProperty(document = true, raw = true) DirectBuffer doc = Decoding.EMPTY;"));
    final var binary =
        pojo(
            valid(
                "@MsgpackProperty(document = true, binary = true) DirectBuffer doc = Decoding.EMPTY;"));

    // when - then
    for (final var source : List.of(raw, binary)) {
      assertThat(compile(source).errors())
          .anyMatch(e -> e.contains("doc") && e.contains("document cannot be combined"));
    }
  }

  @Test
  void shouldRejectAFinalDocumentField() {
    // given
    final var source =
        pojo(valid("@MsgpackProperty(document = true) final DirectBuffer doc = Decoding.EMPTY;"));

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors()).anyMatch(e -> e.contains("doc") && e.contains("must not be final"));
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
      set(read, "doc", buffer("x"));
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
    final var codec = documentCodec();
    final var instance = codec.newInstance();
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
        codecOf(
            valid(
                "@MsgpackProperty(document = true) final UnsafeBuffer doc = new UnsafeBuffer(new byte[] {(byte) 0x80});"));
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
    final var codec =
        codecOf(
            valid(
                """
                @MsgpackProperty(document = true) DirectBuffer doc = new UnsafeBuffer(new byte[] {(byte) 0x80});
                @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();
                """));
    final var message =
        encoded(
            w -> {
              w.writeMapHeader(2);
              w.writeString(buffer("future"));
              w.writeInteger(5);
              w.writeString(buffer("doc"));
              w.writeBinary(new UnsafeBuffer(mapWithOneEntry("a", 1)));
            });
    final var read = codec.newInstance();

    // when
    codec.read(read, message);

    // then the document is read and the unknown entry is written back after it
    assertThat(get(read, "doc")).isEqualTo(new UnsafeBuffer(mapWithOneEntry("a", 1)));
    final var written = codec.write(read);
    assertThat(written).hasSize(message.length);
    assertThat(new UnsafeBuffer(written).byteArray())
        .containsSequence(
            encoded(
                w -> {
                  w.writeString(buffer("future"));
                  w.writeInteger(5);
                }));
  }

  @Test
  void shouldRoundTripAListOfNestedPojos() throws Exception {
    // given
    final var codec = codecOf(valid("final List<Inner> items = new ArrayList<>();"), inner());
    final var source = codec.newInstance();
    final var items = (List<Object>) get(source, "items");
    for (final int value : new int[] {7, 9}) {
      final var item = codec.newInstance("sample.Inner");
      set(item, "value", value);
      items.add(item);
    }

    // when
    final var bytes = codec.write(source);
    final var read = codec.newInstance();
    final var stale = codec.newInstance("sample.Inner");
    ((List<Object>) get(read, "items")).add(stale);
    ((Recyclable) read).reset();
    codec.read(read, bytes);

    // then the items are a msgpack array of maps
    assertThat(bytes).isEqualTo(itemsMessage(7, 9));
    assertThat(codec.codec().getLength(source)).isEqualTo(bytes.length);
    final var readItems = (List<Object>) get(read, "items");
    assertThat(readItems).hasSize(2).doesNotContain(stale);
    assertThat(get(readItems.get(0), "value")).isEqualTo(7);
    assertThat(get(readItems.get(1), "value")).isEqualTo(9);
  }

  @Test
  void shouldRoundTripAListOfBaseValues() throws Exception {
    // given
    final var codec = codecOf(valid("final List<Legacy> items = new ArrayList<>();"), legacy());
    final var read = codec.newInstance();

    // when
    codec.read(read, itemsMessage(7, 9));

    // then
    final var items = (List<Object>) get(read, "items");
    assertThat(items).hasSize(2);
    assertThat(codec.write(read)).isEqualTo(itemsMessage(7, 9));
    assertThat(codec.codec().getLength(read)).isEqualTo(itemsMessage(7, 9).length);
  }

  @Test
  void shouldReadAnEmptyAndAMissingListOfNestedObjects() throws Exception {
    // given
    final var codec = codecOf(valid("final List<Inner> items = new ArrayList<>();"), inner());
    final var read = codec.newInstance();
    ((List<Object>) get(read, "items")).add(codec.newInstance("sample.Inner"));

    // when an empty array is read
    codec.read(read, itemsMessage());

    // then
    assertThat((List<?>) get(read, "items")).isEmpty();

    // when the list is missing and the instance is reset first, as wrap does
    ((List<Object>) get(read, "items")).add(codec.newInstance("sample.Inner"));
    read.reset();
    codec.read(read, encoded(w -> w.writeMapHeader(0)));

    // then
    assertThat((List<?>) get(read, "items")).isEmpty();
  }

  @Test
  void shouldKeepUnknownPropertiesOfItemsAndOfTheListOwner() throws Exception {
    // given an item and the owner with keys of a newer version
    final var codec =
        codecOf(
            valid(
                """
                final List<Inner> items = new ArrayList<>();
                @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();
                """),
            inner());
    final var message =
        encoded(
            w -> {
              w.writeMapHeader(2);
              w.writeString(buffer("items"));
              w.writeArrayHeader(1);
              w.writeMapHeader(2);
              w.writeString(buffer("value"));
              w.writeInteger(7);
              w.writeString(buffer("itemFuture"));
              w.writeInteger(1);
              w.writeString(buffer("future"));
              w.writeInteger(2);
            });
    final var read = codec.newInstance();

    // when
    codec.read(read, message);

    // then both are written back
    assertThat(codec.write(read)).isEqualTo(message);
  }

  @Test
  void shouldRejectAListItemWithoutAPublicConstructor() {
    // given
    final var hidden =
        new Source(
            "sample.Hidden",
            IMPORTS
                + """
                @MsgpackPojo
                public final class Hidden extends MsgpackBufferValue<Hidden> {
                  int value;
                  Hidden() {}
                }
                """);

    // when
    final var result =
        compile(hidden, pojo(valid("final List<Hidden> items = new ArrayList<>();")));

    // then
    assertThat(result.errors())
        .anyMatch(e -> e.contains("items") && e.contains("public constructor without parameters"));
  }

  @Test
  void shouldRejectAListOfAnUnsupportedItemType() {
    // given
    final var source = pojo(valid("final List<String> items = new ArrayList<>();"));

    // when
    final var result = compile(source);

    // then
    assertThat(result.errors())
        .anyMatch(e -> e.contains("Unsupported type") && e.contains("items"));
  }

  @Test
  void shouldFailToResetAListOfNestedObjectsThatDoesNotStartEmpty() throws Exception {
    // given
    final var result =
        compile(
            inner(),
            pojo(valid("final List<Inner> items = new ArrayList<>(List.of(new Inner()));")));
    assertThat(result.errors()).isEmpty();
    final var sample = (Recyclable) result.load("sample.Sample").getConstructor().newInstance();

    // when
    final ThrowingCallable reset = sample::reset;

    // then
    assertThatThrownBy(reset)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("items")
        .hasMessageContaining("must start empty");
  }

  @Test
  void shouldNotChangeTheStoredFormatOfSanitizedFields() throws Exception {
    // given the same fields with and without the mark
    final var fields =
        """
        %1$slong a = 3;
        %1$sDirectBuffer b = new UnsafeBuffer(new byte[] {1});
        %1$s@MsgpackProperty(document = true) DirectBuffer c = new UnsafeBuffer(new byte[] {(byte) 0x80});
        %1$sfinal List<DirectBuffer> d = new ArrayList<>();
        %1$sfinal List<Inner> e = new ArrayList<>();
        """;
    final var plain = codecOf(valid(fields.formatted("")), inner());
    final var sanitized = codecOf(valid(fields.formatted("@MsgpackSanitized ")), inner());
    final var source = plain.newInstance();
    final var marked = sanitized.newInstance();
    for (final var value : List.of(source, marked)) {
      set(value, "a", 42L);
      ((List<DirectBuffer>) get(value, "d")).add(buffer("x"));
    }

    // when
    final var bytes = plain.write(source);

    // then
    assertThat(sanitized.write(marked)).isEqualTo(bytes);
    final var read = sanitized.newInstance();
    sanitized.read(read, bytes);
    assertThat(get(read, "a")).isEqualTo(42L);
  }

  @Test
  void shouldFailToResetABufferFieldWithoutDefault() throws Exception {
    // given
    final var result = compile(pojo(valid("DirectBuffer name;")));
    assertThat(result.errors()).isEmpty();
    final var sample = (Recyclable) result.load("sample.Sample").getConstructor().newInstance();

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
    final var source = pojo(valid("final Inner inner = new Inner();"));

    // when
    final var result = compile(classes, source);

    // then
    assertThat(result.errors()).isEmpty();
  }

  // ---------------------------------------------------------------- helpers

  private static SuperclassSources superclassField(final String field) {
    return new SuperclassSources(
        new Source(
            "sample.Base",
            IMPORTS
                + "public abstract class Base extends MsgpackBufferValue<Sample> { %s }"
                    .formatted(field)),
        new Source(
            "sample.Sample",
            IMPORTS
                + "@MsgpackPojo public final class Sample extends Base { long key; %s }"
                    .formatted(UNKNOWN)));
  }

  /** A compiled sample class with its codec, for tests that read and write bytes. */
  private record SampleCodec(
      InMemoryCompiler.Result result, Class<?> type, MsgpackCodec<Recyclable> codec) {

    Recyclable newInstance(final String className) throws ReflectiveOperationException {
      return (Recyclable) result.load(className).getConstructor().newInstance();
    }

    Recyclable newInstance() throws ReflectiveOperationException {
      return (Recyclable) type.getConstructor().newInstance();
    }

    byte[] write(final Object value) {
      final var bytes = new ExpandableArrayBuffer();
      final int length = codec.write(new MsgPackWriter().wrap(bytes, 0), (Recyclable) value);
      return java.util.Arrays.copyOf(bytes.byteArray(), length);
    }

    void read(final Object target, final byte[] message) {
      codec.read(
          new MsgPackReader().wrap(new UnsafeBuffer(message), 0, message.length),
          (Recyclable) target);
    }
  }

  private static SampleCodec codecOf(final String classSource, final Source... others) {
    final var sources = new java.util.ArrayList<>(List.of(others));
    sources.add(pojo(classSource));
    final var result = compile(sources.toArray(Source[]::new));
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
    return codecOf(
        valid(
            "@MsgpackProperty(document = true) DirectBuffer doc = new UnsafeBuffer(new byte[] {(byte) 0x80});"));
  }

  /** The message {@code {"doc": <binary>}} with the given document bytes. */
  private static byte[] documentMessage(final byte[] document) {
    return encoded(
        w -> {
          w.writeMapHeader(1);
          w.writeString(buffer("doc"));
          w.writeBinary(new UnsafeBuffer(document));
        });
  }

  /** The message {@code {"items": [{"value": v}, ...]}}. */
  private static byte[] itemsMessage(final int... values) {
    return encoded(
        w -> {
          w.writeMapHeader(1);
          w.writeString(buffer("items"));
          w.writeArrayHeader(values.length);
          for (final int value : values) {
            w.writeMapHeader(1);
            w.writeString(buffer("value"));
            w.writeInteger(value);
          }
        });
  }

  private static Source legacy() {
    return new Source(
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
        """);
  }

  private static byte[] mapWithOneEntry(final String key, final long value) {
    return encoded(
        w -> {
          w.writeMapHeader(1);
          w.writeString(buffer(key));
          w.writeInteger(value);
        });
  }

  private static byte[] encoded(final java.util.function.Consumer<MsgPackWriter> body) {
    final var buffer = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(buffer, 0);
    body.accept(writer);
    return java.util.Arrays.copyOf(buffer.byteArray(), writer.getOffset());
  }

  /** A valid class with the body, which gets an unknown properties field if it has none. */
  private static String valid(final String body) {
    return withoutUnknown(body.contains("@MsgpackUnknownProperties") ? body : body + UNKNOWN);
  }

  private static String withoutUnknown(final String body) {
    return """
    @MsgpackPojo
    public final class Sample extends MsgpackBufferValue<Sample> {
      %s
    }
    """
        .formatted(body);
  }

  private static Source pojo(final String classSource) {
    final var name = classSource.contains("class Bad") ? "Bad" : "Sample";
    return new Source("sample." + name, IMPORTS + classSource);
  }

  private static Source inner() {
    return new Source(
        "sample.Inner",
        IMPORTS
            + """
            @MsgpackPojo
            public final class Inner extends MsgpackBufferValue<Inner> {
              int value = 3;
              @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();
            }
            """);
  }

  private static Source enumKind() {
    return new Source("sample.Kind", "package sample; public enum Kind { ONE, TWO }");
  }

  /** A message with a single integer entry. */
  private static DirectBuffer message(final String key, final long value) {
    final var bytes = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter().wrap(bytes, 0);
    writer.writeMapHeader(1);
    writer.writeString(buffer(key));
    writer.writeInteger(value);
    return new UnsafeBuffer(bytes, 0, writer.getOffset());
  }

  /** Reads the message into a new, reset sample, the way MsgpackBufferValue does. */
  private static Recyclable read(final Result result, final DirectBuffer message)
      throws ReflectiveOperationException {
    final var sampleClass = result.load("sample.Sample");
    @SuppressWarnings("unchecked")
    final var codec =
        (MsgpackCodec<Recyclable>) Codecs.of(sampleClass.asSubclass(Recyclable.class));
    final var target = (Recyclable) sampleClass.getConstructor().newInstance();
    target.reset();
    codec.read(new MsgPackReader().wrap(message, 0, message.capacity()), target);
    return target;
  }

  private static DirectBuffer buffer(final String s) {
    return new UnsafeBuffer(s.getBytes(StandardCharsets.UTF_8));
  }

  private record SuperclassSources(Source base, Source pojo) {}
}
