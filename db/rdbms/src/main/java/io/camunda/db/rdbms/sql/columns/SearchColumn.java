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

  Map<Class<?>, Function<Object, Object>> CONVERTERS =
      Map.of(
          Long.class, obj -> Long.valueOf(obj.toString()),
          Integer.class, obj -> Integer.valueOf(obj.toString()),
          String.class, obj -> obj.toString(),
          Boolean.class, obj -> Boolean.valueOf(obj.toString()),
          OffsetDateTime.class, obj -> DateUtil.fuzzyToOffsetDateTime(obj));

  String name();

  String property();

  Class<T> getEntityClass();

  default Class getPropertyType() {
    try {
      final var method = getEntityClass().getMethod(property());
      return method.getReturnType();
    } catch (final NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
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

  default Object convertToPropertyValue(final Object object) {
    if (object == null) {
      return null;
    }

    return getConverter(getPropertyType()).apply(object);
  }

  static Function<Object, Object> getConverter(final Class<?> type) {
    if (type.isEnum()) {
      return obj -> Enum.valueOf((Class<? extends Enum>) type, obj.toString());
    }

    final var converter = CONVERTERS.get(type);

    if (converter == null) {
      throw new IllegalArgumentException(
          "No converter found for type: " + type.getName() + ". Please register a converter.");
    }

    return converter;
  }
}
