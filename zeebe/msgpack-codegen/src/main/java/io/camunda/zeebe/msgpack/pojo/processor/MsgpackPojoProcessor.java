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

/**
 * Generates for every {@link MsgpackPojo} a stateless {@code <Pojo>Codec} and a {@code
 * <Pojo>Resetter}.
 */
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
    BINARY,
    ENUM,
    STRING_LIST,
    NESTED
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
        requireRecyclable((TypeElement) element);
        final var model = buildModel((TypeElement) element);
        write(model.pkg(), model.name() + "Codec", codec(model), element);
        write(model.pkg(), model.name() + "Resetter", resetter(model), element);
      } catch (final IllegalArgumentException e) {
        error(element, e.getMessage());
      }
    }
    return true;
  }

  private void requireRecyclable(final TypeElement type) {
    final var recyclable =
        processingEnv.getElementUtils().getTypeElement("io.camunda.zeebe.msgpack.Recyclable");
    if (recyclable == null
        || !processingEnv.getTypeUtils().isAssignable(type.asType(), recyclable.asType())) {
      throw new IllegalArgumentException(
          "@MsgpackPojo class must implement io.camunda.zeebe.msgpack.Recyclable, whose reset() "
              + "restores the default of every property (extend MsgpackBufferValue)");
    }
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
      final var binary = annotation != null && annotation.binary();
      final var key =
          annotation == null || annotation.value().isEmpty() ? fieldName : annotation.value();
      final var mirror = field.asType();
      final var kind = typeOf(mirror, raw, binary, fieldName);
      final var isFinal = field.getModifiers().contains(Modifier.FINAL);
      final var mutable = kind != Type.NESTED && kind != Type.STRING_LIST;
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

  private Type typeOf(
      final TypeMirror mirror, final boolean raw, final boolean binary, final String field) {
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
          return raw ? Type.RAW : binary ? Type.BINARY : Type.STRING;
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
          return Type.NESTED;
        }
        if (isBaseValue(element)) {
          return Type.NESTED;
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
    return kind == Type.ENUM || kind == Type.NESTED
        ? ((TypeElement) ((DeclaredType) mirror).asElement()).getQualifiedName().toString()
        : mirror.toString();
  }

  // --------------------------------------------------------------------- codec

  private static String resetter(final Model m) {
    final var pojo = m.name();
    final var s = new StringBuilder();
    s.append(
        """
        /*
         * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
         * one or more contributor license agreements. See the NOTICE file distributed
         * with this work for additional information regarding copyright ownership.
         * Licensed under the Camunda License 1.0. You may not use this file
         * except in compliance with the Camunda License 1.0.
         */
        // Generated by MsgpackPojoProcessor, do not edit.
        """);
    s.append("package %s;\n\n".formatted(m.pkg()));
    s.append("import io.camunda.zeebe.msgpack.pojo.MsgpackResetter;\n\n");
    s.append(
        "/** Generated and stateless. Restores the field initializers of {@link %s}. */\n"
            .formatted(pojo));
    s.append(
        "public final class %1$sResetter implements MsgpackResetter<%1$s> {\n".formatted(pojo));
    s.append(
        "  public static final %1$sResetter INSTANCE = new %1$sResetter();\n\n".formatted(pojo));
    s.append("  private static final %s DEFAULTS = new %s();\n\n".formatted(pojo, pojo));
    s.append("  private %sResetter() {}\n\n".formatted(pojo));
    s.append("  @Override\n  public void reset(final %s target) {\n".formatted(pojo));
    for (final var p : m.properties()) {
      final var f = p.field();
      switch (p.type()) {
        case STRING_LIST ->
            s.append(
                "    target.%1$s.clear();\n    target.%1$s.addAll(DEFAULTS.%1$s);\n".formatted(f));
        case NESTED -> s.append("    target.%s.reset();\n".formatted(f));
        default -> s.append("    target.%1$s = DEFAULTS.%1$s;\n".formatted(f));
      }
    }
    if (m.unknownField() != null) {
      s.append("    target.%s.clear();\n".formatted(m.unknownField()));
    }
    s.append("  }\n}\n");
    return s.toString();
  }

  private static String codec(final Model m) {
    final var pojo = m.name();
    final var s = new StringBuilder();
    s.append(
        """
        /*
         * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
         * one or more contributor license agreements. See the NOTICE file distributed
         * with this work for additional information regarding copyright ownership.
         * Licensed under the Camunda License 1.0. You may not use this file
         * except in compliance with the Camunda License 1.0.
         */
        // Generated by MsgpackPojoProcessor, do not edit.
        """);
    s.append("package ").append(m.pkg()).append(";\n\n");
    s.append(
        """
        import io.camunda.zeebe.msgpack.pojo.Codecs;
        import io.camunda.zeebe.msgpack.pojo.Decoding;
        import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
        import io.camunda.zeebe.msgpack.spec.MsgPackReader;
        import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
        import org.agrona.DirectBuffer;
        import org.agrona.concurrent.UnsafeBuffer;

        """);
    s.append("/** Generated, stateless and thread-safe. */\n");
    s.append("public final class %sCodec implements MsgpackCodec<%s> {\n".formatted(pojo, pojo));
    s.append("  public static final %sCodec INSTANCE = new %sCodec();\n\n".formatted(pojo, pojo));
    constants(s, m);
    s.append("\n  private %sCodec() {}\n\n".formatted(pojo));

    final var unknown = m.unknownField();
    final var count =
        m.properties().size() + (unknown == null ? "" : " + v." + unknown + ".count()");

    // write
    s.append(
        "  @Override\n  public int write(final MsgPackWriter w, final %s v) {\n".formatted(pojo));
    s.append("    int n = w.writeMapHeader(%s);\n".formatted(count));
    int i = 0;
    for (final var p : m.properties()) {
      final var get = "v." + p.field();
      s.append("    n += w.writeString(KEY_%d);\n".formatted(i));
      switch (p.type()) {
        case LONG, INT -> s.append("    n += w.writeInteger(%s);\n".formatted(get));
        case BOOLEAN -> s.append("    n += w.writeBoolean(%s);\n".formatted(get));
        case STRING -> s.append("    n += w.writeString(%s);\n".formatted(get));
        case BINARY -> s.append("    n += w.writeBinary(%s);\n".formatted(get));
        case RAW ->
            s.append("    n += w.writeRaw(requireRaw(%s, \"%s\"));\n".formatted(get, p.key()));
        case ENUM ->
            s.append(
                "    n += w.writeString(ENUM_NAMES_%d[requireEnum(%s, \"%s\").ordinal()]);\n"
                    .formatted(i, get, p.key()));
        case STRING_LIST ->
            s.append(
                "    n += w.writeArrayHeader(%1$s.size());\n".formatted(get)
                    + "    for (int i = 0; i < %1$s.size(); i++) {\n".formatted(get)
                    + "      n += w.writeString(%s.get(i));\n".formatted(get)
                    + "    }\n");
        case NESTED -> s.append("    n += CODEC_%d.write(w, %s);\n".formatted(i, get));
      }
      i++;
    }
    if (unknown != null) {
      s.append(
          "    if (!v.%1$s.isEmpty()) {\n      n += w.writeRaw(v.%1$s.buffer(), 0, v.%1$s.length());\n    }\n"
              .formatted(unknown));
    }
    s.append("    return n;\n  }\n\n");

    // length
    s.append("  @Override\n  public int getLength(final %s v) {\n".formatted(pojo));
    s.append("    int n = MsgPackWriter.getEncodedMapHeaderLength(%s);\n".formatted(count));
    i = 0;
    for (final var p : m.properties()) {
      final var get = "v." + p.field();
      s.append("    n += MsgPackWriter.getEncodedStringLength(KEY_%d.capacity());\n".formatted(i));
      switch (p.type()) {
        case LONG, INT ->
            s.append("    n += MsgPackWriter.getEncodedLongValueLength(%s);\n".formatted(get));
        case BOOLEAN -> s.append("    n += MsgPackWriter.getEncodedBooleanValueLength();\n");
        case STRING ->
            s.append(
                "    n += MsgPackWriter.getEncodedStringLength(%s.capacity());\n".formatted(get));
        case BINARY ->
            s.append(
                "    n += MsgPackWriter.getEncodedBinaryValueLength(%s.capacity());\n"
                    .formatted(get));
        case RAW ->
            s.append("    n += requireRaw(%s, \"%s\").capacity();\n".formatted(get, p.key()));
        case ENUM ->
            s.append(
                "    n += MsgPackWriter.getEncodedStringLength(ENUM_NAMES_%d[requireEnum(%s, \"%s\").ordinal()].capacity());\n"
                    .formatted(i, get, p.key()));
        case STRING_LIST ->
            s.append(
                "    n += MsgPackWriter.getEncodedArrayHeaderLength(%s.size());\n".formatted(get)
                    + "    for (int i = 0; i < %s.size(); i++) {\n".formatted(get)
                    + "      n += MsgPackWriter.getEncodedStringLength(%s.get(i).capacity());\n"
                        .formatted(get)
                    + "    }\n");
        case NESTED -> s.append("    n += CODEC_%d.getLength(%s);\n".formatted(i, get));
      }
      i++;
    }
    if (unknown != null) {
      s.append("    n += v.%s.length();\n".formatted(unknown));
    }
    s.append("    return n;\n  }\n\n");

    // read
    s.append(
        "  @Override\n  public void read(final MsgPackReader r, final %s target) {\n"
            .formatted(pojo));
    s.append(
        "    final int size = r.readMapHeader();\n"
            + "    for (int i = 0; i < size; i++) {\n"
            + "      final int entryStart = r.getOffset();\n"
            + "      final int keyLength = r.readStringLength();\n"
            + "      final int keyOffset = r.getOffset();\n"
            + "      r.skipBytes(keyLength);\n"
            + "      switch (indexOf(r.getBuffer(), keyOffset, keyLength, i)) {\n");
    i = 0;
    for (final var p : m.properties()) {
      final var f = "target." + p.field();
      final var line =
          switch (p.type()) {
            case LONG -> f + " = r.readInteger();";
            case INT -> f + " = (int) r.readInteger();";
            case BOOLEAN -> f + " = r.readBoolean();";
            case STRING -> f + " = Decoding.readString(r);";
            case RAW -> f + " = Decoding.readRaw(r);";
            case BINARY -> f + " = Decoding.readBinary(r);";
            case ENUM ->
                "%s = Decoding.readEnum(r, ENUM_NAMES_%d, ENUM_VALUES_%d);".formatted(f, i, i);
            case STRING_LIST ->
                "{\n          final int n = r.readArrayHeader();\n          for (int k = 0; k < n; k++) {\n            %s.add(Decoding.readString(r));\n          }\n        }"
                    .formatted(f);
            case NESTED -> "CODEC_%d.read(r, %s);".formatted(i, f);
          };
      s.append("        case %d -> %s\n".formatted(i, line));
      i++;
    }
    if (unknown != null) {
      s.append(
          "        default -> {\n          r.skipValue();\n          target.%s.append(r.getBuffer(), entryStart, r.getOffset() - entryStart);\n        }\n"
              .formatted(unknown));
    } else {
      s.append("        default -> r.skipValue();\n");
    }
    s.append("      }\n    }\n  }\n\n");

    s.append(
        """
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

          private static <E> E requireEnum(final E value, final String key) {
            if (value == null) {
              throw new IllegalStateException("Property '" + key + "' has no value");
            }
            return value;
          }
        }
        """);
    return s.toString();
  }

  private static void constants(final StringBuilder s, final Model m) {
    int i = 0;
    for (final var p : m.properties()) {
      s.append(
          "  private static final UnsafeBuffer KEY_%d = Decoding.encodedKey(\"%s\");\n"
              .formatted(i, p.key()));
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
        s.append(
            """
              private static final %1$s[] ENUM_VALUES_%2$d = %1$s.values();
              private static final UnsafeBuffer[] ENUM_NAMES_%2$d =
                  new UnsafeBuffer[ENUM_VALUES_%2$d.length];

              static {
                for (int i = 0; i < ENUM_VALUES_%2$d.length; i++) {
                  ENUM_NAMES_%2$d[i] = Decoding.encodedKey(ENUM_VALUES_%2$d[i].toString());
                }
              }
            """
                .formatted(p.javaType(), i));
      } else if (p.type() == Type.NESTED) {
        s.append(
            "  private static final MsgpackCodec<%1$s> CODEC_%2$d = Codecs.of(%1$s.class);\n"
                .formatted(p.javaType(), i));
      }
      i++;
    }
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
