/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.util;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ZeebeImmutableProtocol;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
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
 * <p>Expected usage is via {@link #forEach(Consumer)}.
 */
public final class ProtocolTypeMapping {
  private static final String PROTOCOL_PACKAGE_NAME = Record.class.getPackage().getName() + "*";
  private static final Logger LOGGER = LoggerFactory.getLogger(ProtocolTypeMapping.class);
  private final Map<Class<?>, Mapping<?>> concreteMappings;

  private ProtocolTypeMapping() {
    concreteMappings = new HashMap<>();
    loadTypeMappings();
  }

  public static void forEach(final Consumer<Mapping<?>> consumer) {
    Singleton.INSTANCE.concreteMappings.values().forEach(consumer);
  }

  @SuppressWarnings("java:S1452") // the expected usage is to pass it as is with the wildcard type
  public static Mapping<?> mappingForConcreteType(final Class<?> concreteType) {
    return Singleton.INSTANCE.concreteMappings.get(concreteType);
  }

  private void loadTypeMappings() {
    final ClassInfoList abstractTypes = findProtocolTypes();
    for (final ClassInfo abstractType : abstractTypes) {
      LOGGER.trace("Found abstract protocol type {}", abstractType);
      loadTypeMappingsFor(abstractType, abstractType.loadClass());
    }

    if (abstractTypes.isEmpty()) {
      LOGGER.warn(
          "Found no abstract protocol types in package {}; deserialization will most likely not work",
          PROTOCOL_PACKAGE_NAME);
    }
  }

  private <T> void loadTypeMappingsFor(final ClassInfo abstractType, final Class<T> abstractClass) {
    Objects.requireNonNull(abstractType, "must specify an abstract type");
    Objects.requireNonNull(abstractClass, "must specify the abstract class");

    // a few of the abstract types actually extend each other, so we need to lookup only the direct
    // concrete types, as otherwise we won't be able to tell which implementation is the right one
    final ClassInfoList concreteTypes =
        abstractType
            .getClassesImplementing()
            .filter(ClassInfo::isStandardClass)
            .filter(info -> !info.isAbstract())
            .directOnly();

    for (final ClassInfo concreteType : concreteTypes) {
      final Class<T> concreteClass = concreteType.loadClass(abstractClass);
      LOGGER.trace(
          "Found concrete type mapping for protocol class {} => {}", abstractClass, concreteClass);
      loadTypeMappingsFor(concreteType, abstractClass, concreteClass);
    }

    if (concreteTypes.isEmpty()) {
      LOGGER.warn(
          "Found no concrete type mapping for protocol type {}; deserialization may not always work",
          abstractClass);
    }
  }

  private <T> void loadTypeMappingsFor(
      final ClassInfo concreteType, final Class<T> abstractClass, final Class<T> concreteClass) {
    Objects.requireNonNull(concreteType, "must specify a concrete type");
    Objects.requireNonNull(abstractClass, "must specify an abstract class");
    Objects.requireNonNull(concreteClass, "must specify a concrete class");

    final ClassInfoList builderTypes =
        concreteType.getInnerClasses().filter(info -> "Builder".equals(info.getSimpleName()));
    for (final ClassInfo builder : builderTypes) {
      final Mapping<T> mapping = new Mapping<>(abstractClass, concreteClass, builder.loadClass());
      concreteMappings.put(concreteClass, mapping);
    }

    if (builderTypes.isEmpty()) {
      LOGGER.warn(
          "Found no inner builder type for concrete type {} of protocol type {}; deserialization "
              + "may not work",
          concreteClass,
          abstractClass);
    }
  }

  static ClassInfoList findProtocolTypes() {
    return new ClassGraph()
        .acceptPackages(PROTOCOL_PACKAGE_NAME)
        .enableAnnotationInfo()
        .scan()
        .getAllInterfaces()
        .filter(info -> info.hasAnnotation(ZeebeImmutableProtocol.class))
        .directOnly();
  }

  public static final class Mapping<T> {
    final Class<T> abstractClass;
    final Class<? extends T> concreteClass;
    final Class<?> builderClass;

    private Mapping(
        final Class<T> abstractClass,
        final Class<? extends T> concreteClass,
        final Class<?> builderClass) {
      this.abstractClass = Objects.requireNonNull(abstractClass, "must specify an abstract class");
      this.concreteClass = Objects.requireNonNull(concreteClass, "must specify a concrete class");
      this.builderClass = Objects.requireNonNull(builderClass, "must specify a builder class");
    }

    public Class<T> getAbstractClass() {
      return abstractClass;
    }

    public Class<? extends T> getConcreteClass() {
      return concreteClass;
    }

    public Class<?> getBuilderClass() {
      return builderClass;
    }
  }

  private static final class Singleton {
    private static final ProtocolTypeMapping INSTANCE = new ProtocolTypeMapping();
  }
}
