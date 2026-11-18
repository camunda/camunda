/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import io.camunda.zeebe.msgpack.pojo.annotation.Binary;
import io.camunda.zeebe.msgpack.pojo.annotation.Ignore;
import io.camunda.zeebe.msgpack.pojo.annotation.Key;
import io.camunda.zeebe.msgpack.pojo.annotation.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.annotation.Raw;
import java.util.ArrayList;
import java.util.HashSet;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

/** Validates a {@link MsgpackPojo} class and describes its stored properties as a {@link Model}. */
final class ModelBuilder {

  private static final String DIRECT_BUFFER = "org.agrona.DirectBuffer";
  private static final String UNSAFE_BUFFER = "org.agrona.concurrent.UnsafeBuffer";
  private static final String BUFFER_VALUE = "io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue";
  private static final String LIST = "java.util.List";
  private static final String UNKNOWN_PROPERTIES =
      "io.camunda.zeebe.msgpack.pojo.UnknownProperties";

  private final ProcessingEnvironment processingEnv;

  ModelBuilder(final ProcessingEnvironment processingEnv) {
    this.processingEnv = processingEnv;
  }

  /**
   * Validates the class and describes its stored properties. Throws an {@link
   * IllegalArgumentException} with the message of the compile error.
   */
  Model build(final TypeElement type) {
    requireTopLevel(type);
    requireRecyclable(type);
    requireNoInheritedFields(type);
    return buildModel(type);
  }

  /**
   * The generated classes are named after the simple name, and {@code Codecs} finds them by the
   * binary name, which only agree for top-level classes.
   */
  private static void requireTopLevel(final TypeElement type) {
    if (type.getNestingKind() != NestingKind.TOP_LEVEL) {
      throw new IllegalArgumentException("@MsgpackPojo class must be a top-level class");
    }
  }

  /** Only the fields of the annotated class are stored, so a superclass must not declare any. */
  private static void requireNoInheritedFields(final TypeElement type) {
    TypeMirror current = type.getSuperclass();
    while (current.getKind() == TypeKind.DECLARED) {
      final var superclass = (TypeElement) ((DeclaredType) current).asElement();
      final var name = superclass.getQualifiedName().toString();
      if (name.equals(BUFFER_VALUE) || name.startsWith("java.")) {
        break;
      }
      for (final Element field : superclass.getEnclosedElements()) {
        if (field.getKind() == ElementKind.FIELD
            && !field.getModifiers().contains(Modifier.STATIC)
            && field.getAnnotation(Ignore.class) == null) {
          throw new IllegalArgumentException(
              "Field %s of superclass %s would not be stored: declare it in the @MsgpackPojo class or annotate it with @Ignore"
                  .formatted(field.getSimpleName(), name));
        }
      }
      current = superclass.getSuperclass();
    }
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
    final var keys = new HashSet<String>();
    final var unknownProperties = type.getAnnotation(MsgpackPojo.class).unknownProperties();
    String unknownField = null;
    for (final Element field : type.getEnclosedElements()) {
      if (field.getKind() != ElementKind.FIELD
          || field.getModifiers().contains(Modifier.STATIC)
          || field.getAnnotation(Ignore.class) != null) {
        continue;
      }
      final var fieldName = field.getSimpleName().toString();
      if (field.getModifiers().contains(Modifier.PRIVATE)) {
        throw new IllegalArgumentException(
            "Field " + fieldName + " must not be private, or annotated with @Ignore");
      }
      if (isType(field.asType(), UNKNOWN_PROPERTIES)) {
        if (!unknownProperties) {
          throw new IllegalArgumentException(
              "Field %s: a @MsgpackPojo(unknownProperties = false) class must not have an UnknownProperties field"
                  .formatted(fieldName));
        }
        if (unknownField != null) {
          throw new IllegalArgumentException(
              "Fields %s and %s are both UnknownProperties fields"
                  .formatted(unknownField, fieldName));
        }
        if (!field.getModifiers().contains(Modifier.FINAL)) {
          throw new IllegalArgumentException(
              "UnknownProperties field %s must be final".formatted(fieldName));
        }
        unknownField = fieldName;
        continue;
      }
      final var raw = field.getAnnotation(Raw.class) != null;
      final var binary = field.getAnnotation(Binary.class) != null;
      final var keyAnnotation = field.getAnnotation(Key.class);
      final var key = keyAnnotation == null ? fieldName : keyAnnotation.value();
      if (key.isEmpty()) {
        throw new IllegalArgumentException("Field %s: @Key must not be empty".formatted(fieldName));
      }
      if (!keys.add(key)) {
        throw new IllegalArgumentException(
            "Field " + fieldName + " uses the key '" + key + "' of another field");
      }
      final var mirror = field.asType();
      final var kind = typeOf(mirror, raw, binary, fieldName);
      final var isFinal = field.getModifiers().contains(Modifier.FINAL);
      if (!isFinal && kind.container) {
        throw new IllegalArgumentException(
            "Field %s must be final: it is read into and reset in place".formatted(fieldName));
      }
      final var inPlace = isUnsafeBuffer(mirror);
      if (!isFinal && inPlace) {
        throw new IllegalArgumentException(
            ("Field %s must be final: an UnsafeBuffer is wrapped in place, which would modify the "
                    + "shared default. Declare it as DirectBuffer to replace it on every read")
                .formatted(fieldName));
      }
      final var mutable = !kind.container && !inPlace;
      if (isFinal && mutable) {
        throw new IllegalArgumentException("Field " + fieldName + " must not be final");
      }
      final var keyLiteral = processingEnv.getElementUtils().getConstantExpression(key);
      final var element = kind == Type.LIST ? elementOf(mirror) : null;
      properties.add(
          new Property(keyLiteral, kind, fieldName, javaType(mirror, kind), inPlace, element));
    }
    if (properties.isEmpty()) {
      throw new IllegalArgumentException("@MsgpackPojo class has no stored fields");
    }
    if (unknownProperties && unknownField == null) {
      throw new IllegalArgumentException(
          "@MsgpackPojo class needs a final UnknownProperties field: it keeps the keys of a newer "
              + "version, so that writing the value back does not drop them. Use "
              + "@MsgpackPojo(unknownProperties = false) to skip them instead");
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
        if (name.equals(DIRECT_BUFFER) || name.equals(UNSAFE_BUFFER)) {
          return raw ? Type.RAW : binary ? Type.BINARY : Type.STRING;
        }
        if (name.equals(LIST) && elementOf(mirror) != null) {
          return Type.LIST;
        }
        if (element.getKind() == ElementKind.ENUM) {
          requireNoToString(element, field);
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

  /**
   * Enums are written by {@code toString()} and read by {@code name()}, like {@code EnumValue}, so
   * an enum that overrides {@code toString()} could not be read back.
   */
  private static void requireNoToString(final TypeElement enumType, final String field) {
    final boolean overridden =
        enumType.getEnclosedElements().stream()
            .anyMatch(
                e ->
                    e.getKind() == ElementKind.METHOD
                        && e.getSimpleName().contentEquals("toString")
                        && ((ExecutableElement) e).getParameters().isEmpty());
    if (overridden) {
      throw new IllegalArgumentException(
          "Enum %s of field %s overrides toString(), but it is stored by toString() and read by name()"
              .formatted(enumType.getQualifiedName(), field));
    }
  }

  /**
   * The element of a {@code List<E>}, with {@code E} a {@code DirectBuffer}, a {@code Long}, an
   * {@code Integer} or such a list again; null for any other type.
   */
  private @Nullable ListElement elementOf(final TypeMirror list) {
    final var arguments = ((DeclaredType) list).getTypeArguments();
    if (arguments.size() != 1) {
      return null;
    }
    // compares the erased element, so type annotations like @Nullable do not matter
    final var argument = arguments.get(0);
    if (isType(argument, DIRECT_BUFFER)) {
      return new ListElement(ListElement.Kind.STRING, DIRECT_BUFFER, null);
    }
    if (isType(argument, "java.lang.Long")) {
      return new ListElement(ListElement.Kind.LONG, "java.lang.Long", null);
    }
    if (isType(argument, "java.lang.Integer")) {
      return new ListElement(ListElement.Kind.INT, "java.lang.Integer", null);
    }
    if (argument.getKind() == TypeKind.DECLARED && isType(argument, LIST)) {
      final var inner = elementOf(argument);
      return inner == null
          ? null
          : new ListElement(ListElement.Kind.LIST, LIST + "<" + inner.javaType() + ">", inner);
    }
    return null;
  }

  private boolean isType(final TypeMirror mirror, final String qualifiedName) {
    final var expected = processingEnv.getElementUtils().getTypeElement(qualifiedName);
    final var types = processingEnv.getTypeUtils();
    return expected != null
        && types.isSameType(types.erasure(mirror), types.erasure(expected.asType()));
  }

  private boolean isUnsafeBuffer(final TypeMirror mirror) {
    return mirror.getKind() == TypeKind.DECLARED && isType(mirror, UNSAFE_BUFFER);
  }

  private boolean isBaseValue(final TypeElement element) {
    TypeMirror current = element.asType();
    while (current.getKind() == TypeKind.DECLARED) {
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
}
