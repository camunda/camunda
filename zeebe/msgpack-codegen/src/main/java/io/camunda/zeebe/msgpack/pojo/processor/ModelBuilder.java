/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.processor;

import io.camunda.zeebe.msgpack.pojo.annotation.Binary;
import io.camunda.zeebe.msgpack.pojo.annotation.Document;
import io.camunda.zeebe.msgpack.pojo.annotation.Ignore;
import io.camunda.zeebe.msgpack.pojo.annotation.Key;
import io.camunda.zeebe.msgpack.pojo.annotation.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.annotation.Raw;
import io.camunda.zeebe.msgpack.pojo.annotation.Required;
import io.camunda.zeebe.msgpack.pojo.annotation.Sanitized;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import javax.tools.Diagnostic.Kind;
import org.jspecify.annotations.Nullable;

/** Validates a {@link MsgpackPojo} class and describes its stored properties as a {@link Model}. */
final class ModelBuilder {

  private static final String DIRECT_BUFFER = "org.agrona.DirectBuffer";
  private static final String UNSAFE_BUFFER = "org.agrona.concurrent.UnsafeBuffer";
  private static final String BUFFER_VALUE = "io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue";
  private static final String LIST = "java.util.List";
  private static final String INT_ARRAY_LIST = "org.agrona.collections.IntArrayList";
  private static final String LONG_ARRAY_LIST = "org.agrona.collections.LongArrayList";
  private static final String REQUIRED_LONG = "io.camunda.zeebe.msgpack.pojo.RequiredLong";
  private static final String REQUIRED_INT = "io.camunda.zeebe.msgpack.pojo.RequiredInt";
  private static final String NULLABLE = "org.jspecify.annotations.Nullable";
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
    requireNoCycle(type);
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

  /**
   * A generated codec resolves the codecs of its nested objects while its class is initialized, so
   * two classes that contain each other would wait for each other when first used by two threads.
   */
  private void requireNoCycle(final TypeElement root) {
    final var path = new ArrayList<TypeElement>();
    path.add(root);
    findCycle(root, root, new HashSet<>(), path);
  }

  private void findCycle(
      final TypeElement root,
      final TypeElement current,
      final Set<TypeElement> visited,
      final List<TypeElement> path) {
    for (final var next : nestedPojos(current)) {
      if (next.equals(root)) {
        throw new IllegalArgumentException(
            "@MsgpackPojo classes must not contain themselves, directly or through other classes: "
                + path.stream()
                    .map(t -> t.getSimpleName().toString())
                    .collect(Collectors.joining(" -> "))
                + " -> "
                + root.getSimpleName());
      }
      if (visited.add(next)) {
        path.add(next);
        findCycle(root, next, visited, path);
        path.remove(path.size() - 1);
      }
    }
  }

  /** The {@code @MsgpackPojo} classes that the stored fields hold, also as list elements. */
  private static Set<TypeElement> nestedPojos(final TypeElement type) {
    final var pojos = new LinkedHashSet<TypeElement>();
    for (final Element field : type.getEnclosedElements()) {
      if (field.getKind() == ElementKind.FIELD
          && !field.getModifiers().contains(Modifier.STATIC)
          && field.getAnnotation(Ignore.class) == null) {
        collectPojos(field.asType(), pojos);
      }
    }
    return pojos;
  }

  private static void collectPojos(final TypeMirror mirror, final Set<TypeElement> pojos) {
    if (mirror.getKind() != TypeKind.DECLARED) {
      return;
    }
    final var declared = (DeclaredType) mirror;
    final var element = (TypeElement) declared.asElement();
    if (element.getAnnotation(MsgpackPojo.class) != null) {
      pojos.add(element);
    }
    declared.getTypeArguments().forEach(argument -> collectPojos(argument, pojos));
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
      final var document = field.getAnnotation(Document.class) != null;
      if (document && (raw || binary)) {
        throw new IllegalArgumentException(
            "Field %s: document cannot be combined with raw or binary".formatted(fieldName));
      }
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
      final var kind = typeOf(mirror, raw, binary, document, fieldName);
      if (document && kind != Type.DOCUMENT) {
        throw new IllegalArgumentException(
            "Field %s: document requires a DirectBuffer or UnsafeBuffer field"
                .formatted(fieldName));
      }
      final var nullable = isNullable(field);
      if (nullable && !kind.nullable) {
        throw new IllegalArgumentException(
            ("Field %s must not be @Nullable: only a string, binary, raw, document, enum, Boolean, "
                    + "Long or Integer field can be unset. Use Long, Integer, RequiredLong or "
                    + "RequiredInt for a long or int")
                .formatted(fieldName));
      }
      if (!nullable && (kind == Type.BOXED_LONG || kind == Type.BOXED_INT)) {
        throw new IllegalArgumentException(
            "Field %s must be @Nullable, null is its unset state. Use %s for a default"
                .formatted(fieldName, kind == Type.BOXED_LONG ? "long" : "int"));
      }
      if (!nullable && kind == Type.BOXED_BOOLEAN) {
        throw new IllegalArgumentException(
            "Field %s must be @Nullable, null is its unset state. Use boolean for a default"
                .formatted(fieldName));
      }
      final var inPlace = isUnsafeBuffer(mirror);
      final var sentinel = field.getAnnotation(Required.class) != null;
      final var bufferKind =
          kind == Type.STRING || kind == Type.RAW || kind == Type.BINARY || kind == Type.DOCUMENT;
      if (sentinel && kind != Type.LONG && kind != Type.INT && !(inPlace && bufferKind)) {
        throw new IllegalArgumentException(
            ("Field %s: @Required only applies to a long or int, or to a final UnsafeBuffer, whose "
                    + "initializer is the sentinel for unset. Use a @Nullable field for the other "
                    + "types")
                .formatted(fieldName));
      }
      final var isFinal = field.getModifiers().contains(Modifier.FINAL);
      if (!isFinal && kind.container) {
        throw new IllegalArgumentException(
            "Field %s must be final: it is read into and reset in place".formatted(fieldName));
      }
      if (nullable && inPlace) {
        throw new IllegalArgumentException(
            "Field %s must not be @Nullable: an UnsafeBuffer is final and never null"
                .formatted(fieldName));
      }
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
      final var sanitized = field.getAnnotation(Sanitized.class) != null;
      final var element = kind == Type.LIST ? elementOf(mirror, fieldName) : null;
      if (element != null && !element.unboxed()) {
        switch (element.kind()) {
          case INT -> warning(field, boxedListWarning(fieldName, "IntArrayList"));
          case LONG -> warning(field, boxedListWarning(fieldName, "LongArrayList"));
          default -> {}
        }
      }
      final var javaType = element != null ? element.leaf().javaType() : javaType(mirror, kind);
      final var pojo =
          kind == Type.NESTED
              && ((DeclaredType) mirror).asElement().getAnnotation(MsgpackPojo.class) != null;
      final var presence =
          sentinel
              ? Presence.SENTINEL
              : nullable
                  ? Presence.NULLABLE
                  : kind == Type.REQUIRED_LONG || kind == Type.REQUIRED_INT
                      ? Presence.HOLDER
                      : Presence.DEFAULT;
      properties.add(
          new Property(
              keyLiteral, kind, fieldName, javaType, inPlace, presence, sanitized, pojo, element));
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
      final TypeMirror mirror,
      final boolean raw,
      final boolean binary,
      final boolean document,
      final String field) {
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
          return raw ? Type.RAW : binary ? Type.BINARY : document ? Type.DOCUMENT : Type.STRING;
        }
        if (name.equals(REQUIRED_LONG)) {
          return Type.REQUIRED_LONG;
        }
        if (name.equals(REQUIRED_INT)) {
          return Type.REQUIRED_INT;
        }
        if (name.equals("java.lang.Boolean")) {
          return Type.BOXED_BOOLEAN;
        }
        if (name.equals("java.lang.Long")) {
          return Type.BOXED_LONG;
        }
        if (name.equals("java.lang.Integer")) {
          return Type.BOXED_INT;
        }
        if (name.equals(INT_ARRAY_LIST)
            || name.equals(LONG_ARRAY_LIST)
            || name.equals(LIST) && elementOf(mirror, field) != null) {
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
   * The element of an agrona {@code IntArrayList} or {@code LongArrayList}, or of a {@code List<E>}
   * with {@code E} a {@code DirectBuffer}, a {@code Long}, an {@code Integer}, a {@link
   * MsgpackPojo}, a {@code BaseValue} or such a list again; null for any other type.
   */
  private @Nullable ListElement elementOf(final TypeMirror list, final String field) {
    if (isType(list, INT_ARRAY_LIST)) {
      return new ListElement(ListElement.Kind.INT, "int", true, false, null);
    }
    if (isType(list, LONG_ARRAY_LIST)) {
      return new ListElement(ListElement.Kind.LONG, "long", true, false, null);
    }
    final var arguments = ((DeclaredType) list).getTypeArguments();
    if (arguments.size() != 1) {
      return null;
    }
    // compares the erased element, so type annotations like @Nullable do not matter
    final var argument = arguments.get(0);
    if (isType(argument, DIRECT_BUFFER)) {
      return new ListElement(ListElement.Kind.STRING, DIRECT_BUFFER, false, false, null);
    }
    if (isType(argument, "java.lang.Long")) {
      return new ListElement(ListElement.Kind.LONG, "java.lang.Long", false, false, null);
    }
    if (isType(argument, "java.lang.Integer")) {
      return new ListElement(ListElement.Kind.INT, "java.lang.Integer", false, false, null);
    }
    if (argument.getKind() != TypeKind.DECLARED) {
      return null;
    }
    if (isType(argument, LIST)) {
      final var inner = elementOf(argument, field);
      return inner == null
          ? null
          : new ListElement(
              ListElement.Kind.LIST, LIST + "<" + inner.javaType() + ">", false, false, inner);
    }
    final var item = (TypeElement) ((DeclaredType) argument).asElement();
    final var pojo = item.getAnnotation(MsgpackPojo.class) != null;
    if (pojo || isBaseValue(item)) {
      requirePublicConstructor(item, field);
      return new ListElement(
          ListElement.Kind.ITEM, item.getQualifiedName().toString(), false, pojo, null);
    }
    return null;
  }

  /** Items are created while reading, so the generated codec must be able to call {@code new}. */
  private static void requirePublicConstructor(final TypeElement item, final String field) {
    final boolean found =
        item.getEnclosedElements().stream()
            .anyMatch(
                e ->
                    e.getKind() == ElementKind.CONSTRUCTOR
                        && e.getModifiers().contains(Modifier.PUBLIC)
                        && ((ExecutableElement) e).getParameters().isEmpty());
    if (!found) {
      throw new IllegalArgumentException(
          "Items of the list %s are created while reading, so %s needs a public constructor without parameters"
              .formatted(field, item.getQualifiedName()));
    }
  }

  private boolean isType(final TypeMirror mirror, final String qualifiedName) {
    final var expected = processingEnv.getElementUtils().getTypeElement(qualifiedName);
    final var types = processingEnv.getTypeUtils();
    return expected != null
        && types.isSameType(types.erasure(mirror), types.erasure(expected.asType()));
  }

  /** {@code @Nullable} on the field itself, or on its type (jspecify's is a type annotation). */
  private static boolean isNullable(final Element field) {
    return Stream.concat(
            field.getAnnotationMirrors().stream(), field.asType().getAnnotationMirrors().stream())
        .anyMatch(
            a ->
                ((TypeElement) a.getAnnotationType().asElement())
                    .getQualifiedName()
                    .contentEquals(NULLABLE));
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

  private static String boxedListWarning(final String field, final String agronaList) {
    return "Field %s boxes every element, so reading it allocates: use %s instead"
        .formatted(field, agronaList);
  }

  private void warning(final Element element, final String message) {
    processingEnv.getMessager().printMessage(Kind.WARNING, message, element);
  }
}
