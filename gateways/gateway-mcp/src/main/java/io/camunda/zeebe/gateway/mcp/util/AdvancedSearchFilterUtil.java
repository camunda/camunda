/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.util;

import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
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

  protected static <T> List<Operation<T>> mapToOperations(
      final Object filter, final Class<T> tClass) {
    return mapToOperations(filter, tClass, null);
  }

  protected static <T> List<Operation<T>> mapToOperations(
      final Object filter, final Class<T> tClass, final CustomConverter<T> customConverter) {
    final var fClass = filter.getClass();
    final var operations = new ArrayList<Operation<T>>();
    for (final Operator operator : Operator.values()) {
      final Method method;
      try {
        final var methodName = operator.getValue();
        if (methodName == null) {
          continue;
        }
        method = fClass.getMethod(methodName);
      } catch (final NoSuchMethodException e) {
        // ignore exception, filter doesn't contain this method
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
