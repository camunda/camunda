/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util;

import io.camunda.search.entities.ValueTypeEnum;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.math.NumberUtils;

/** Utility class for determining the value type of an object. */
public class ValueTypeUtil {

  public static final String JSON_NULL = "null";

  /**
   * Determines the value type of the given value.
   *
   * <p>
   *
   * <ul>
   *   <li>If the value is null or string "null", the method returns {@link ValueTypeEnum#NULL}.
   *   <li>If the value is a boolean or string boolean ("true", "false", ...) the method returns
   *       {@link ValueTypeEnum#BOOLEAN}.
   *   <li>If the value is a long or integer or a numeric string without ".", the method returns
   *       {@link ValueTypeEnum#LONG}.
   *   <li>If the value is a double or a numeric string with ".", the method returns {@link
   *       ValueTypeEnum#DOUBLE}.
   *   <li>If the value is any other string (can also contain json), the method returns {@link
   *       ValueTypeEnum#STRING}.
   * </ul>
   */
  public static ValueTypeEnum getValueType(final Object value) {
    if (value == null) {
      return ValueTypeEnum.NULL;
    }

    if (value instanceof final String stringValue) {
      if (JSON_NULL.equalsIgnoreCase(stringValue)) {
        return ValueTypeEnum.NULL;
      }

      if ("true".equalsIgnoreCase(stringValue) || "false".equalsIgnoreCase(stringValue)) {
        return ValueTypeEnum.BOOLEAN;
      }

      if (!NumberUtils.isParsable(stringValue)) {
        return ValueTypeEnum.STRING;
      } else {
        if (stringValue.contains(".")) {
          return ValueTypeEnum.DOUBLE;
        } else {
          return ValueTypeEnum.LONG;
        }
      }
    } else if (value instanceof Long || value instanceof Integer) {
      return ValueTypeEnum.LONG;
    } else if (value instanceof Double) {
      return ValueTypeEnum.DOUBLE;
    } else if (value instanceof Boolean) {
      return ValueTypeEnum.BOOLEAN;
    } else {
      throw new IllegalArgumentException("Unsupported value type: " + value.getClass());
    }
  }

  public static Object mapValueType(final Object value, final ValueTypeEnum valueTypeEnum) {
    return switch (valueTypeEnum) {
      case STRING -> value;
      case LONG -> mapLong(value);
      case DOUBLE -> mapDouble(value);
      case BOOLEAN -> mapBoolean(value);
      case NULL -> null;
    };
  }

  public static Long mapLong(final Object value) {
    if (value instanceof final Long longValue) {
      return longValue;
    }
    if (value instanceof final Integer integerValue) {
      return integerValue.longValue();
    } else if (value instanceof final String stringValue) {
      return Long.parseLong(stringValue);
    } else {
      return null;
    }
  }

  public static Double mapDouble(final Object value) {
    if (value instanceof final Double doubleValue) {
      return doubleValue;
    } else if (value instanceof final String stringValue) {
      return Double.parseDouble(stringValue);
    } else {
      return null;
    }
  }

  public static String mapBoolean(final Object value) {
    if (value instanceof Boolean) {
      return String.valueOf(value);
    } else if (value instanceof final String stringValue) {
      return BooleanUtils.toBooleanObject(stringValue).toString();
    } else {
      return null;
    }
  }
}
