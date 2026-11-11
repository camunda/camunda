/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

/** Generates {@code <Pojo>Serializer} and {@code <Pojo>Deserializer} for {@link MsgpackPojo}s. */
@SupportedAnnotationTypes("io.camunda.zeebe.msgpack.pojo.MsgpackPojo")
public final class MsgpackPojoProcessor extends AbstractProcessor {

  private static final String DIRECT_BUFFER = "org.agrona.DirectBuffer";
  private static final String LIST = "java.util.List";

  private enum Type {
    LONG,
    INT,
    BOOLEAN,
    STRING,
    RAW,
    ENUM,
    STRING_LIST,
    POJO
  }

  private record Property(String key, Type type, String getter, String setter, String javaType) {}

  private record Model(String pkg, String name, List<Property> properties, String unknownGetter) {}

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(
      final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
    for (final Element element : roundEnv.getElementsAnnotatedWith(MsgpackPojo.class)) {
      if (element.getKind() != ElementKind.CLASS) {
        error(element, "@MsgpackPojo can only be applied to classes");
        continue;
      }
      try {
        final var model = buildModel((TypeElement) element);
        write(model.pkg(), model.name() + "Serializer", serializer(model), element);
        write(model.pkg(), model.name() + "Deserializer", deserializer(model), element);
      } catch (final IllegalArgumentException e) {
        error(element, e.getMessage());
      }
    }
    return true;
  }

  private Model buildModel(final TypeElement type) {
    final var pkg =
        processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
    final var properties = new ArrayList<Property>();
    String unknownGetter = null;
    for (final Element field : type.getEnclosedElements()) {
      if (field.getKind() != ElementKind.FIELD) {
        continue;
      }
      final var fieldName = field.getSimpleName().toString();
      if (field.getAnnotation(MsgpackUnknownProperties.class) != null) {
        unknownGetter = "get" + capitalize(fieldName);
        continue;
      }
      final var annotation = field.getAnnotation(MsgpackProperty.class);
      if (annotation == null) {
        continue;
      }
      final var mirror = field.asType();
      final var kind = typeOf(mirror, annotation.raw(), fieldName);
      final var getter = (kind == Type.BOOLEAN ? "is" : "get") + capitalize(fieldName);
      final var setter = "set" + capitalize(fieldName);
      properties.add(
          new Property(annotation.value(), kind, getter, setter, javaType(mirror, kind)));
    }
    if (properties.isEmpty()) {
      throw new IllegalArgumentException("@MsgpackPojo class has no @MsgpackProperty fields");
    }
    return new Model(pkg, type.getSimpleName().toString(), properties, unknownGetter);
  }

  private Type typeOf(final TypeMirror mirror, final boolean raw, final String field) {
    switch (mirror.getKind()) {
      case LONG:
        return Type.LONG;
      case INT:
        return Type.INT;
      case BOOLEAN:
        return Type.BOOLEAN;
      case DECLARED:
        final var declared = (DeclaredType) mirror;
        final var element = (TypeElement) declared.asElement();
        final var name = element.getQualifiedName().toString();
        if (name.equals(DIRECT_BUFFER)) {
          return raw ? Type.RAW : Type.STRING;
        }
        if (name.equals(LIST)
            && declared.getTypeArguments().size() == 1
            && declared.getTypeArguments().get(0).toString().equals(DIRECT_BUFFER)) {
          return Type.STRING_LIST;
        }
        if (element.getKind() == ElementKind.ENUM) {
          return Type.ENUM;
        }
        if (element.getAnnotation(MsgpackPojo.class) != null) {
          return Type.POJO;
        }
        break;
      default:
        break;
    }
    throw new IllegalArgumentException("Unsupported type " + mirror + " of field " + field);
  }

  private String javaType(final TypeMirror mirror, final Type kind) {
    return kind == Type.ENUM || kind == Type.POJO
        ? ((TypeElement) ((DeclaredType) mirror).asElement()).getQualifiedName().toString()
        : mirror.toString();
  }

  // ---------------------------------------------------------------- serializer

  private static String serializer(final Model m) {
    final var pojo = m.name();
    final var s = new StringBuilder();
    header(s, m, pojo + "Serializer");
    s.append("import io.camunda.zeebe.msgpack.pojo.Decoding;\n");
    s.append("import io.camunda.zeebe.msgpack.spec.MsgPackWriter;\n");
    s.append("import org.agrona.DirectBuffer;\nimport org.agrona.MutableDirectBuffer;\n");
    s.append("import org.agrona.concurrent.UnsafeBuffer;\n\n");
    s.append("/** Generated. Not thread-safe: use one instance per thread. */\n");
    s.append("public final class ").append(pojo).append("Serializer {\n");
    constants(s, m);
    s.append("  private final MsgPackWriter writer = new MsgPackWriter();\n");
    for (final var p : m.properties()) {
      if (p.type() == Type.POJO) {
        s.append("  private final ")
            .append(nested(p))
            .append("Serializer ")
            .append(field(p))
            .append("Serializer = new ")
            .append(nested(p))
            .append("Serializer();\n");
      }
    }
    s.append(
        "\n  /** Writes the value at {@code offset} and returns the number of bytes written. */\n");
    s.append("  public int serialize(final ")
        .append(pojo)
        .append(" value, final MutableDirectBuffer buffer, final int offset) {\n");
    s.append("    writer.wrap(buffer, offset);\n    return write(writer, value);\n  }\n\n");

    s.append("  public int write(final MsgPackWriter w, final ").append(pojo).append(" v) {\n");
    final var count =
        m.properties().size()
            + (m.unknownGetter() == null ? "" : " + v." + m.unknownGetter() + "().count()");
    s.append("    int n = w.writeMapHeader(").append(count).append(");\n");
    int i = 0;
    for (final var p : m.properties()) {
      s.append("    n += w.writeString(KEY_").append(i).append(");\n");
      final var get = "v." + p.getter() + "()";
      switch (p.type()) {
        case LONG, INT -> s.append("    n += w.writeInteger(").append(get).append(");\n");
        case BOOLEAN -> s.append("    n += w.writeBoolean(").append(get).append(");\n");
        case STRING -> s.append("    n += w.writeString(").append(get).append(");\n");
        case RAW ->
            s.append("    n += w.writeRaw(requireRaw(")
                .append(get)
                .append(", \"")
                .append(p.key())
                .append("\"));\n");
        case ENUM ->
            s.append("    n += w.writeString(ENUM_NAMES_")
                .append(i)
                .append("[requireEnum(")
                .append(get)
                .append(", \"")
                .append(p.key())
                .append("\").ordinal()]);\n");
        case STRING_LIST -> {
          s.append("    {\n      final var list = ").append(get).append(";\n");
          s.append("      n += w.writeArrayHeader(list.size());\n");
          s.append(
              "      for (int i = 0; i < list.size(); i++) {\n"
                  + "        n += w.writeString(list.get(i));\n"
                  + "      }\n"
                  + "    }\n");
        }
        case POJO ->
            s.append("    n += ")
                .append(field(p))
                .append("Serializer.write(w, ")
                .append(get)
                .append(");\n");
      }
      i++;
    }
    if (m.unknownGetter() != null) {
      s.append("    final var unknown = v.").append(m.unknownGetter()).append("();\n");
      s.append(
          "    if (!unknown.isEmpty()) {\n"
              + "      n += w.writeRaw(unknown.buffer(), 0, unknown.length());\n"
              + "    }\n");
    }
    s.append("    return n;\n  }\n\n");

    s.append("  public int getLength(final ").append(pojo).append(" v) {\n");
    final var lenCount = count;
    s.append("    int n = MsgPackWriter.getEncodedMapHeaderLength(")
        .append(lenCount)
        .append(");\n");
    i = 0;
    for (final var p : m.properties()) {
      s.append("    n += MsgPackWriter.getEncodedStringLength(KEY_")
          .append(i)
          .append(".capacity());\n");
      final var get = "v." + p.getter() + "()";
      switch (p.type()) {
        case LONG, INT ->
            s.append("    n += MsgPackWriter.getEncodedLongValueLength(")
                .append(get)
                .append(");\n");
        case BOOLEAN -> s.append("    n += MsgPackWriter.getEncodedBooleanValueLength();\n");
        case STRING ->
            s.append("    n += MsgPackWriter.getEncodedStringLength(")
                .append(get)
                .append(".capacity());\n");
        case RAW ->
            s.append("    n += requireRaw(")
                .append(get)
                .append(", \"")
                .append(p.key())
                .append("\").capacity();\n");
        case ENUM ->
            s.append("    n += MsgPackWriter.getEncodedStringLength(ENUM_NAMES_")
                .append(i)
                .append("[requireEnum(")
                .append(get)
                .append(", \"")
                .append(p.key())
                .append("\").ordinal()].capacity());\n");
        case STRING_LIST -> {
          s.append("    {\n      final var list = ").append(get).append(";\n");
          s.append("      n += MsgPackWriter.getEncodedArrayHeaderLength(list.size());\n");
          s.append(
              "      for (int i = 0; i < list.size(); i++) {\n"
                  + "        n += MsgPackWriter.getEncodedStringLength(list.get(i).capacity());\n"
                  + "      }\n"
                  + "    }\n");
        }
        case POJO ->
            s.append("    n += ")
                .append(field(p))
                .append("Serializer.getLength(")
                .append(get)
                .append(");\n");
      }
      i++;
    }
    if (m.unknownGetter() != null) {
      s.append("    n += v.").append(m.unknownGetter()).append("().length();\n");
    }
    s.append("    return n;\n  }\n\n");

    s.append(
        "  private static DirectBuffer requireRaw(final DirectBuffer value, final String key) {\n");
    s.append(
        "    if (value.capacity() == 0) {\n"
            + "      throw new IllegalStateException(\"Property '\" + key + \"' holds no encoded"
            + " value\");\n"
            + "    }\n"
            + "    return value;\n"
            + "  }\n\n");
    s.append("  private static <E> E requireEnum(final E value, final String key) {\n");
    s.append(
        "    if (value == null) {\n"
            + "      throw new IllegalStateException(\"Property '\" + key + \"' has no value\");\n"
            + "    }\n"
            + "    return value;\n"
            + "  }\n"
            + "}\n");
    return s.toString();
  }

  // -------------------------------------------------------------- deserializer

  private static String deserializer(final Model m) {
    final var pojo = m.name();
    final var s = new StringBuilder();
    header(s, m, pojo + "Deserializer");
    s.append("import io.camunda.zeebe.msgpack.pojo.Decoding;\n");
    s.append("import io.camunda.zeebe.msgpack.spec.MsgPackReader;\n");
    s.append("import org.agrona.DirectBuffer;\nimport org.agrona.concurrent.UnsafeBuffer;\n\n");
    s.append("/** Generated. Not thread-safe: use one instance per thread. */\n");
    s.append("public final class ").append(pojo).append("Deserializer {\n");
    constants(s, m);
    s.append("  private final MsgPackReader reader = new MsgPackReader();\n");
    s.append("  private final ")
        .append(pojo)
        .append(" defaults = new ")
        .append(pojo)
        .append("();\n");
    for (final var p : m.properties()) {
      if (p.type() == Type.POJO) {
        s.append("  private final ")
            .append(nested(p))
            .append("Deserializer ")
            .append(field(p))
            .append("Deserializer = new ")
            .append(nested(p))
            .append("Deserializer();\n");
      }
    }
    s.append(
        "\n"
            + "  /**\n"
            + "   * Fills {@code target}. Properties missing in the buffer keep their default,"
            + " strings and raw\n"
            + "   * values are copied so the buffer can be reused afterwards.\n"
            + "   */\n");
    s.append(
            "  public void deserialize(final DirectBuffer buffer, final int offset, final int"
                + " length, final ")
        .append(pojo)
        .append(" target) {\n");
    s.append("    reader.wrap(buffer, offset, length);\n    read(reader, target);\n  }\n\n");

    s.append("  public void reset(final ").append(pojo).append(" target) {\n");
    for (final var p : m.properties()) {
      switch (p.type()) {
        case STRING_LIST ->
            s.append("    target.")
                .append(p.getter())
                .append("().clear();\n    target.")
                .append(p.getter())
                .append("().addAll(defaults.")
                .append(p.getter())
                .append("());\n");
        case POJO ->
            s.append("    ")
                .append(field(p))
                .append("Deserializer.reset(target.")
                .append(p.getter())
                .append("());\n");
        default ->
            s.append("    target.")
                .append(p.setter())
                .append("(defaults.")
                .append(p.getter())
                .append("());\n");
      }
    }
    if (m.unknownGetter() != null) {
      s.append("    target.").append(m.unknownGetter()).append("().clear();\n");
    }
    s.append("  }\n\n");

    s.append("  public void read(final MsgPackReader r, final ")
        .append(pojo)
        .append(" target) {\n");
    s.append("    reset(target);\n    final int size = r.readMapHeader();\n");
    s.append("    for (int i = 0; i < size; i++) {\n");
    s.append("      final int entryStart = r.getOffset();\n");
    s.append(
        "      final int keyLength = r.readStringLength();\n"
            + "      final int keyOffset = r.getOffset();\n"
            + "      r.skipBytes(keyLength);\n");
    s.append("      switch (indexOf(r.getBuffer(), keyOffset, keyLength, i)) {\n");
    int i = 0;
    for (final var p : m.properties()) {
      s.append("        case ").append(i).append(" -> ");
      final var set = "target." + p.setter();
      switch (p.type()) {
        case LONG -> s.append(set).append("(r.readInteger());\n");
        case INT -> s.append(set).append("((int) r.readInteger());\n");
        case BOOLEAN -> s.append(set).append("(r.readBoolean());\n");
        case STRING -> s.append(set).append("(Decoding.readString(r));\n");
        case RAW -> s.append(set).append("(Decoding.readRaw(r));\n");
        case ENUM ->
            s.append(set)
                .append("(Decoding.readEnum(r, ENUM_NAMES_")
                .append(i)
                .append(", ENUM_VALUES_")
                .append(i)
                .append("));\n");
        case STRING_LIST -> {
          s.append(
                  "{\n"
                      + "          final int n = r.readArrayHeader();\n"
                      + "          final var list = target.")
              .append(p.getter())
              .append("();\n");
          s.append(
              "          for (int k = 0; k < n; k++) {\n"
                  + "            list.add(Decoding.readString(r));\n"
                  + "          }\n"
                  + "        }\n");
        }
        case POJO ->
            s.append(field(p))
                .append("Deserializer.read(r, target.")
                .append(p.getter())
                .append("());\n");
      }
      i++;
    }
    if (m.unknownGetter() != null) {
      s.append("        default -> {\n          r.skipValue();\n          target.")
          .append(m.unknownGetter())
          .append("().append(r.getBuffer(), entryStart, r.getOffset() - entryStart);\n        }\n");
    } else {
      s.append("        default -> r.skipValue();\n");
    }
    s.append("      }\n    }\n  }\n\n");

    s.append(
        "  /** Keys are written in declaration order, so the expected index is tried first. */\n");
    s.append(
        "  private static int indexOf(final DirectBuffer buffer, final int offset, final int"
            + " length, final int hint) {\n");
    s.append(
        "    final int n = KEYS.length;\n"
            + "    for (int k = 0; k < n; k++) {\n"
            + "      final int index = (hint + k) % n;\n");
    s.append(
        "      if (Decoding.keyEquals(KEYS[index], buffer, offset, length)) {\n"
            + "        return index;\n"
            + "      }\n"
            + "    }\n"
            + "    return -1;\n"
            + "  }\n"
            + "}\n");
    return s.toString();
  }

  // ------------------------------------------------------------------- helpers

  private static void constants(final StringBuilder s, final Model m) {
    int i = 0;
    for (final var p : m.properties()) {
      s.append("  private static final UnsafeBuffer KEY_")
          .append(i)
          .append(" = Decoding.encodedKey(\"")
          .append(p.key())
          .append("\");\n");
      i++;
    }
    s.append("  private static final UnsafeBuffer[] KEYS = {");
    for (int k = 0; k < i; k++) {
      s.append(k == 0 ? "" : ", ").append("KEY_").append(k);
    }
    s.append("};\n");
    i = 0;
    for (final var p : m.properties()) {
      if (p.type() == Type.ENUM) {
        s.append("  private static final ")
            .append(p.javaType())
            .append("[] ENUM_VALUES_")
            .append(i)
            .append(" = ")
            .append(p.javaType())
            .append(".values();\n");
        s.append("  private static final UnsafeBuffer[] ENUM_NAMES_")
            .append(i)
            .append(" = new UnsafeBuffer[ENUM_VALUES_")
            .append(i)
            .append(".length];\n");
        s.append("  static {\n    for (int i = 0; i < ENUM_VALUES_")
            .append(i)
            .append(".length; i++) {\n      ENUM_NAMES_")
            .append(i)
            .append("[i] = Decoding.encodedKey(ENUM_VALUES_")
            .append(i)
            .append("[i].toString());\n    }\n  }\n");
      }
      i++;
    }
  }

  private static void header(final StringBuilder s, final Model m, final String cls) {
    s.append(
            "/*\n"
                + " * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH"
                + " under\n")
        .append(" * one or more contributor license agreements. See the NOTICE file distributed\n")
        .append(" * with this work for additional information regarding copyright ownership.\n")
        .append(" * Licensed under the Camunda License 1.0. You may not use this file\n")
        .append(" * except in compliance with the Camunda License 1.0.\n */\n");
    s.append("// Generated by MsgpackPojoProcessor, do not edit.\n");
    s.append("package ").append(m.pkg()).append(";\n\n");
  }

  private static String nested(final Property p) {
    return p.javaType();
  }

  private static String field(final Property p) {
    return p.getter()
        .replaceFirst("^(get|is)", "")
        .transform(n -> Character.toLowerCase(n.charAt(0)) + n.substring(1));
  }

  private static String capitalize(final String name) {
    return Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }

  private void write(
      final String pkg, final String cls, final String source, final Element origin) {
    try (final Writer writer =
        processingEnv.getFiler().createSourceFile(pkg + "." + cls, origin).openWriter()) {
      writer.write(source);
    } catch (final IOException e) {
      error(origin, "Could not write " + cls + ": " + e.getMessage());
    }
  }

  private void error(final Element element, final String message) {
    processingEnv.getMessager().printMessage(Kind.ERROR, message, element);
  }
}
