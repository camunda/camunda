/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.compile;
import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.pojo;
import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.pojoClass;
import static io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.source;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.pojo.processor.InMemoryCompiler.Source;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Compiles classes in memory with the processor and checks which ones it rejects, and why. */
final class MsgpackPojoProcessorRejectionTest {

  /** Each case fails to compile with an error that contains every fragment. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidPojos")
  void shouldRejectAnInvalidPojo(
      final String description, final List<Source> sources, final List<String> fragments) {
    // when
    final var result = compile(sources.toArray(Source[]::new));

    // then
    assertThat(result.errors())
        .as(description)
        .anyMatch(e -> fragments.stream().allMatch(e::contains));
  }

  static Stream<Arguments> invalidPojos() {
    return Stream.of(
        classes(
            "not Recyclable",
            List.of(source("Bad", "@MsgpackPojo public final class Bad { long key; }")),
            "must implement",
            "Recyclable"),
        classes(
            "not top-level",
            List.of(
                source(
                    "Outer",
                    "public class Outer { %s }"
                        .formatted(
                            pojoClass("Sample", "long key;").replace("final", "static final")))),
            "top-level"),
        classes(
            "field of a superclass",
            superclassField("long inherited;"),
            "inherited",
            "would not be stored"),
        classes(
            "no unknown properties field",
            List.of(source("Sample", pojoClass("Sample", "long key;"))),
            "needs a final UnknownProperties field"),
        fields(
            """
            long key;
            final UnknownProperties first = new UnknownProperties();
            final UnknownProperties second = new UnknownProperties();
            """,
            "first",
            "second",
            "both"),
        fields(
            "long key; UnknownProperties extra = new UnknownProperties();",
            "extra",
            "must be final"),
        classes(
            "unknown properties field with unknownProperties = false",
            List.of(
                source(
                    "Sample",
                    pojoClass(
                            "Sample",
                            "long key; final UnknownProperties extra = new UnknownProperties();")
                        .replace("@MsgpackPojo", "@MsgpackPojo(unknownProperties = false)"))),
            "extra",
            "must not have an UnknownProperties field"),
        fields("long key; @Key(\"key\") long other;", "other", "'key'"),
        fields("long key; @Key(\"\") long other;", "other", "@Key must not be empty"),
        fields("private long key;", "key", "must not be private"),
        fields("final long key = 1L;", "key", "must not be final"),
        fields("List<DirectBuffer> key = new ArrayList<>();", "key", "must be final"),
        fields("List<Long> key = new ArrayList<>();", "key", "must be final"),
        fields("Inner key = new Inner();", "key", "must be final"),
        fields(
            "UnsafeBuffer key = new UnsafeBuffer(0, 0);", "key", "must be final", "DirectBuffer"),
        fields("Map<String, String> key;", "Unsupported type", "key"),
        fields("final StringBuilder key = new StringBuilder();", "Unsupported type", "key"),
        fields("final List<List<String>> key = new ArrayList<>();", "Unsupported type", "key"),
        fields("final List key = new ArrayList();", "Unsupported type", "key"),
        fields("final List<long[]> key = new ArrayList<>();", "Unsupported type", "key"),
        classes(
            "enum overriding toString()",
            List.of(
                source(
                    "BadKind",
                    "public enum BadKind { ONE; @Override public String toString() { return \"one\"; } }"),
                pojo("Sample", "BadKind kind = BadKind.ONE;")),
            "overrides toString()"),
        // agrona lists
        fields("IntArrayList key = new IntArrayList();", "key", "must be final"),
        fields("LongArrayList key = new LongArrayList();", "key", "must be final"),
        // documents
        fields("@Document long doc;", "doc", "document requires a DirectBuffer"),
        fields(
            "@Document @Raw DirectBuffer doc = Decoding.EMPTY;",
            "doc",
            "document cannot be combined"),
        fields(
            "@Document @Binary DirectBuffer doc = Decoding.EMPTY;",
            "doc",
            "document cannot be combined"),
        fields("@Document final DirectBuffer doc = Decoding.EMPTY;", "doc", "must not be final"),
        // lists of nested objects
        fields("List<Inner> key = new ArrayList<>();", "key", "must be final"),
        fields("final List<String> items = new ArrayList<>();", "Unsupported type", "items"),
        classes(
            "list item without a public constructor",
            List.of(
                pojo("Hidden", "int value; Hidden() {}"),
                pojo("Sample", "final List<Hidden> items = new ArrayList<>();")),
            "items",
            "public constructor without parameters"),
        classes(
            "list of its own class",
            List.of(pojo("Sample", "final List<Sample> children = new ArrayList<>();")),
            "must not contain themselves",
            "Sample -> Sample"),
        classes(
            "classes that contain each other",
            List.of(
                pojo("First", "final List<Second> items = new ArrayList<>();"),
                pojo("Second", "final List<List<First>> items = new ArrayList<>();"),
                pojo("Sample", "long key;")),
            "must not contain themselves",
            "First -> Second -> First"));
  }

  @Test
  void shouldAcceptAnIgnoredFieldOfASuperclass() {
    // when
    final var result = compile(superclassField("@Ignore long inherited;").toArray(Source[]::new));

    // then
    assertThat(result.errors()).isEmpty();
  }

  // ---------------------------------------------------------------- helpers

  /**
   * A case of {@link #invalidPojos()}: the sample class with the fields, next to Kind and Inner.
   */
  private static Arguments fields(final String fields, final String... fragments) {
    return classes(fields, List.of(enumKind(), inner(), pojo("Sample", fields)), fragments);
  }

  private static Arguments classes(
      final String description, final List<Source> sources, final String... fragments) {
    return Arguments.of(description, sources, List.of(fragments));
  }

  /** A sample class whose superclass declares the field. */
  private static List<Source> superclassField(final String field) {
    return List.of(
        source(
            "Base",
            "public abstract class Base extends MsgpackBufferValue<Sample> { %s }"
                .formatted(field)),
        source(
            "Sample",
            pojoClass(
                    "Sample",
                    "long key; final UnknownProperties unknown = new UnknownProperties();")
                .replace("extends MsgpackBufferValue<Sample>", "extends Base")));
  }

  private static Source inner() {
    return pojo("Inner", "int value = 3;");
  }

  private static Source enumKind() {
    return new Source("sample.Kind", "package sample; public enum Kind { ONE, TWO }");
  }
}
