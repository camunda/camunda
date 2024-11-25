/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util;

import io.camunda.search.entities.ValueTypeEnum;
import org.apache.commons.lang3.math.NumberUtils;

public class ValueTypeUtil {

  public static ValueTypeEnum getValueType(final Object value) {
    if (value == null) {
      return ValueTypeEnum.NULL;
    }

    if (value instanceof final String stringValue) {
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
      return String.valueOf(Boolean.parseBoolean(stringValue));
    } else {
      return null;
    }
  }
}
