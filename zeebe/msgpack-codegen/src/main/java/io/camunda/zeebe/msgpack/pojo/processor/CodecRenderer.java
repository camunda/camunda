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

import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Generates the {@code <Pojo>Codec} class of a {@link Model}. */
final class CodecRenderer {

  private CodecRenderer() {}

  static String codec(final Model m) {
    final var unknown = m.unknownField();
    final var count =
        m.properties().size() + (unknown != null ? " + v." + unknown + ".count()" : "");

    final var write = new StringBuilder();
    int i = 0;
    for (final var p : m.properties()) {
      final var value =
          switch (p.type()) {
            case LONG, INT -> "    n += w.writeInteger($value);\n";
            case BOOLEAN -> "    n += w.writeBoolean($value);\n";
            case STRING -> "    n += w.writeString($value);\n";
            case BINARY -> "    n += w.writeBinary($value);\n";
            case RAW -> "    n += w.writeRaw(requireRaw($value, $key));\n";
            case DOCUMENT -> "    n += w.writeBinary(Decoding.document($value));\n";
            case ENUM ->
                "    n += w.writeString(ENUM_NAMES_$i[requireEnum($value, $key).ordinal()]);\n";
            case LIST -> "$list";
            case NESTED -> "    n += CODEC_$i.write(w, $value);\n";
          };
      final var list = p.type() == Type.LIST ? writeList("v." + p.field(), p.listElement(), 0) : "";
      write.append(
          fill(
              p,
              "    n += w.writeString(KEY_$i);\n" + value,
              "$i",
              i,
              "$value",
              "v." + p.field(),
              "$list",
              list.indent(4)));
      i++;
    }
    if (unknown != null) {
      write.append(
          fill(
              """
                  if (!v.$field.isEmpty()) {
                    n += w.writeRaw(v.$field.buffer(), 0, v.$field.length());
                  }
              """,
              "$field",
              unknown));
    }

    final var length = new StringBuilder();
    i = 0;
    for (final var p : m.properties()) {
      final var value =
          switch (p.type()) {
            case LONG, INT -> "    n += MsgPackWriter.getEncodedLongValueLength($value);\n";
            case BOOLEAN -> "    n += MsgPackWriter.getEncodedBooleanValueLength();\n";
            case STRING -> "    n += MsgPackWriter.getEncodedStringLength($value.capacity());\n";
            case BINARY ->
                "    n += MsgPackWriter.getEncodedBinaryValueLength($value.capacity());\n";
            case RAW -> "    n += requireRaw($value, $key).capacity();\n";
            case DOCUMENT ->
                "    n += MsgPackWriter.getEncodedBinaryValueLength(Decoding.document($value).capacity());\n";
            case ENUM ->
                "    n += MsgPackWriter.getEncodedStringLength(ENUM_NAMES_$i[requireEnum($value, $key).ordinal()].capacity());\n";
            case LIST -> "$list";
            case NESTED -> "    n += CODEC_$i.getLength($value);\n";
          };
      final var list =
          p.type() == Type.LIST ? listLength("v." + p.field(), p.listElement(), 0) : "";
      length.append(
          fill(
              p,
              "    n += MsgPackWriter.getEncodedStringLength(KEY_$i.capacity());\n" + value,
              "$i",
              i,
              "$value",
              "v." + p.field(),
              "$list",
              list.indent(4)));
      i++;
    }
    if (unknown != null) {
      length.append(fill("    n += v.$field.length();\n", "$field", unknown));
    }

    final var cases = new StringBuilder();
    i = 0;
    for (final var p : m.properties()) {
      final var statement =
          switch (p.type()) {
            case LONG -> "target.$field = r.readInteger();";
            case INT -> "target.$field = Decoding.readInt(r);";
            case BOOLEAN -> "target.$field = r.readBoolean();";
            case STRING ->
                p.inPlace()
                    ? "Decoding.readString(r, target.$field);"
                    : "target.$field = Decoding.readString(r);";
            case RAW ->
                p.inPlace()
                    ? "Decoding.readRaw(r, target.$field);"
                    : "target.$field = Decoding.readRaw(r);";
            case BINARY ->
                p.inPlace()
                    ? "Decoding.readBinary(r, target.$field);"
                    : "target.$field = Decoding.readBinary(r);";
            case DOCUMENT ->
                p.inPlace()
                    ? "Decoding.readDocument(r, target.$field);"
                    : "target.$field = Decoding.readDocument(r);";
            case ENUM -> "target.$field = Decoding.readEnum(r, ENUM_NAMES_$i, ENUM_VALUES_$i);";
            case LIST ->
                """
                {
                          target.$field.clear();
                $list\
                        }\
                """;
            case NESTED -> "CODEC_$i.read(r, target.$field);";
          };
      final var list =
          p.type() == Type.LIST ? readList("target." + p.field(), p.listElement(), 0) : "";
      cases.append(
          fill(p, "        case $i -> " + statement + "\n", "$i", i, "$list", list.indent(10)));
      i++;
    }
    cases.append(
        unknown != null
            ? fill(
                """
                        default -> {
                          r.skipValue();
                          target.$field.append(r.getBuffer(), entryStart, r.getOffset() - entryStart);
                        }
                """,
                "$field",
                unknown)
            : "        default -> r.skipValue();\n");
    // an enum field holds no value if neither the input nor its initializer gave it one
    final var requireEnums = new StringBuilder();
    for (final var p : m.properties()) {
      if (p.type() == Type.ENUM) {
        requireEnums.append(
            fill(
                p,
                """
                    if (target.$field == null) {
                      throw missing($key);
                    }
                """));
      }
    }

    return HEADER
        + fill(
            """
            package $pkg;

            import io.camunda.zeebe.msgpack.pojo.Codecs;
            import io.camunda.zeebe.msgpack.pojo.Decoding;
            import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
            import io.camunda.zeebe.msgpack.spec.MsgPackReader;
            import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
            import org.agrona.DirectBuffer;
            import org.agrona.concurrent.UnsafeBuffer;

            /** Generated, stateless and thread-safe. */
            public final class $pojoCodec implements MsgpackCodec<$pojo> {
              public static final $pojoCodec INSTANCE = new $pojoCodec();

            $constants\

              private $pojoCodec() {}

              @Override
              public int write(final MsgPackWriter w, final $pojo v) {
                int n = w.writeMapHeader($count);
            $write\
                return n;
              }

              @Override
              public int getLength(final $pojo v) {
                int n = MsgPackWriter.getEncodedMapHeaderLength($count);
            $length\
                return n;
              }

              @Override
              public void read(final MsgPackReader r, final $pojo target) {
                final int size = r.readMapHeader();
                for (int i = 0; i < size; i++) {
            $entryStart\
                  final int keyLength = r.readStringLength();
                  final int keyOffset = r.getOffset();
                  r.skipBytes(keyLength);
                  try {
                  switch (indexOf(r.getBuffer(), keyOffset, keyLength, i)) {
            $cases\
                  }
                  } catch (final RuntimeException e) {
                    throw Decoding.propertyFailure(r.getBuffer(), keyOffset, keyLength, e);
                  }
                }
            $requireEnums\
              }

              /** Keys are written in declaration order, so the expected index is tried first. */
              private static int indexOf(
                  final DirectBuffer buffer, final int offset, final int length, final int hint) {
                final int n = KEYS.length;
                for (int k = 0; k < n; k++) {
                  final int index = (hint + k) % n;
                  if (Decoding.keyEquals(KEYS[index], buffer, offset, length)) {
                    return index;
                  }
                }
                return -1;
              }

              private static DirectBuffer requireRaw(final DirectBuffer value, final String key) {
                if (value.capacity() == 0) {
                  throw new IllegalStateException("Property '" + key + "' holds no encoded value");
                }
                return value;
              }

              private static IllegalStateException missing(final String key) {
                return new IllegalStateException("Property '" + key + "' has no valid value");
              }

              private static <E> E requireEnum(final E value, final String key) {
                if (value == null) {
                  throw new IllegalStateException("Property '" + key + "' has no value");
                }
                return value;
              }
            }
            """,
            "$pkg",
            m.pkg(),
            "$pojo",
            m.name(),
            "$constants",
            constants(m),
            "$count",
            count,
            "$write",
            write,
            "$length",
            length,
            "$entryStart",
            unknown != null ? "      final int entryStart = r.getOffset();\n" : "",
            "$cases",
            cases,
            "$requireEnums",
            requireEnums);
  }

  /**
   * Writes a list as an array, and each element with the code for its type. An element that is a
   * list is written by the same method one level deeper.
   */
  private static String writeList(final String list, final ListElement e, final int depth) {
    final var x = e.at(list, depth);
    final var element =
        switch (e.kind()) {
          case STRING -> fill("n += w.writeString($x);\n", "$x", x);
          case LONG, INT -> fill("n += w.writeInteger($x);\n", "$x", x);
          case LIST -> writeList(x, e.inner(), depth + 1);
        };
    return fill("n += w.writeArrayHeader($list.size());\n", "$list", list)
        + loop(list, depth, element);
  }

  /** The length of {@link #writeList}. */
  private static String listLength(final String list, final ListElement e, final int depth) {
    final var x = e.at(list, depth);
    final var element =
        switch (e.kind()) {
          case STRING ->
              fill("n += MsgPackWriter.getEncodedStringLength($x.capacity());\n", "$x", x);
          case LONG, INT -> fill("n += MsgPackWriter.getEncodedLongValueLength($x);\n", "$x", x);
          case LIST -> listLength(x, e.inner(), depth + 1);
        };
    return fill("n += MsgPackWriter.getEncodedArrayHeaderLength($list.size());\n", "$list", list)
        + loop(list, depth, element);
  }

  /**
   * Reads an array and adds each element to the list, which must be empty. An element that is a
   * list is a new {@code ArrayList}, read by the same method one level deeper; the locals have the
   * depth in their name ({@code n0}, {@code i0}, {@code e0}, ...), so the levels do not clash.
   */
  private static String readList(final String list, final ListElement e, final int depth) {
    final var x = "e" + depth;
    final var element =
        switch (e.kind()) {
          case STRING -> fill("$list.add(Decoding.readString(r));\n", "$list", list);
          case LONG -> fill("$list.$add(r.readInteger());\n", "$list", list, "$add", e.add());
          case INT -> fill("$list.$add(Decoding.readInt(r));\n", "$list", list, "$add", e.add());
          case LIST ->
              fill(
                  """
                  final $type $x = new java.util.ArrayList<>();
                  $read\
                  $list.add($x);
                  """,
                  "$type",
                  e.javaType(),
                  "$x",
                  x,
                  "$read",
                  readList(x, e.inner(), depth + 1),
                  "$list",
                  list);
        };
    return fill(
        """
        final int $n = r.readArrayHeader();
        for (int $i = 0; $i < $n; $i++) {
        $element\
        }
        """,
        "$n",
        "n" + depth,
        "$i",
        "i" + depth,
        "$element",
        element.indent(2));
  }

  private static String constants(final Model m) {
    final var s = new StringBuilder();
    int i = 0;
    for (final var p : m.properties()) {
      s.append(
          fill(
              p,
              "  private static final UnsafeBuffer KEY_$i = Decoding.encodedKey($key);\n",
              "$i",
              i));
      i++;
    }
    final var keys = IntStream.range(0, i).mapToObj(k -> "KEY_" + k);
    s.append(
        fill(
            "  private static final UnsafeBuffer[] KEYS = {$keys};\n",
            "$keys",
            keys.collect(Collectors.joining(", "))));
    i = 0;
    for (final var p : m.properties()) {
      if (p.type() == Type.ENUM) {
        s.append(
            fill(
                p,
                """
                  private static final $type[] ENUM_VALUES_$i = $type.values();
                  private static final UnsafeBuffer[] ENUM_NAMES_$i =
                      new UnsafeBuffer[ENUM_VALUES_$i.length];

                  static {
                    for (int i = 0; i < ENUM_VALUES_$i.length; i++) {
                      ENUM_NAMES_$i[i] = Decoding.encodedKey(ENUM_VALUES_$i[i].name());
                    }
                  }
                """,
                "$i",
                i));
      } else if (p.type() == Type.NESTED) {
        s.append(
            fill(
                p,
                "  private static final MsgpackCodec<$type> CODEC_$i = Codecs.of($type.class);\n",
                "$i",
                i));
      }
      i++;
    }
    return s.toString();
  }
}
