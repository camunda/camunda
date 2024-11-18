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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdvancedSearchFilterUtil {

  private static final Logger LOG = LoggerFactory.getLogger(AdvancedSearchFilterUtil.class);

  public static <T> Operation<T> mapToOperation(final T value) {
    return Operation.eq(value);
  }

  public static <T> Function<Object, List<Operation<T>>> mapToOperations(final Class<T> tClass) {
    return (final Object filter) -> mapToOperations(filter, tClass);
  }

  private static <T> T convertValue(final Class<T> tClass, final Object value) {
    if (!tClass.isInstance(value)) {
      if (tClass == String.class) {
        return (T) value.toString();
      }
    }

    return tClass.cast(value);
  }

  protected static <T> List<Operation<T>> mapToOperations(
      final Object filter, final Class<T> tClass) {
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
              final var tValues = values.stream().map(v -> convertValue(tClass, v)).toList();
              operations.add(new Operation<>(operator, tValues));
            }
          } else {
            operations.add(new Operation<>(operator, convertValue(tClass, value)));
          }
        }
      } catch (final InvocationTargetException | IllegalAccessException e) {
        LOG.error(e.getMessage(), e);
      }
    }
    return operations;
  }
}
