/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.broker.protocol;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to map RecordValue interface instances to their corresponding Immutable
 * implementations. This is primarily used in test scenarios where values are deserialized as
 * interface implementations and need to be compared with Immutable* implementations.
 */
public final class ImmutableRecordValueMapper {
  private static final Logger LOG = LoggerFactory.getLogger(ImmutableRecordValueMapper.class);
  private static final Map<Class<?>, Class<?>> INTERFACE_TO_IMMUTABLE_CACHE = new HashMap<>();

  static {
    buildInterfaceToImmutableMapping();
  }

  private ImmutableRecordValueMapper() {}

  /**
   * Maps a RecordValue instance to its corresponding ImmutableRecordValue implementation using the
   * copyOf() static method.
   *
   * @param <T> the RecordValue type
   * @param value the source value to map
   * @return the mapped immutable implementation, or null if value is null
   * @throws IllegalStateException if no immutable implementation is found or mapping fails
   */
  @SuppressWarnings("unchecked")
  public static <T extends RecordValue> T toImmutable(final T value) {
    if (value == null) {
      return null;
    }

    // If already an immutable implementation, return as-is
    if (value.getClass().isAnnotationPresent(ImmutableProtocol.Type.class)) {
      return value;
    }

    final Class<?> valueInterface = findValueInterface(value.getClass());
    final Class<?> immutableClass = INTERFACE_TO_IMMUTABLE_CACHE.get(valueInterface);

    if (immutableClass == null) {
      throw new IllegalStateException(
          "No immutable implementation found for interface: " + valueInterface.getName());
    }

    try {
      // Use the copyOf() static method
      final Method copyOfMethod = immutableClass.getMethod("copyOf", valueInterface);
      return (T) copyOfMethod.invoke(null, value);
    } catch (final Exception e) {
      throw new IllegalStateException(
          "Failed to map value using copyOf() for class: " + immutableClass.getName(), e);
    }
  }

  /**
   * Finds the RecordValue interface implemented by the given class.
   *
   * @param clazz the class to inspect
   * @return the RecordValue interface
   * @throws IllegalStateException if no RecordValue interface is found
   */
  private static Class<?> findValueInterface(final Class<?> clazz) {
    for (final Class<?> iface : clazz.getInterfaces()) {
      if (RecordValue.class.isAssignableFrom(iface) && iface != RecordValue.class) {
        return iface;
      }
    }

    // Check superclass interfaces recursively
    final Class<?> superclass = clazz.getSuperclass();
    if (superclass != null && superclass != Object.class) {
      return findValueInterface(superclass);
    }

    throw new IllegalStateException("No RecordValue interface found for class: " + clazz.getName());
  }

  /** Builds the mapping from RecordValue interfaces to their Immutable implementations. */
  private static void buildInterfaceToImmutableMapping() {
    try (final var scanResult =
        new ClassGraph()
            .enableAnnotationInfo()
            .enableClassInfo()
            .acceptPackages("io.camunda.zeebe.protocol.record.value")
            .scan()) {

      for (final ClassInfo classInfo :
          scanResult.getClassesWithAnnotation(ImmutableProtocol.Type.class)) {
        final Class<?> immutableClass = classInfo.loadClass();

        // Find the RecordValue interface this immutable class implements
        for (final Class<?> iface : immutableClass.getInterfaces()) {
          if (RecordValue.class.isAssignableFrom(iface) && iface != RecordValue.class) {
            INTERFACE_TO_IMMUTABLE_CACHE.put(iface, immutableClass);
            LOG.debug(
                "Mapped interface {} to {}", iface.getSimpleName(), immutableClass.getSimpleName());
          }
        }
      }

      LOG.info(
          "Built mapping for {} RecordValue interfaces to Immutable implementations",
          INTERFACE_TO_IMMUTABLE_CACHE.size());
    }
  }
}
