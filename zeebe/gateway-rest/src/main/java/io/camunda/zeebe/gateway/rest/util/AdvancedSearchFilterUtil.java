/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.util;

import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdvancedSearchFilterUtil {

  private static final Logger LOG = LoggerFactory.getLogger(AdvancedSearchFilterUtil.class);

  public static <T> Function<Object, List<Operation<T>>> mapToOperations(final Class<T> tClass) {
    return (final Object filter) -> mapToOperations(filter, tClass);
  }

  public static <T> Function<Object, List<Operation<T>>> mapToOperations(
      final Class<T> tClass, final CustomConverter<T> customConverter) {
    return (final Object filter) -> mapToOperations(filter, tClass, customConverter);
  }

  protected static <T> T convertValue(final Class<T> tClass, final Object value) {
    return convertValue(tClass, value, null);
  }

  protected static <T> T convertValue(
      final Class<T> tClass, final Object value, final CustomConverter<T> customConverter) {
    if (customConverter != null && customConverter.canConvert(value)) {
      return customConverter.convertValue(value);
    }
    if (value == null) {
      return null;
    } else if (tClass.isInstance(value)) {
      return tClass.cast(value);
    } else if (tClass == String.class) {
      return (T) value.toString();
    } else if (tClass == OffsetDateTime.class && value instanceof String) {
      try {
        return (T) OffsetDateTime.parse((String) value);
      } catch (final DateTimeParseException e) {
        throw new IllegalArgumentException("Failed to parse date-time: [%s]".formatted(value), e);
      }
    } else if (tClass == Long.class && value instanceof String) {
      return (T) Long.valueOf((String) value);
    }

    throw new IllegalArgumentException(
        "Could not convert request value [%s] to [%s]".formatted(value, tClass.getName()));
  }

  /**
   * True for an advanced filter object, one with operator getters such as {@code get$In}, that has
   * no usable value, for example {@code {"$in": []}}. Any other value is not empty.
   */
  public static boolean isEmptyAdvancedFilter(final Object value) {
    boolean isAdvancedFilter = false;
    for (final Operator operator : Operator.values()) {
      final Method getter = findOperatorGetter(value.getClass(), operator);
      if (getter == null) {
        continue;
      }
      isAdvancedFilter = true;
      if (hasUsableValue(getter, value)) {
        return false;
      }
    }
    return isAdvancedFilter;
  }

  private static boolean hasUsableValue(final Method getter, final Object filter) {
    try {
      final var operand = getter.invoke(filter);
      // an empty or null-only list is dropped by the mapping and selects nothing; a list that
      // still holds a value keeps it, as nulls are removed individually
      return operand != null
          && !(operand instanceof final List<?> list && list.stream().allMatch(Objects::isNull));
    } catch (final InvocationTargetException | IllegalAccessException e) {
      LOG.error(e.getMessage(), e);
      return false;
    }
  }

  private static Method findOperatorGetter(final Class<?> filterClass, final Operator operator) {
    try {
      final var methodName = "get$%s".formatted(StringUtils.capitalize(operator.getValue()));
      return filterClass.getMethod(methodName);
    } catch (final NoSuchMethodException e) {
      return null;
    }
  }

  protected static <T> List<Operation<T>> mapToOperations(
      final Object filter, final Class<T> tClass) {
    return mapToOperations(filter, tClass, null);
  }

  protected static <T> List<Operation<T>> mapToOperations(
      final Object filter, final Class<T> tClass, final CustomConverter<T> customConverter) {
    final var fClass = filter.getClass();
    final var operations = new ArrayList<Operation<T>>();
    for (final Operator operator : Operator.values()) {
      final Method method = findOperatorGetter(fClass, operator);
      if (method == null) {
        // filter doesn't contain this method
        continue;
      }
      method.setAccessible(true);
      try {
        final var value = method.invoke(filter);
        if (value != null) {
          if (value instanceof final Boolean booleanValue) {
            operations.add(Operation.exists(booleanValue));
          } else if (value instanceof final List<?> values) {
            if (!values.isEmpty()) {
              final var tValues =
                  values.stream().map(v -> convertValue(tClass, v, customConverter)).toList();
              operations.add(new Operation<>(operator, tValues));
            }
          } else {
            operations.add(new Operation<>(operator, convertValue(tClass, value, customConverter)));
          }
        }
      } catch (final InvocationTargetException | IllegalAccessException e) {
        LOG.error(e.getMessage(), e);
      }
    }
    return operations;
  }
}
