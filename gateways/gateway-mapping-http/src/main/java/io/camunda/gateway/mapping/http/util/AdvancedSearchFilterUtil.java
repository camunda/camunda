/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.util;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_DATE_PARSING;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_INVALID_KEY_FORMAT;

import io.camunda.gateway.mapping.http.converters.CustomConverter;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdvancedSearchFilterUtil {

  private static final Logger LOG = LoggerFactory.getLogger(AdvancedSearchFilterUtil.class);

  public static Function<Object, List<Operation<Long>>> mapToKeyOperations(
      final String fieldName, final List<String> validationErrors) {
    return (final Object filter) ->
        mapToTypedOperations(
            filter,
            value -> {
              if (value instanceof final Long l) {
                return l;
              } else if (value instanceof final String s) {
                try {
                  return Long.valueOf(s);
                } catch (final NumberFormatException e) {
                  validationErrors.add(ERROR_MESSAGE_INVALID_KEY_FORMAT.formatted(fieldName, s));
                  return null;
                }
              }
              validationErrors.add(ERROR_MESSAGE_INVALID_KEY_FORMAT.formatted(fieldName, value));
              return null;
            });
  }

  public static Function<Object, List<Operation<Integer>>> mapToIntegerOperations(
      final String fieldName, final List<String> validationErrors) {
    return (final Object filter) ->
        mapToTypedOperations(
            filter,
            value -> {
              if (value instanceof final Integer i) {
                return i;
              }
              validationErrors.add(
                  "The provided %s '%s' is not a valid integer value.".formatted(fieldName, value));
              return null;
            });
  }

  public static Function<Object, List<Operation<String>>> mapToStringOperations() {
    return (final Object filter) -> mapToTypedOperations(filter, Object::toString);
  }

  public static Function<Object, List<Operation<String>>> mapToStringOperations(
      final String fieldName,
      final List<String> validationErrors,
      final CustomConverter<String> converter) {
    return (final Object filter) ->
        mapToTypedOperations(
            filter,
            value -> {
              if (converter.canConvert(value)) {
                try {
                  return converter.convertValue(value);
                } catch (final Exception e) {
                  validationErrors.add(
                      "The provided %s '%s' is not valid: %s"
                          .formatted(fieldName, value, e.getMessage()));
                  return null;
                }
              }
              return value.toString();
            });
  }

  public static Function<Object, List<Operation<OffsetDateTime>>> mapToOffsetDateTimeOperations(
      final String fieldName, final List<String> validationErrors) {
    return (final Object filter) ->
        mapToTypedOperations(
            filter,
            value -> {
              if (value instanceof final OffsetDateTime odt) {
                return odt;
              } else if (value instanceof final String s) {
                try {
                  return OffsetDateTime.parse(s);
                } catch (final DateTimeParseException e) {
                  validationErrors.add(ERROR_MESSAGE_DATE_PARSING.formatted(fieldName, s));
                  return null;
                }
              }
              validationErrors.add(ERROR_MESSAGE_DATE_PARSING.formatted(fieldName, value));
              return null;
            });
  }

  private static <T> List<Operation<T>> mapToTypedOperations(
      final Object filter, final NullableConverter<T> converter) {
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
        if (value == null) {
          continue;
        }
        addTypedOperation(operations, operator, value, converter);
      } catch (final InvocationTargetException | IllegalAccessException e) {
        LOG.error(e.getMessage(), e);
      }
    }
    return operations;
  }

  private static <T> void addTypedOperation(
      final List<Operation<T>> operations,
      final Operator operator,
      final Object value,
      final NullableConverter<T> converter) {
    if (value instanceof final Boolean booleanValue) {
      operations.add(Operation.exists(booleanValue));
    } else if (value instanceof final List<?> values) {
      convertListOperation(operations, operator, values, converter);
    } else {
      final T converted = converter.apply(value);
      if (converted != null) {
        operations.add(new Operation<>(operator, converted));
      }
    }
  }

  private static <T> void convertListOperation(
      final List<Operation<T>> operations,
      final Operator operator,
      final List<?> values,
      final NullableConverter<T> converter) {
    if (values.isEmpty()) {
      return;
    }
    final var tValues = new ArrayList<T>();
    boolean hasNull = false;
    for (final Object v : values) {
      if (v == null) {
        hasNull = true;
        continue;
      }
      final T converted = converter.apply(v);
      if (converted == null) {
        hasNull = true;
      } else {
        tValues.add(converted);
      }
    }
    if (!hasNull && !tValues.isEmpty()) {
      operations.add(new Operation<>(operator, tValues));
    }
  }

  /**
   * Function variant that tolerates {@code null} results to signal "could not convert this value".
   * Used by the filter-operation mappers which aggregate validation errors instead of throwing.
   */
  @FunctionalInterface
  private interface NullableConverter<T> {
    @Nullable T apply(Object value);
  }
}
