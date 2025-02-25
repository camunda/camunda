/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.stream.Stream;

public final class ReflectUtil {

  private ReflectUtil() {}

  /**
   * Returns a new instance of the given class, using the default, no-arguments constructor.
   *
   * @param clazz the class to instantiate
   * @return an instance of the class
   * @param <T> the expected type of the instance
   */
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
   * Sets the field to be accessible via reflection if it is not currently for the given instance
   * object. This replaces the old Junit 5 `ReflectUtils.makeAccessible` which was removed from
   * their platform.
   *
   * @param member the field to make accessible
   * @param instance the instance on which we check accessibility
   * @return the field, accessible
   * @param <M> the type of the member
   * @param <U> the type of the instance, typically just {@code Object}
   */
  public static <M extends AccessibleObject & Member, U> M makeAccessible(
      final M member, final U instance) {
    var canAccess = false;
    try {
      // this throws an IllegalArgumentException if member is not a field/method of Instance
      canAccess = member.canAccess(instance);
    } catch (final IllegalArgumentException e) {
      final var modifiers = member.getModifiers();
      if (Modifier.isPublic(modifiers)
          && Modifier.isPublic(member.getDeclaringClass().getModifiers())) {
        canAccess = true;
      }
    }
    if (!canAccess) {
      member.setAccessible(true);
    }

    return member;
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
