/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.lang.reflect.InvocationTargetException;
import java.util.stream.Stream;

public final class ReflectUtil {

  private ReflectUtil() {}

  public static <T> T newInstance(final Class<T> clazz) {
    try {
      return clazz.getDeclaredConstructor().newInstance();
    } catch (final InstantiationException
        | IllegalAccessException
        | NoSuchMethodException
        | InvocationTargetException e) {
      throw new IllegalStateException(
          String.format(
              "Failed to instantiate class %s with the default constructor", clazz.getName()),
          e);
    }
  }

  /**
   * Returns a stream of all non-interface classes that are a subtypes of the given sealed class.
   */
  public static <T> Stream<Class<T>> implementationsOfSealedInterface(final Class<T> clazz) {
    if (!clazz.isSealed()) {
      throw new IllegalArgumentException(String.format("Class %s is not sealed", clazz.getName()));
    }
    return Stream.of(clazz.getPermittedSubclasses())
        .flatMap(
            c -> {
              if (c.isSealed()) {
                return implementationsOfSealedInterface((Class<T>) c);
              } else {
                return Stream.of((Class<T>) c);
              }
            });
  }
}
