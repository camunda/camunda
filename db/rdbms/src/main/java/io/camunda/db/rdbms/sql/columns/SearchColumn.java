/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.zeebe.util.DateUtil;
import java.lang.reflect.InvocationTargetException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.function.Function;

public interface SearchColumn<T> {

  static final Map<Class<?>, Function<Object, Object>> converters = Map.of(
      Long.class, obj -> Long.valueOf(obj.toString()),
      OffsetDateTime.class, obj -> DateUtil.fuzzyToOffsetDateTime(obj)
  );

  String name();

  default String property() {
    return null;
  }

  default Class<T> getEntityClass() {
    return null;
  }

  default Object getPropertyValue(final T entity) {
    try {
      final var method = getEntityClass().getMethod(property());
      return method.invoke(entity);
    } catch (final NoSuchMethodException e) {
      throw new RuntimeException(e);
    } catch (final InvocationTargetException e) {
      throw new RuntimeException(e);
    } catch (final IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  default Class getPropertyType() {
    try {
      final var method = getEntityClass().getMethod(property());
      return method.getReturnType();
    } catch (final NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  // deprecated
  default Object convertSortOption(final Object object) {
    final Class<?> targetType = getPropertyType();
    return converters.getOrDefault(targetType, obj -> obj).apply(object);
  }
}
