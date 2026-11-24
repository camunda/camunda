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
import static io.camunda.zeebe.msgpack.pojo.processor.Templates.loop;

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

            import io.camunda.zeebe.msgpack.pojo.Decoding;
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

            $copy\
            $equals\
            $hashCode\
            $toString\
              /**
               * Items and inner lists are mutable and are copied by reference, so a default with
               * elements would be shared by every reset.
               */
              private static void requireEmpty(final java.util.List<?> value, final String field) {
                if (!value.isEmpty()) {
                  throw new IllegalStateException(
                      "List " + field + " of $pojo must start empty: its elements are created while reading");
                }
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

                @Override
                public void copy(final $pojo source, final $pojo target) {
                  $pojoOps.copy(source, target);
                }

                @Override
                public boolean equals(final $pojo a, final $pojo b) {
                  return $pojoOps.equals(a, b);
                }

                @Override
                public int hashCode(final $pojo value) {
                  return $pojoOps.hashCode(value);
                }

                @Override
                public String toString(final $pojo value) {
                  return $pojoOps.toString(value);
                }
              }
            }
            """,
            "$pkg",
            m.pkg(),
            "$pojo",
            m.name(),
            "$reset",
            reset,
            "$copy",
            copy(m),
            "$equals",
            equalsMethod(m),
            "$hashCode",
            hashCodeMethod(m),
            "$toString",
            toStringMethod(m));
  }

  /** The statements of {@code reset} that restore the initializer of a property. */
  private static String resetStatement(final Property p) {
    if (p.presence() == Presence.HOLDER) {
      return "    target.$field.reset();\n";
    }
    if (p.presence() == Presence.NULLABLE) {
      return "    target.$field = DEFAULTS.$field;\n";
    }
    return switch (p.type()) {
      // checked on every reset: an exception in the static initializer is not reliably
      // propagated, as the class is initialized while Codecs resolves these ops
      case LIST ->
          p.listElement().items() || p.listElement().kind() == ListElement.Kind.LIST
              ? """
                    requireEmpty(DEFAULTS.$field, "$field");
                    target.$field.clear();
                """
              : """
                    target.$field.clear();
                    target.$field.addAll(DEFAULTS.$field);
                """;
      case NESTED -> "    target.$field.reset();\n";
      case STRING, BINARY, RAW, DOCUMENT ->
          p.inPlace()
              ? "    target.$field.wrap(DEFAULTS.$field);\n"
              : "    target.$field = requireDefault(DEFAULTS.$field, \"$field\");\n";
      default -> "    target.$field = DEFAULTS.$field;\n";
    };
  }

  /** The static {@code copy(source, target)} of the ops class. */
  private static String copy(final Model m) {
    final var body = new StringBuilder();
    for (final var p : m.properties()) {
      final var statements =
          switch (p.type()) {
            case LONG, INT, BOOLEAN, BOXED_BOOLEAN, BOXED_LONG, BOXED_INT, ENUM ->
                "    target.$field = source.$field;\n";
            case REQUIRED_LONG, REQUIRED_INT ->
                """
                    if (source.$field.isSet()) {
                      target.$field.set(source.$field.get());
                    } else {
                      target.$field.reset();
                    }
                """;
            case STRING, BINARY, RAW, DOCUMENT ->
                p.inPlace()
                    ? "    Decoding.copyInto(source.$field, target.$field);\n"
                    : "    target.$field = Decoding.copy(source.$field);\n";
            case LIST -> "    target.$field.clear();\n$list";
            case NESTED ->
                p.pojo()
                    ? "    $typeOps.copy(source.$field, target.$field);\n"
                    : "    Decoding.copyValue(source.$field, target.$field);\n";
          };
      final var list =
          p.type() == Type.LIST
              ? copyList("source." + p.field(), "target." + p.field(), p.listElement(), 0)
              : "";
      body.append(fill(p, statements, "$list", list.indent(4)));
    }
    if (m.unknownField() != null) {
      body.append(fill("    target.$field.copyFrom(source.$field);\n", "$field", m.unknownField()));
    }
    return fill(
        """
          /** Deep copy of the stored properties, see {@link MsgpackOps#copy}. */
          public static void copy(final $pojo source, final $pojo target) {
            if (source == target) {
              return;
            }
        $body\
          }

        """,
        "$pojo",
        m.name(),
        "$body",
        body);
  }

  /** The static {@code equals(a, b)} of the ops class. */
  private static String equalsMethod(final Model m) {
    final var body = new StringBuilder();
    for (final var p : m.properties()) {
      final var differs =
          switch (p.type()) {
            case LONG, INT, BOOLEAN, ENUM -> "a.$field != b.$field";
            case BOXED_BOOLEAN, BOXED_LONG, BOXED_INT ->
                "!java.util.Objects.equals(a.$field, b.$field)";
            case REQUIRED_LONG, REQUIRED_INT ->
                "a.$field.isSet() != b.$field.isSet() || a.$field.isSet() && a.$field.get() != b.$field.get()";
            case STRING, BINARY, RAW, DOCUMENT -> "!Decoding.equalContent(a.$field, b.$field)";
            case NESTED ->
                p.pojo() ? "!$typeOps.equals(a.$field, b.$field)" : "!a.$field.equals(b.$field)";
            case LIST -> null;
          };
      if (p.canBeUnset()) {
        body.append(
            fill(
                """
                    if ($unsetA || $unsetB) {
                      throw Decoding.unset($key);
                    }
                """,
                "$unsetA",
                p.unset("a"),
                "$unsetB",
                p.unset("b"),
                "$key",
                p.keyLiteral()));
      }
      final var template =
          """
              if ($differs) {
                return false;
              }
          """;
      final var statements = differs != null ? fill(template, "$differs", differs) : "$list";
      final var list =
          p.type() == Type.LIST
              ? equalLists("a." + p.field(), "b." + p.field(), p.listElement(), 0)
              : "";
      body.append(fill(p, statements, "$list", list.indent(4)));
    }
    if (m.unknownField() != null) {
      body.append(
          fill(
              """
                  if (!a.$field.equals(b.$field)) {
                    return false;
                  }
              """,
              "$field",
              m.unknownField()));
    }
    return fill(
        """
          /** Equality of the stored properties, see {@link MsgpackOps#equals}. */
          public static boolean equals(final $pojo a, final $pojo b) {
            if (a == b) {
              return true;
            }
            if (a == null || b == null) {
              return false;
            }
        $body\
            return true;
          }

        """,
        "$pojo",
        m.name(),
        "$body",
        body);
  }

  /** The static {@code toString(value)} and {@code appendTo(builder, value)} of the ops class. */
  private static String toStringMethod(final Model m) {
    final var body = new StringBuilder();
    for (final var p : m.properties()) {
      final var value =
          p.sanitized()
              ? """
                    sb.append("\\"***\\"");
                """
              : switch (p.type()) {
                case LONG, INT, BOOLEAN, BOXED_BOOLEAN, BOXED_LONG, BOXED_INT ->
                    "    sb.append(v.$field);\n";
                case REQUIRED_LONG, REQUIRED_INT -> "    sb.append(v.$field.get());\n";
                case STRING -> "    Decoding.appendString(sb, v.$field);\n";
                case BINARY, DOCUMENT -> "    Decoding.appendBinary(sb, v.$field);\n";
                case RAW -> "    Decoding.appendRaw(sb, v.$field);\n";
                case ENUM -> "    Decoding.appendEnum(sb, v.$field);\n";
                case NESTED ->
                    p.pojo()
                        ? "    $typeOps.appendTo(sb, v.$field);\n"
                        : "    v.$field.writeJSON(sb);\n";
                case LIST -> "$list";
              };
      // the comma goes with the property, so a property that is left out leaves none behind
      var statements =
          """
              if (sb.length() > start) {
                sb.append(',');
              }
              sb.append("\\"").append($key).append("\\":");
          """
              + value;
      final var list =
          p.type() == Type.LIST ? appendList("v." + p.field(), p.listElement(), 0) : "";
      statements = fill(p, statements, "$list", list.indent(4));
      if (p.canBeUnset()) {
        statements =
            fill("    if (!($unset)) {\n", "$unset", p.unset("v"))
                + statements.indent(2)
                + "    }\n";
      }
      body.append(statements);
    }
    if (m.unknownField() != null) {
      body.append(fill("    v.$field.appendJson(sb, start);\n", "$field", m.unknownField()));
    }
    return fill(
        """
          /** JSON of the stored properties, see {@link MsgpackOps#toString}. */
          public static String toString(final $pojo v) {
            final StringBuilder sb = new StringBuilder();
            appendTo(sb, v);
            return sb.toString();
          }

          /** Appends {@link #toString} to a builder, for the string of a nested value. */
          public static void appendTo(final StringBuilder sb, final $pojo v) {
            sb.append('{');
            final int start = sb.length();
        $body\
            sb.append('}');
          }

        """,
        "$pojo",
        m.name(),
        "$body",
        body);
  }

  /** The static {@code hashCode(value)} of the ops class. */
  private static String hashCodeMethod(final Model m) {
    final var body = new StringBuilder();
    for (final var p : m.properties()) {
      final var hash =
          switch (p.type()) {
            case LONG -> "Long.hashCode(v.$field)";
            case INT -> "v.$field";
            case BOOLEAN -> "Boolean.hashCode(v.$field)";
            case REQUIRED_LONG -> "(v.$field.isSet() ? Long.hashCode(v.$field.get()) : -1)";
            case REQUIRED_INT -> "(v.$field.isSet() ? v.$field.get() : -1)";
            case BOXED_BOOLEAN, BOXED_LONG, BOXED_INT, ENUM ->
                "java.util.Objects.hashCode(v.$field)";
            case STRING, BINARY, RAW, DOCUMENT -> "Decoding.contentHash(v.$field)";
            // the hash code of a BaseValue is not consistent with its equals (it includes whether
            // a property was set and the recycled ones), so it is not used: equal values must hash
            // alike
            case NESTED -> p.pojo() ? "$typeOps.hashCode(v.$field)" : "0";
            case LIST -> null;
          };
      final var statements =
          hash != null ? fill("    h = 31 * h + $hash;\n", "$hash", hash) : "$list";
      final var list = p.type() == Type.LIST ? hashList("v." + p.field(), p.listElement(), 0) : "";
      body.append(fill(p, statements, "$list", list.indent(4)));
    }
    if (m.unknownField() != null) {
      body.append(fill("    h = 31 * h + v.$field.hashCode();\n", "$field", m.unknownField()));
    }
    return fill(
        """
          /** Hash code of the stored properties, see {@link MsgpackOps#hashCode}. */
          public static int hashCode(final $pojo v) {
            int h = 1;
        $body\
            return h;
          }

        """,
        "$pojo",
        m.name(),
        "$body",
        body);
  }

  /**
   * Copies each element of the list {@code source} into the empty list {@code target}: a string as
   * a copy of its bytes, an item as a new item, and a list as a new {@code ArrayList} that the same
   * method fills one level deeper.
   */
  private static String copyList(
      final String source, final String target, final ListElement e, final int depth) {
    final var x = e.at(source, depth);
    final var y = "e" + depth;
    final var element =
        switch (e.kind()) {
          case STRING -> fill("$target.add(Decoding.copy($x));\n", "$target", target, "$x", x);
          case LONG, INT ->
              fill("$target.$add($x);\n", "$target", target, "$add", e.add(), "$x", x);
          case ITEM ->
              fill(
                  """
                  final $type $y = new $type();
                  $copy;
                  $target.add($y);
                  """,
                  "$type",
                  e.javaType(),
                  "$y",
                  y,
                  "$copy",
                  fill(
                      e.pojo() ? "$typeOps.copy($x, $y)" : "Decoding.copyValue($x, $y)",
                      "$type",
                      e.javaType(),
                      "$x",
                      x,
                      "$y",
                      y),
                  "$target",
                  target);
          case LIST ->
              fill(
                  """
                  final $type $y = new java.util.ArrayList<>();
                  $copy\
                  $target.add($y);
                  """,
                  "$type",
                  e.javaType(),
                  "$y",
                  y,
                  "$copy",
                  copyList(x, y, e.inner(), depth + 1),
                  "$target",
                  target);
        };
    return loop(source, depth, element);
  }

  /**
   * Returns false if the lists differ: in size, or in an element, which a list compares by the same
   * method one level deeper.
   */
  private static String equalLists(
      final String a, final String b, final ListElement e, final int depth) {
    final var x = e.at(a, depth);
    final var y = e.at(b, depth);
    final var differs =
        switch (e.kind()) {
          case STRING -> "!Decoding.equalContent($x, $y)";
          case LONG, INT -> e.unboxed() ? "$x != $y" : "!$x.equals($y)";
          // equals() of a list would call equals() of the items, which a POJO need not implement
          case ITEM -> e.pojo() ? "!$typeOps.equals($x, $y)" : "!$x.equals($y)";
          case LIST -> null;
        };
    final var element =
        differs == null
            ? equalLists(x, y, e.inner(), depth + 1)
            : fill(
                fill(
                    """
                    if ($differs) {
                      return false;
                    }
                    """,
                    "$differs",
                    differs),
                "$type",
                e.javaType(),
                "$x",
                x,
                "$y",
                y);
    return fill(
            """
            if ($a.size() != $b.size()) {
              return false;
            }
            """,
            "$a",
            a,
            "$b",
            b)
        + loop(a, depth, element);
  }

  /**
   * Adds the size and the hash code of each element of a list to {@code h}; a list element adds its
   * own by the same method one level deeper.
   */
  private static String hashList(final String list, final ListElement e, final int depth) {
    final var x = e.at(list, depth);
    final var element =
        switch (e.kind()) {
          case STRING -> fill("h = 31 * h + Decoding.contentHash($x);\n", "$x", x);
          case LONG -> fill("h = 31 * h + Long.hashCode($x);\n", "$x", x);
          case INT -> fill("h = 31 * h + Integer.hashCode($x);\n", "$x", x);
          // nothing for a BaseValue, as its hash code is not used, see hashCodeMethod
          case ITEM ->
              e.pojo()
                  ? fill("h = 31 * h + $typeOps.hashCode($x);\n", "$type", e.javaType(), "$x", x)
                  : "";
          case LIST -> hashList(x, e.inner(), depth + 1);
        };
    final var size = fill("h = 31 * h + $list.size();\n", "$list", list);
    return element.isEmpty() ? size : size + loop(list, depth, element);
  }

  /** Appends a list as a JSON array, and a list element by the same method one level deeper. */
  private static String appendList(final String list, final ListElement e, final int depth) {
    final var x = e.at(list, depth);
    final var element =
        switch (e.kind()) {
          case STRING -> fill("Decoding.appendString(sb, $x);\n", "$x", x);
          case LONG, INT -> fill("sb.append($x);\n", "$x", x);
          case ITEM ->
              fill(
                  e.pojo() ? "$typeOps.appendTo(sb, $x);\n" : "$x.writeJSON(sb);\n",
                  "$type",
                  e.javaType(),
                  "$x",
                  x);
          case LIST -> appendList(x, e.inner(), depth + 1);
        };
    final var body =
        fill(
            """
            if ($i > 0) {
              sb.append(',');
            }
            $element\
            """,
            "$i",
            "i" + depth,
            "$element",
            element);
    return "sb.append('[');\n" + loop(list, depth, body) + "sb.append(']');\n";
  }
}
