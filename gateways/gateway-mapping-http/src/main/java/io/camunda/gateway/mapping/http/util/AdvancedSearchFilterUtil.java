/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.util;

import io.camunda.gateway.mapping.http.converters.CustomConverter;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
      try {
        return (T) Long.valueOf((String) value);
      } catch (final NumberFormatException e) {
        throw new IllegalArgumentException(
            "The provided value '%s' is not a valid key.".formatted(value), e);
      }
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
    // Handle plain values (strict contract deserialization of Object fields).
    // Jackson deserializes e.g. "5" as String, 5 as Integer. Treat as implicit $eq.
    if (filter instanceof String || filter instanceof Number || filter instanceof Boolean) {
      if (filter instanceof final Boolean booleanValue) {
        return List.of(Operation.exists(booleanValue));
      }
      return List.of(
          new Operation<>(Operator.EQUALS, convertValue(tClass, filter, customConverter)));
    }

    // Handle Map values (strict contract deserialization of {"$eq": "5"} etc.).
    // Jackson deserializes complex filter objects as LinkedHashMap when target type is Object.
    if (filter instanceof final Map<?, ?> map) {
      return mapToOperationsFromMap(map, tClass, customConverter);
    }

    // Handle generated filter property types (sealed interface + plain value wrapper + advanced
    // filter records). Records use $eq()/$neq() accessors, not get$Eq()/get$Neq().
    if (filter.getClass().isRecord()) {
      return mapToOperationsFromRecord(filter, tClass, customConverter);
    }

    // Protocol model filter types — use reflection to call get$Eq(), get$Neq() etc.
    final var fClass = filter.getClass();
    final var operations = new ArrayList<Operation<T>>();
    for (final Operator operator : Operator.values()) {
      final Method method;
      try {
        final var methodName = "get$%s".formatted(StringUtils.capitalize(operator.getValue()));
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

  private static <T> List<Operation<T>> mapToOperationsFromMap(
      final Map<?, ?> map, final Class<T> tClass, final CustomConverter<T> customConverter) {
    final var operations = new ArrayList<Operation<T>>();
    for (final Operator operator : Operator.values()) {
      if (operator.getValue() == null) {
        continue;
      }
      final var key = "$" + operator.getValue();
      final var value = map.get(key);
      if (value == null) {
        continue;
      }
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
    return operations;
  }

  /**
   * Handles generated filter property records (from sealed interface deserialization). Plain-value
   * wrappers have a single {@code value()} accessor. Advanced filter records have operator
   * accessors: {@code $eq()}, {@code $neq()}, etc.
   */
  private static <T> List<Operation<T>> mapToOperationsFromRecord(
      final Object filter, final Class<T> tClass, final CustomConverter<T> customConverter) {
    final var fClass = filter.getClass();

    // Check for plain-value wrapper: has a single "value" accessor → implicit $eq.
    try {
      final var valueMethod = fClass.getMethod("value");
      final var plainValue = valueMethod.invoke(filter);
      if (plainValue != null) {
        return List.of(
            new Operation<>(Operator.EQUALS, convertValue(tClass, plainValue, customConverter)));
      }
      return List.of();
    } catch (final NoSuchMethodException e) {
      // Not a plain-value wrapper — fall through to operator extraction.
    } catch (final ReflectiveOperationException e) {
      LOG.error("Failed to invoke value() on record {}", fClass.getSimpleName(), e);
      return List.of();
    }

    // Advanced filter record: extract operator fields ($eq, $neq, $exists, etc.)
    final var operations = new ArrayList<Operation<T>>();
    for (final Operator operator : Operator.values()) {
      if (operator.getValue() == null) {
        continue;
      }
      final Method method;
      try {
        // Record accessors match the field name: $eq(), $neq(), $exists(), etc.
        method = fClass.getMethod("$" + operator.getValue());
      } catch (final NoSuchMethodException e) {
        continue;
      }
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
      } catch (final ReflectiveOperationException e) {
        LOG.error(e.getMessage(), e);
      }
    }
    return operations;
  }
}
