/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson;

import edu.umd.cs.findbugs.annotations.DefaultAnnotationForFields;
import edu.umd.cs.findbugs.annotations.DefaultAnnotationForParameters;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.ReturnValuesAreNonnullByDefault;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ZeebeImmutableProtocol;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.jcip.annotations.Immutable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility class to lazy load and cache the mappings between the protocol abstract classes, e.g.
 * {@link Record}, {@link io.camunda.zeebe.protocol.record.value.ErrorRecordValue}, and their
 * concrete immutable implementations, e.g. {@link
 * io.camunda.zeebe.protocol.record.ImmutableRecord}.
 *
 * <p>This class is thread-safe, including static initialization by multiple concurrent class
 * loaders.
 *
 * <p>Expected usage is via {@link #forEach(Consumer)}. See {@link ZeebeProtocolModule} for an
 * example.
 */
@Immutable
@ReturnValuesAreNonnullByDefault
@DefaultAnnotationForParameters(NonNull.class)
@DefaultAnnotationForFields(NonNull.class)
final class ProtocolTypeMapping {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProtocolTypeMapping.class);
  private final Map<Class<?>, TypeMapping<?>> typeMappings;

  private ProtocolTypeMapping() {
    typeMappings = Collections.unmodifiableMap(loadTypeMappings());
  }

  static void forEach(final Consumer<TypeMapping<?>> consumer) {
    Singleton.INSTANCE.typeMappings.values().forEach(consumer);
  }

  @SuppressWarnings("java:S1452") // the expected usage is to pass it as is with the wildcard type
  @Nullable
  static TypeMapping<?> mappingForConcreteType(@Nullable final Class<?> concreteType) {
    return Singleton.INSTANCE.typeMappings.get(concreteType);
  }

  private Map<Class<?>, TypeMapping<?>> loadTypeMappings() {
    final Map<Class<?>, TypeMapping<?>> mappings = new HashMap<>();
    final String protocolPackageName = Record.class.getPackage().getName() + "*";
    final ClassInfoList abstractTypes = findProtocolTypes(protocolPackageName);
    for (final ClassInfo abstractType : abstractTypes) {
      LOGGER.trace("Found abstract protocol type {}", abstractType);
      mappings.putAll(loadTypeMappingsFor(abstractType, abstractType.loadClass()));
    }

    if (abstractTypes.isEmpty()) {
      LOGGER.warn(
          "Found no abstract protocol types in package {}; deserialization will most likely not work",
          protocolPackageName);
    }

    return mappings;
  }

  private <T> Map<Class<?>, TypeMapping<T>> loadTypeMappingsFor(
      final ClassInfo abstractType, final Class<T> abstractClass) {
    Objects.requireNonNull(abstractType, "must specify an abstract type");
    Objects.requireNonNull(abstractClass, "must specify the abstract class");

    final Map<Class<?>, TypeMapping<T>> mappings = new HashMap<>();
    // a few of the abstract types actually extend each other, so we need to lookup only the direct
    // concrete types, as otherwise we won't be able to tell which implementation is the right one
    final ClassInfoList concreteTypes =
        abstractType.getClassesImplementing().filter(ClassInfo::isStandardClass).directOnly();

    for (final ClassInfo concreteType : concreteTypes) {
      final Class<T> concreteClass = concreteType.loadClass(abstractClass);
      LOGGER.trace(
          "Found concrete type mapping for protocol class {} => {}", abstractClass, concreteClass);
      mappings.putAll(loadTypeMappingsFor(concreteType, abstractClass, concreteClass));
    }

    if (concreteTypes.isEmpty()) {
      LOGGER.warn(
          "Found no concrete type mapping for protocol type {}; deserialization may not always work",
          abstractClass);
    }

    return mappings;
  }

  private <T> Map<Class<?>, TypeMapping<T>> loadTypeMappingsFor(
      final ClassInfo concreteType, final Class<T> abstractClass, final Class<T> concreteClass) {
    Objects.requireNonNull(concreteType, "must specify a concrete type");
    Objects.requireNonNull(abstractClass, "must specify an abstract class");
    Objects.requireNonNull(concreteClass, "must specify a concrete class");

    final Map<Class<?>, TypeMapping<T>> mappings = new HashMap<>();
    final ClassInfoList builderTypes =
        concreteType.getInnerClasses().filter(info -> "Builder".equals(info.getSimpleName()));
    for (final ClassInfo builder : builderTypes) {
      final TypeMapping<T> typeMapping =
          new TypeMapping<>(abstractClass, concreteClass, builder.loadClass());
      mappings.put(concreteClass, typeMapping);
    }

    if (builderTypes.isEmpty()) {
      LOGGER.warn(
          "Found no inner builder type for concrete type {} of protocol type {}; deserialization "
              + "may not work",
          concreteClass,
          abstractClass);
    }

    return mappings;
  }

  private ClassInfoList findProtocolTypes(final String packageName) {
    return new ClassGraph()
        .acceptPackages(Objects.requireNonNull(packageName, "must specify a package name"))
        .enableAnnotationInfo()
        .scan()
        .getAllInterfaces()
        .filter(info -> info.hasAnnotation(ZeebeImmutableProtocol.class));
  }

  @Immutable
  @ReturnValuesAreNonnullByDefault
  @DefaultAnnotationForParameters(NonNull.class)
  @DefaultAnnotationForFields(NonNull.class)
  static final class TypeMapping<T> {
    final Class<T> abstractClass;
    final Class<? extends T> concreteClass;
    final Class<?> builderClass;

    private TypeMapping(
        final Class<T> abstractClass,
        final Class<? extends T> concreteClass,
        final Class<?> builderClass) {
      this.abstractClass = Objects.requireNonNull(abstractClass, "must specify an abstract class");
      this.concreteClass = Objects.requireNonNull(concreteClass, "must specify a concrete class");
      this.builderClass = Objects.requireNonNull(builderClass, "must specify a builder class");
    }
  }

  private static final class Singleton {
    private static final ProtocolTypeMapping INSTANCE = new ProtocolTypeMapping();
  }
}
