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

import io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.Source;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Tuple;

/**
 * The random POJO shapes of {@link GeneratedCodecPropertyTest}: any mix of the supported field
 * types and custom keys, their values, and the classes compiled from them.
 */
final class RandomShapes {

  static final String[] KIND_NAMES = {"ONE", "TWO", "THREE"};
  static final int DEFAULT_NESTED_VALUE = 3;
  static final int[] LENGTHS = {0, 1, 5, 31, 32, 33, 255, 256};
  private static final int DEFAULT_KIND = 1;
  private static final List<Long> BOUNDARIES =
      List.of(
          -33L,
          -32L,
          -1L,
          0L,
          127L,
          128L,
          255L,
          256L,
          65_535L,
          65_536L,
          -128L,
          -129L,
          -32_768L,
          -32_769L,
          (long) Integer.MAX_VALUE,
          (long) Integer.MIN_VALUE,
          (long) Integer.MAX_VALUE + 1,
          (long) Integer.MIN_VALUE - 1,
          4_294_967_295L,
          4_294_967_296L);

  private static final Map<String, Compiled> COMPILED = new ConcurrentHashMap<>();

  private RandomShapes() {}

  static Arbitrary<Scenario> scenarios() {
    return schemas()
        .flatMap(
            schema ->
                Combinators.combine(
                        valuesOf(schema),
                        valuesOf(schema),
                        Arbitraries.frequency(Tuple.of(3, true), Tuple.of(1, false))
                            .list()
                            .ofSize(schema.fields().size()),
                        Arbitraries.longs(),
                        unknownEntries(),
                        Arbitraries.integers().between(0, 16))
                    .as(
                        (values, previous, present, seed, unknowns, offset) ->
                            new Scenario(
                                schema, values, previous, present, seed, unknowns, offset)));
  }

  private static Arbitrary<Schema> schemas() {
    return Arbitraries.of(Type.values())
        .list()
        .ofMinSize(1)
        .ofMaxSize(12)
        .flatMap(
            types -> {
              final var fields = new ArrayList<Arbitrary<FieldSpec>>();
              for (int i = 0; i < types.size(); i++) {
                final int index = i;
                final var type = types.get(i);
                fields.add(
                    Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .withChars('-', '_', 'é', '"', '\\')
                        .ofMaxLength(40)
                        .injectNull(0.5)
                        .map(suffix -> new FieldSpec(index, type, suffix)));
              }
              return Combinators.combine(fields)
                  .as(list -> list)
                  .flatMap(
                      list -> Arbitraries.of(true, false).map(empty -> new Schema(list, empty)));
            });
  }

  private static Arbitrary<List<Object>> valuesOf(final Schema schema) {
    final var perField = new ArrayList<Arbitrary<Object>>();
    for (final var field : schema.fields()) {
      perField.add(valueOf(field.type()));
    }
    return Combinators.combine(perField).as(list -> list);
  }

  private static Arbitrary<Object> valueOf(final Type type) {
    return switch (type) {
      case LONG -> longs().map(v -> (Object) v);
      case INT -> ints().map(v -> (Object) v);
      case BOOLEAN -> Arbitraries.of(true, false).map(v -> (Object) v);
      case STRING -> stringBytes(lengths()).map(v -> (Object) v);
      case BINARY -> binaryBytes(lengths()).map(v -> (Object) v);
      case RAW -> Arbitraries.longs().map(ReferenceModel::randomValue).map(v -> (Object) v);
      case DOCUMENT -> Arbitraries.longs().map(ReferenceModel::randomDocument).map(v -> (Object) v);
      case ENUM -> Arbitraries.integers().between(0, KIND_NAMES.length - 1).map(v -> (Object) v);
      case STRING_LIST ->
          stringBytes(Arbitraries.integers().between(0, 40))
              .list()
              .ofMaxSize(20)
              .map(v -> (Object) v);
      case LONG_LIST -> longs().list().ofMaxSize(20).map(v -> (Object) v);
      case POJO_LIST, VALUE_LIST ->
          Combinators.combine(
                  Arbitraries.integers().injectNull(0.3),
                  stringBytes(Arbitraries.integers().between(0, 40)).injectNull(0.3),
                  Arbitraries.just(false))
              .as(NestedValue::new)
              .list()
              .ofMaxSize(6)
              .map(v -> (Object) v);
      case NESTED ->
          Combinators.combine(
                  Arbitraries.integers().injectNull(0.3),
                  stringBytes(Arbitraries.integers().between(0, 40)).injectNull(0.3),
                  Arbitraries.of(true, false))
              .as(NestedValue::new)
              .map(v -> (Object) v);
    };
  }

  private static Arbitrary<List<UnknownEntry>> unknownEntries() {
    return Combinators.combine(
            Arbitraries.strings().alpha().ofMaxLength(35),
            Arbitraries.longs().map(ReferenceModel::randomValue))
        .as(UnknownEntry::new)
        .list()
        .ofMaxSize(4);
  }

  private static Arbitrary<Integer> lengths() {
    return Arbitraries.oneOf(
        Arbitraries.of(0, 1, 31, 32, 255, 256, 300), Arbitraries.integers().between(0, 300));
  }

  private static Arbitrary<Long> longs() {
    return Arbitraries.oneOf(
        Arbitraries.longs(), Arbitraries.longs().between(-40, 70_000), Arbitraries.of(BOUNDARIES));
  }

  private static Arbitrary<Integer> ints() {
    return Arbitraries.oneOf(
        Arbitraries.integers(),
        Arbitraries.integers().between(-40, 70_000),
        Arbitraries.of(BOUNDARIES)
            .filter(v -> v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE)
            .map(Long::intValue));
  }

  private static Arbitrary<byte[]> stringBytes(final Arbitrary<Integer> lengths) {
    return lengths.flatMap(
        n ->
            Arbitraries.strings()
                .ascii()
                .withChars('é', '€')
                .ofLength(n)
                .map(s -> s.getBytes(StandardCharsets.UTF_8)));
  }

  private static Arbitrary<byte[]> binaryBytes(final Arbitrary<Integer> lengths) {
    return lengths.flatMap(n -> Arbitraries.bytes().array(byte[].class).ofSize(n));
  }

  /** What a field holds when its key is missing: the initializer in {@link FieldSpec}. */
  static Object defaultValue(final Type type, final boolean emptyLists) {
    return switch (type) {
      case LONG -> -7L;
      case INT -> 5;
      case BOOLEAN -> true;
      case STRING, BINARY -> new byte[] {'d', 1};
      case RAW -> new byte[] {(byte) 0xc0};
      case DOCUMENT -> new byte[] {(byte) 0x80};
      case ENUM -> DEFAULT_KIND;
      case STRING_LIST -> emptyLists ? List.of() : List.of(new byte[] {'x'});
      case LONG_LIST -> emptyLists ? List.of() : List.of(9L);
      case POJO_LIST, VALUE_LIST -> List.of();
      case NESTED -> new NestedValue(null, null, false);
    };
  }

  /** The value with its missing nested parts replaced by their defaults. */
  @SuppressWarnings("unchecked")
  static Object complete(final FieldSpec field, final Object value) {
    if (field.type() == Type.POJO_LIST || field.type() == Type.VALUE_LIST) {
      return ((List<NestedValue>) value)
          .stream().map(item -> (NestedValue) complete(nestedField(), item)).toList();
    }
    if (field.type() == Type.NESTED) {
      final var nested = (NestedValue) value;
      return new NestedValue(
          nested.value() == null ? DEFAULT_NESTED_VALUE : nested.value(),
          nested.text() == null ? new byte[0] : nested.text(),
          false);
    }
    return value;
  }

  private static FieldSpec nestedField() {
    return new FieldSpec(0, Type.NESTED, null);
  }

  static Compiled compiled(final Schema schema) {
    return COMPILED.computeIfAbsent(
        schema.source(),
        source -> {
          final var result =
              compile(
                  new Source("sample.Kind", "package sample; public enum Kind { ONE, TWO, THREE }"),
                  new Source(
                      "sample.Inner",
                      InMemoryCompiler.IMPORTS
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
                            DirectBuffer text = Decoding.EMPTY;
                            final UnknownProperties unknown = new UnknownProperties();
                          }
                          """),
                  new Source(
                      "sample.Legacy",
                      """
                      package sample;
                      import io.camunda.zeebe.msgpack.UnpackedObject;
                      import io.camunda.zeebe.msgpack.property.IntegerProperty;
                      import io.camunda.zeebe.msgpack.property.StringProperty;
                      import org.agrona.DirectBuffer;
                      public final class Legacy extends UnpackedObject {
                        private final IntegerProperty value = new IntegerProperty("value", 3);
                        private final StringProperty text = new StringProperty("text", "");
                        public Legacy() {
                          super(2);
                          declareProperty(value).declareProperty(text);
                        }
                        public int value() { return value.getValue(); }
                        public DirectBuffer text() { return text.getValue(); }
                      }
                      """),
                  new Source("sample.Sample", source));
          assertThat(result.errors()).as(source).isEmpty();
          try {
            final var sample = result.load("sample.Sample");
            final var inner = result.load("sample.Inner");
            final var fields = new LinkedHashMap<String, Field>();
            for (final var field : schema.fields()) {
              fields.put(field.name(), accessible(sample, field.name()));
            }
            return new Compiled(
                sample,
                result.load("sample.Kind"),
                fields,
                accessible(inner, "value"),
                accessible(inner, "text"),
                result.load("sample.Legacy"));
          } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(e);
          }
        });
  }

  private static Field accessible(final Class<?> type, final String name)
      throws NoSuchFieldException {
    final var field = type.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  enum Type {
    LONG,
    INT,
    BOOLEAN,
    STRING,
    BINARY,
    RAW,
    DOCUMENT,
    ENUM,
    STRING_LIST,
    LONG_LIST,
    POJO_LIST,
    VALUE_LIST,
    NESTED
  }

  /** A field named {@code f<index>}, stored under that name or under a custom key. */
  record FieldSpec(int index, Type type, String keySuffix) {

    String name() {
      return "f" + index;
    }

    String key() {
      return keySuffix == null ? name() : "k" + index + keySuffix;
    }

    String declaration(final boolean emptyLists) {
      final var attributes = new ArrayList<String>();
      if (keySuffix != null) {
        final var escaped = key().replace("\\", "\\\\").replace("\"", "\\\"");
        attributes.add("@Key(\"" + escaped + "\")");
      }
      if (type == Type.BINARY) {
        attributes.add("@Binary");
      }
      if (type == Type.RAW) {
        attributes.add("@Raw");
      }
      if (type == Type.DOCUMENT) {
        attributes.add("@Document");
      }
      final var annotation = attributes.isEmpty() ? "" : String.join(" ", attributes) + " ";
      return annotation
          + switch (type) {
            case LONG -> "long " + name() + " = -7L;";
            case INT -> "int " + name() + " = 5;";
            case BOOLEAN -> "boolean " + name() + " = true;";
            case STRING, BINARY ->
                "DirectBuffer " + name() + " = new UnsafeBuffer(new byte[] {'d', 1});";
            case RAW -> "DirectBuffer " + name() + " = new UnsafeBuffer(new byte[] {(byte) 0xc0});";
            case DOCUMENT ->
                "DirectBuffer " + name() + " = new UnsafeBuffer(new byte[] {(byte) 0x80});";
            case ENUM -> "Kind " + name() + " = Kind.TWO;";
            case STRING_LIST ->
                "final List<DirectBuffer> "
                    + name()
                    + " = new ArrayList<>("
                    + (emptyLists ? "" : "List.of(new UnsafeBuffer(new byte[] {'x'}))")
                    + ");";
            case LONG_LIST ->
                "final List<Long> "
                    + name()
                    + " = new ArrayList<>("
                    + (emptyLists ? "" : "List.of(9L)")
                    + ");";
            case POJO_LIST -> "final List<Inner> " + name() + " = new ArrayList<>();";
            case VALUE_LIST -> "final List<Legacy> " + name() + " = new ArrayList<>();";
            case NESTED -> "final Inner " + name() + " = new Inner();";
          };
    }
  }

  /**
   * @param emptyLists whether list fields start empty, as an UnpackedObject's ArrayProperty does
   */
  record Schema(List<FieldSpec> fields, boolean emptyLists) {

    String source() {
      final var s = new StringBuilder(InMemoryCompiler.IMPORTS);
      s.append("@MsgpackPojo public final class Sample extends MsgpackBufferValue<Sample> {\n");
      for (final var field : fields) {
        s.append("  ").append(field.declaration(emptyLists)).append('\n');
      }
      s.append("  final UnknownProperties unknown = new UnknownProperties();\n");
      s.append(
          "  @Override protected io.camunda.zeebe.msgpack.pojo.MsgpackCodec<Sample> codec() { return SampleCodec.INSTANCE; }\n");
      s.append(
          "  @Override protected io.camunda.zeebe.msgpack.pojo.MsgpackOps<Sample> ops() { return SampleOps.INSTANCE; }\n");
      return s.append("}\n").toString();
    }
  }

  /** {@code null} parts are not written, so the nested object keeps their default. */
  record NestedValue(Integer value, byte[] text, boolean withUnknown) {}

  record UnknownEntry(String suffix, byte[] value) {}

  record Scenario(
      Schema schema,
      List<Object> values,
      List<Object> previousValues,
      List<Boolean> present,
      long orderSeed,
      List<UnknownEntry> unknowns,
      int offset) {}

  record Compiled(
      Class<?> sample,
      Class<?> kind,
      Map<String, Field> fields,
      Field innerValue,
      Field innerText,
      Class<?> legacy) {

    Object newInstance() throws ReflectiveOperationException {
      return sample.getConstructor().newInstance();
    }
  }
}
