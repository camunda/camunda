/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import static io.camunda.zeebe.msgpack.pojo.processor.Templates.HEADER;
import static io.camunda.zeebe.msgpack.pojo.processor.Templates.fill;

/** Generates the {@code <Pojo>Ops} class of a {@link Model}. */
final class OpsRenderer {

  private OpsRenderer() {}

  static String ops(final Model m) {
    final var reset = new StringBuilder();
    for (final var p : m.properties()) {
      reset.append(fill(p, resetStatement(p)));
    }
    if (m.unknownField() != null) {
      reset.append(fill("    target.$field.clear();\n", "$field", m.unknownField()));
    }
    return HEADER
        + fill(
            """
            package $pkg;

            import io.camunda.zeebe.msgpack.pojo.MsgpackOps;

            /** Generated and stateless. The operations on {@link $pojo} besides reading and writing. */
            public final class $pojoOps {
              public static final MsgpackOps<$pojo> INSTANCE = new Instance();

              private static final $pojo DEFAULTS = new $pojo();

              private $pojoOps() {}

              /** Restores the field initializers. */
              public static void reset(final $pojo target) {
            $reset\
              }

              private static <T> T requireDefault(final T value, final String field) {
                if (value == null) {
                  throw new IllegalStateException(
                      "Field " + field + " of $pojo has no default: give it an initializer");
                }
                return value;
              }

              /** For code that is generic in the type; it delegates to the static methods. */
              private static final class Instance implements MsgpackOps<$pojo> {
                @Override
                public void reset(final $pojo target) {
                  $pojoOps.reset(target);
                }

              }
            }
            """,
            "$pkg",
            m.pkg(),
            "$pojo",
            m.name(),
            "$reset",
            reset);
  }

  /** The statements of {@code reset} that restore the initializer of a property. */
  private static String resetStatement(final Property p) {
    return switch (p.type()) {
      case LIST ->
          """
              target.$field.clear();
              target.$field.addAll(DEFAULTS.$field);
          """;
      case NESTED -> "    target.$field.reset();\n";
      // checked on every reset: an exception in the static initializer is not reliably
      // propagated, as the class is initialized while Codecs resolves these ops
      case STRING, BINARY, RAW, DOCUMENT ->
          p.inPlace()
              ? "    target.$field.wrap(DEFAULTS.$field);\n"
              : "    target.$field = requireDefault(DEFAULTS.$field, \"$field\");\n";
      default -> "    target.$field = DEFAULTS.$field;\n";
    };
  }
}
