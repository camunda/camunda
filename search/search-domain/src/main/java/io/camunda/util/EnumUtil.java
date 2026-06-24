/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EnumUtil {

  private static final String UNKNOWN_ENUM_VALUE = "UNKNOWN_ENUM_VALUE";
  private static final String UNKNOWN_DEFAULT_OPEN_API = "UNKNOWN_DEFAULT_OPEN_API";

  private static final Logger LOGGER = LoggerFactory.getLogger(EnumUtil.class);

  private EnumUtil() {}

  public static <E> void logUnknownEnumValue(
      final Object value, final String enumName, final E[] validValues) {
    LOGGER.debug(
        "Unexpected {} '{}', should be one of {}", enumName, value, Arrays.toString(validValues));
  }

  public static <E1 extends Enum<E1>, E2 extends Enum<E2>> E2 convert(
      final E1 source, final Class<E2> targetClass) {
    if (source == null) {
      return null;
    }
    try {
      if (source.name().equals(UNKNOWN_ENUM_VALUE)) {
        return E2.valueOf(targetClass, UNKNOWN_DEFAULT_OPEN_API);
      }
      if (source.name().equals(UNKNOWN_DEFAULT_OPEN_API)) {
        return E2.valueOf(targetClass, UNKNOWN_ENUM_VALUE);
      }
      return E2.valueOf(targetClass, source.name());
    } catch (final IllegalArgumentException e) {
      logUnknownEnumValue(source, source.getClass().getName(), targetClass.getEnumConstants());
      throw new RuntimeException(e);
    }
  }
}
