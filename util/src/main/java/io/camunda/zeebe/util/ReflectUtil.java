/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util;

public final class ReflectUtil {

  public static <T> T newInstance(final Class<T> clazz) {
    try {
      return clazz.newInstance();
    } catch (final Exception e) {
      throw new RuntimeException("Could not instantiate class " + clazz.getName(), e);
    }
  }
}
