/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.util;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.Record;
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
  private final Map<Class<?>, Mapping<?>> abstractMappings;
  private final Map<Class<?>, Mapping<?>> builderMappings;

  private ProtocolTypeMapping() {
    concreteMappings = new HashMap<>();
    abstractMappings = new HashMap<>();
    builderMappings = new HashMap<>();

    loadTypeMappings();
  }

  /**
   * Iterates over all known protocol type mappings.
   *
   * @param consumer called for each known protocol type mapping
   */
  public static void forEach(final Consumer<Mapping<?>> consumer) {
    Singleton.INSTANCE.concreteMappings.values().forEach(consumer);
  }

  /**
   * Returns a type mapping for the given concrete type, or null if none found.
   *
   * @param concreteType the concrete type to look for
   * @return a mapping of this concrete type to an abstract type and builder type
   */
  @SuppressWarnings("java:S1452") // the expected usage is to pass it as is with the wildcard type
  public static Mapping<?> getForConcreteType(final Class<?> concreteType) {
    return Singleton.INSTANCE.concreteMappings.get(concreteType);
  }

  /**
   * Returns a type mapping for the given abstract type, or null if none found.
   *
   * @param abstractType the abstract type to look for
   * @return a mapping of this abstract type to an concrete type and builder type
   */
  @SuppressWarnings("java:S1452") // the expected usage is to pass it as is with the wildcard type
  public static Mapping<?> getForAbstractType(final Class<?> abstractType) {
    return Singleton.INSTANCE.abstractMappings.get(abstractType);
  }

  /**
   * Returns a type mapping for the given abstract type, or null if none found.
   *
   * @param builderType the builder type to look for
   * @return a mapping of this abstract type to an abstract type and a concrete type
   */
  @SuppressWarnings("java:S1452") // the expected usage is to pass it as is with the wildcard type
  public static Mapping<?> getForBuilderType(final Class<?> builderType) {
    return Singleton.INSTANCE.builderMappings.get(builderType);
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
      final Class<?> builderClass = builder.loadClass();
      final Mapping<T> mapping = new Mapping<>(abstractClass, concreteClass, builderClass);
      concreteMappings.put(concreteClass, mapping);
      abstractMappings.put(abstractClass, mapping);
      builderMappings.put(builderClass, mapping);
    }

    if (builderTypes.isEmpty()) {
      LOGGER.warn(
          "Found no inner builder type for concrete type {} of protocol type {}; deserialization "
              + "may not work",
          concreteClass,
          abstractClass);
    }
  }

  // visible for testing
  static ClassInfoList findProtocolTypes() {
    return new ClassGraph()
        .acceptPackages(PROTOCOL_PACKAGE_NAME)
        .enableAnnotationInfo()
        .scan()
        .getAllInterfaces()
        .filter(info -> info.hasAnnotation(ImmutableProtocol.class))
        .directOnly();
  }

  /**
   * A mapping between an abstract protocol type (i.e. annotated via {@link ImmutableProtocol}, its
   * concrete type, and its builder type.
   *
   * @param <T> the abstract type of the mapping
   */
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
