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
    final var hasUnset = m.properties().stream().anyMatch(Property::canBeUnset);
    final var hasWriteCheck = m.properties().stream().anyMatch(Property::checkedOnWrite);

    final var write = new StringBuilder();
    int i = 0;
    for (final var p : m.properties()) {
      final var value =
          switch (p.type()) {
            case LONG, INT, BOXED_LONG, BOXED_INT, REQUIRED_LONG, REQUIRED_INT ->
                "    n += w.writeInteger($value);\n";
            case BOOLEAN, BOXED_BOOLEAN -> "    n += w.writeBoolean($value);\n";
            case STRING -> "    n += w.writeString($value);\n";
            case BINARY -> "    n += w.writeBinary($value);\n";
            case RAW -> "    n += w.writeRaw(Decoding.raw($value, $key));\n";
            case DOCUMENT -> "    n += w.writeBinary(Decoding.document($value));\n";
            case ENUM -> "    n += w.writeString(ENUM_NAMES_$i[$value.ordinal()]);\n";
            case LIST -> "$list";
            case NESTED -> "    n += CODEC_$i.write(w, $value);\n";
          };
      final var list =
          p.type() == Type.LIST
              ? writeList("v." + p.field(), p.listElement(), 0, "CODEC_" + i)
              : "";
      write.append(
          fill(
              p,
              "    n += w.writeString(KEY_$i);\n" + value,
              "$i",
              i,
              "$value",
              p.value("v"),
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
            case LONG, INT, BOXED_LONG, BOXED_INT, REQUIRED_LONG, REQUIRED_INT ->
                "    n += MsgPackWriter.getEncodedLongValueLength($value);\n";
            case BOOLEAN, BOXED_BOOLEAN ->
                "    n += MsgPackWriter.getEncodedBooleanValueLength();\n";
            case STRING -> "    n += MsgPackWriter.getEncodedStringLength($value.capacity());\n";
            case BINARY ->
                "    n += MsgPackWriter.getEncodedBinaryValueLength($value.capacity());\n";
            case RAW -> "    n += Decoding.raw($value, $key).capacity();\n";
            case DOCUMENT ->
                "    n += MsgPackWriter.getEncodedBinaryValueLength(Decoding.document($value).capacity());\n";
            case ENUM ->
                "    n += MsgPackWriter.getEncodedStringLength(ENUM_NAMES_$i[$value.ordinal()].capacity());\n";
            case LIST -> "$list";
            case NESTED -> "    n += CODEC_$i.getLength($value);\n";
          };
      final var list =
          p.type() == Type.LIST
              ? listLength("v." + p.field(), p.listElement(), 0, "CODEC_" + i)
              : "";
      length.append(
          fill(
              p,
              "    n += MsgPackWriter.getEncodedStringLength(KEY_$i.capacity());\n" + value,
              "$i",
              i,
              "$value",
              p.value("v"),
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
            case LONG, BOXED_LONG -> "target.$field = r.readInteger();";
            case INT, BOXED_INT -> "target.$field = Decoding.readInt(r);";
            case BOOLEAN, BOXED_BOOLEAN -> "target.$field = r.readBoolean();";
            case REQUIRED_LONG -> "target.$field.set(r.readInteger());";
            case REQUIRED_INT -> "target.$field.set(Decoding.readInt(r));";
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
          p.type() == Type.LIST
              ? readList("target." + p.field(), p.listElement(), 0, "CODEC_" + i)
              : "";
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
            : "        default -> Decoding.skipValue(r);\n");

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
            $verifyOnWrite\
                int n = w.writeMapHeader($count);
            $write\
                return n;
              }

              @Override
              public int getLength(final $pojo v) {
            $verifyOnLength\
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
            $requireSet\
              }

            $unsetChecks\
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
            "$verifyOnWrite",
            hasWriteCheck ? "    verifyFieldsAreSet(v, UNSET_ON_WRITE);\n" : "",
            "$write",
            write,
            "$verifyOnLength",
            hasWriteCheck ? "    verifyFieldsAreSet(v, UNSET_ON_LENGTH);\n" : "",
            "$length",
            length,
            "$entryStart",
            unknown != null ? "      final int entryStart = r.getOffset();\n" : "",
            "$cases",
            cases.toString().indent(2),
            // like ObjectValue, a property without default must be in the input
            "$requireSet",
            hasUnset
                ? """
                      final String unset = firstUnset(target, false);
                      if (unset != null) {
                        throw new RuntimeException("Property '" + unset + "' has no valid value");
                      }
                  """
                : "",
            "$unsetChecks",
            hasWriteCheck ? unsetChecks(m) : "");
  }

  /**
   * The check of the properties that can be unset, in the order of declaration, with the messages
   * of {@code ObjectValue}: one for writing, one for the length, and the one used after reading.
   */
  private static String unsetChecks(final Model m) {
    final var checks = new StringBuilder();
    for (final var p : m.properties()) {
      if (p.checkedOnWrite()) {
        final var unset = p.canBeUnset() ? p.unset("v") : fill(p, "write && v.$field == null");
        checks.append(
            fill(
                p,
                """
                    if ($unset) {
                      return $key;
                    }
                """,
                "$unset",
                unset));
      }
    }
    return fill(
        """
          private static final String UNSET_ON_WRITE =
              "Expected a value or default value to be set before writing, but has nothing";
          private static final String UNSET_ON_LENGTH =
              "Expected a value or default value to be specified, but has nothing";

          private static void verifyFieldsAreSet(final $pojo v, final String message) {
            final String unset = firstUnset(v, true);
            if (unset != null) {
              throw new io.camunda.zeebe.msgpack.MsgpackPropertyException(
                  new io.camunda.zeebe.msgpack.value.StringValue(unset), message);
            }
          }

          /**
           * The key of the first property without a value, or null. A non-nullable enum is only
           * checked when writing, as it has a default unless it is assigned null.
           */
          private static String firstUnset(final $pojo v, final boolean write) {
        $checks\
            return null;
          }

        """,
        "$pojo",
        m.name(),
        "$checks",
        checks);
  }

  /**
   * Writes a list as an array, and each element with the code for its type. An element that is a
   * list is written by the same method one level deeper.
   */
  private static String writeList(
      final String list, final ListElement e, final int depth, final String codec) {
    final var x = e.at(list, depth);
    final var element =
        switch (e.kind()) {
          case STRING -> fill("n += w.writeString($x);\n", "$x", x);
          case LONG, INT -> fill("n += w.writeInteger($x);\n", "$x", x);
          case ITEM -> fill("n += $codec.write(w, $x);\n", "$codec", codec, "$x", x);
          case LIST -> writeList(x, e.inner(), depth + 1, codec);
        };
    return fill("n += w.writeArrayHeader($list.size());\n", "$list", list)
        + loop(list, depth, element);
  }

  /** The length of {@link #writeList}. */
  private static String listLength(
      final String list, final ListElement e, final int depth, final String codec) {
    final var x = e.at(list, depth);
    final var element =
        switch (e.kind()) {
          case STRING ->
              fill("n += MsgPackWriter.getEncodedStringLength($x.capacity());\n", "$x", x);
          case LONG, INT -> fill("n += MsgPackWriter.getEncodedLongValueLength($x);\n", "$x", x);
          case ITEM -> fill("n += $codec.getLength($x);\n", "$codec", codec, "$x", x);
          case LIST -> listLength(x, e.inner(), depth + 1, codec);
        };
    return fill("n += MsgPackWriter.getEncodedArrayHeaderLength($list.size());\n", "$list", list)
        + loop(list, depth, element);
  }

  /**
   * Reads an array and adds each element to the list, which must be empty. An element that is a
   * list is a new {@code ArrayList}, read by the same method one level deeper; the locals have the
   * depth in their name ({@code n0}, {@code i0}, {@code e0}, ...), so the levels do not clash.
   */
  private static String readList(
      final String list, final ListElement e, final int depth, final String codec) {
    final var x = "e" + depth;
    final var element =
        switch (e.kind()) {
          case STRING -> fill("$list.add(Decoding.readString(r));\n", "$list", list);
          case LONG -> fill("$list.$add(r.readInteger());\n", "$list", list, "$add", e.add());
          case INT -> fill("$list.$add(Decoding.readInt(r));\n", "$list", list, "$add", e.add());
          case ITEM ->
              fill(
                  """
                  final $type $x = new $type();
                  $codec.read(r, $x);
                  $list.add($x);
                  """,
                  "$type",
                  e.javaType(),
                  "$x",
                  x,
                  "$codec",
                  codec,
                  "$list",
                  list);
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
                  readList(x, e.inner(), depth + 1, codec),
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
      } else if (p.type() == Type.NESTED || p.type() == Type.LIST && p.listElement().items()) {
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
