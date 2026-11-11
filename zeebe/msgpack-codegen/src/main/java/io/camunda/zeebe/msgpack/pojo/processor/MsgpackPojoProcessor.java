/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import io.camunda.zeebe.msgpack.pojo.MsgpackIgnore;
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
import javax.lang.model.element.Modifier;
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
    POJO,
    VALUE
  }

  private record Property(String key, Type type, String field, String javaType, boolean isFinal) {}

  private record Model(String pkg, String name, List<Property> properties, String unknownField) {}

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
    String unknownField = null;
    for (final Element field : type.getEnclosedElements()) {
      if (field.getKind() != ElementKind.FIELD
          || field.getModifiers().contains(Modifier.STATIC)
          || field.getAnnotation(MsgpackIgnore.class) != null) {
        continue;
      }
      final var fieldName = field.getSimpleName().toString();
      if (field.getModifiers().contains(Modifier.PRIVATE)) {
        throw new IllegalArgumentException(
            "Field " + fieldName + " must not be private, or annotated with @MsgpackIgnore");
      }
      if (field.getAnnotation(MsgpackUnknownProperties.class) != null) {
        unknownField = fieldName;
        continue;
      }
      final var annotation = field.getAnnotation(MsgpackProperty.class);
      final var raw = annotation != null && annotation.raw();
      final var key =
          annotation == null || annotation.value().isEmpty() ? fieldName : annotation.value();
      final var mirror = field.asType();
      final var kind = typeOf(mirror, raw, fieldName);
      final var isFinal = field.getModifiers().contains(Modifier.FINAL);
      final var mutable = kind != Type.POJO && kind != Type.VALUE && kind != Type.STRING_LIST;
      if (isFinal && mutable) {
        throw new IllegalArgumentException("Field " + fieldName + " must not be final");
      }
      properties.add(new Property(key, kind, fieldName, javaType(mirror, kind), isFinal));
    }
    if (properties.isEmpty()) {
      throw new IllegalArgumentException("@MsgpackPojo class has no @MsgpackProperty fields");
    }
    return new Model(pkg, type.getSimpleName().toString(), properties, unknownField);
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
        if (isBaseValue(element)) {
          return Type.VALUE;
        }
        break;
      default:
        break;
    }
    throw new IllegalArgumentException("Unsupported type " + mirror + " of field " + field);
  }

  private boolean isBaseValue(final TypeElement element) {
    TypeMirror current = element.asType();
    while (current.getKind() == javax.lang.model.type.TypeKind.DECLARED) {
      final var currentElement = (TypeElement) ((DeclaredType) current).asElement();
      if (currentElement
          .getQualifiedName()
          .contentEquals("io.camunda.zeebe.msgpack.value.BaseValue")) {
        return true;
      }
      current = currentElement.getSuperclass();
    }
    return false;
  }

  private String javaType(final TypeMirror mirror, final Type kind) {
    return kind == Type.ENUM || kind == Type.POJO || kind == Type.VALUE
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
            + (m.unknownField() == null ? "" : " + v." + m.unknownField() + ".count()");
    s.append("    int n = w.writeMapHeader(").append(count).append(");\n");
    int i = 0;
    for (final var p : m.properties()) {
      s.append("    n += w.writeString(KEY_").append(i).append(");\n");
      final var get = "v." + p.field();
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
        case VALUE -> s.append("    n += ").append(get).append(".write(w);\n");
      }
      i++;
    }
    if (m.unknownField() != null) {
      s.append("    final var unknown = v.").append(m.unknownField()).append(";\n");
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
      final var get = "v." + p.field();
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
        case VALUE -> s.append("    n += ").append(get).append(".getEncodedLength();\n");
      }
      i++;
    }
    if (m.unknownField() != null) {
      s.append("    n += v.").append(m.unknownField()).append(".length();\n");
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
      final var f = p.field();
      switch (p.type()) {
        case STRING_LIST ->
            s.append(
                "    target.%s.clear();\n    target.%s.addAll(defaults.%s);\n".formatted(f, f, f));
        case POJO -> s.append("    %sDeserializer.reset(target.%s);\n".formatted(f, f));
        case VALUE -> s.append("    target.%s.reset();\n".formatted(f));
        default -> s.append("    target.%s = defaults.%s;\n".formatted(f, f));
      }
    }
    if (m.unknownField() != null) {
      s.append("    target.").append(m.unknownField()).append(".clear();\n");
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
      final var set = "target." + p.field() + " = ";
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
              "{\n          final int n = r.readArrayHeader();\n          final var list = target.%s;\n"
                  .formatted(p.field()));
          s.append(
              "          for (int k = 0; k < n; k++) {\n"
                  + "            list.add(Decoding.readString(r));\n"
                  + "          }\n"
                  + "        }\n");
        }
        case POJO ->
            s.append("%sDeserializer.read(r, target.%s);\n".formatted(p.field(), p.field()));
        case VALUE -> s.append("target.%s.read(r);\n".formatted(p.field()));
      }
      i++;
    }
    if (m.unknownField() != null) {
      s.append(
          "        default -> {\n          r.skipValue();\n          target.%s.append(r.getBuffer(), entryStart, r.getOffset() - entryStart);\n        }\n"
              .formatted(m.unknownField()));
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
    return p.field();
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
