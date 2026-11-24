/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.pojo.processor.ListElement.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Checks the source of {@code <Name>Ops} for the properties whose code is only reached while a
 * class is compiled. The behaviour of the generated code is tested by the {@code Generated*Test}s
 * on classes compiled once for all of their tests, which is not attributed to each of those tests
 * by a mutation testing run.
 */
final class OpsRendererTest {

  private static final ListElement LONG =
      new ListElement(Kind.LONG, "java.lang.Long", false, false, null);
  private static final ListElement LONGS =
      new ListElement(Kind.LIST, "java.util.List<java.lang.Long>", false, false, LONG);

  @Test
  void shouldResetAHolderByResettingIt() {
    // given
    final var key =
        property(
            Type.REQUIRED_LONG,
            "key",
            "io.camunda.zeebe.msgpack.pojo.RequiredLong",
            Presence.HOLDER,
            null);

    // when
    final var ops = OpsRenderer.ops(model(key));

    // then
    // the copy of an unset holder resets it too, so look at the reset method only
    assertThat(resetMethod(ops)).contains("    target.key.reset();\n");
  }

  @Test
  void shouldResetANullableFieldToItsDefault() {
    // given
    final var name = property(Type.BOXED_LONG, "name", "java.lang.Long", Presence.NULLABLE, null);

    // when
    final var ops = OpsRenderer.ops(model(name));

    // then
    assertThat(resetMethod(ops)).contains("    target.name = DEFAULTS.name;\n");
  }

  @Test
  void shouldCompareAndHashTheSizeOfEveryLevelOfAListOfLists() {
    // given
    final var paths =
        property(
            Type.LIST,
            "paths",
            "java.util.List<java.util.List<java.lang.Long>>",
            Presence.DEFAULT,
            LONGS);

    // when
    final var ops = OpsRenderer.ops(model(paths));

    // then the outer list and the inner lists are both compared and hashed
    assertThat(ops.split("\\.size\\(\\) != ", -1)).as("size comparisons").hasSize(3);
    assertThat(ops.split("h = 31 \\* h \\+ [^;]*\\.size\\(\\);", -1)).as("size hashes").hasSize(3);
  }

  /** The body of {@code reset}. */
  private static String resetMethod(final String ops) {
    final var start = ops.indexOf("public static void reset(final Sample target) {");
    return ops.substring(start, ops.indexOf("\n  }\n", start) + 1);
  }

  private static Model model(final Property property) {
    return new Model("sample", "Sample", List.of(property), null);
  }

  private static Property property(
      final Type type,
      final String field,
      final String javaType,
      final Presence presence,
      final ListElement element) {
    return new Property(
        "\"" + field + "\"", type, field, javaType, false, presence, false, false, element);
  }
}
