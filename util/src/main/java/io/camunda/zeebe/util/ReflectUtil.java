/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util;

import java.lang.reflect.InvocationTargetException;

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
}
