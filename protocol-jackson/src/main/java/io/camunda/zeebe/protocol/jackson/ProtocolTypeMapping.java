/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ZeebeImmutableProtocol;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
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
@ThreadSafe
@Immutable
@ParametersAreNonnullByDefault
final class ProtocolTypeMapping {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProtocolTypeMapping.class);
  private final Map<Class<?>, TypeMapping<?>> typeMappings;

  private ProtocolTypeMapping() {
    typeMappings = Collections.unmodifiableMap(loadTypeMappings());
  }

  static void forEach(final Consumer<TypeMapping<?>> consumer) {
    Singleton.INSTANCE.typeMappings.values().forEach(consumer);
  }

  @Nullable
  static TypeMapping<?> mappingForConcreteType(final Class<?> concreteType) {
    return Singleton.INSTANCE.typeMappings.get(concreteType);
  }

  @Nonnull
  private Map<Class<?>, TypeMapping<?>> loadTypeMappings() {
    final Map<Class<?>, TypeMapping<?>> mappings = new HashMap<>();
    final String protocolPackageName = Record.class.getPackage().getName();
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

  @Nonnull
  private <T> Map<Class<?>, TypeMapping<T>> loadTypeMappingsFor(
      final ClassInfo abstractType, final Class<T> abstractClass) {
    final Map<Class<?>, TypeMapping<T>> mappings = new HashMap<>();
    final ClassInfoList concreteTypes =
        abstractType.getClassesImplementing().filter(ClassInfo::isStandardClass);

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

  @Nonnull
  private <T> Map<Class<?>, TypeMapping<T>> loadTypeMappingsFor(
      final ClassInfo concreteType, final Class<T> abstractClass, final Class<T> concreteClass) {
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

  @Nonnull
  private ClassInfoList findProtocolTypes(final String packageName) {
    return new ClassGraph()
        .acceptPackages(packageName)
        .enableAnnotationInfo()
        .scan()
        .getAllInterfaces()
        .filter(info -> info.hasAnnotation(ZeebeImmutableProtocol.class));
  }

  @Immutable
  static final class TypeMapping<T> {
    final Class<T> abstractClass;
    final Class<? extends T> concreteClass;
    final Class<?> builderClass;

    private TypeMapping(
        final Class<T> abstractClass,
        final Class<? extends T> concreteClass,
        final Class<?> builderClass) {
      this.abstractClass = abstractClass;
      this.concreteClass = concreteClass;
      this.builderClass = builderClass;
    }
  }

  private static final class Singleton {
    private static final ProtocolTypeMapping INSTANCE = new ProtocolTypeMapping();
  }
}
