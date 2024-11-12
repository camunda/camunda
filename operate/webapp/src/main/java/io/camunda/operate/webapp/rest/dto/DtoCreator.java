/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto;

import io.camunda.operate.exceptions.OperateRuntimeException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class DtoCreator {

  public static <T extends CreatableFromEntity<T, E>, E extends Object> T create(
      final E from, final Class<T> clazz) {
    if (from == null) {
      return null;
    }
    try {
      final T newDto = clazz.getDeclaredConstructor().newInstance();
      newDto.fillFrom(from);
      return newDto;
    } catch (final InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      throw new OperateRuntimeException("Not implemented");
    }
  }

  public static <T extends CreatableFromEntity<T, E>, E extends Object> List<T> create(
      final List<E> entities, final Class<T> clazz) {
    if (entities == null) {
      return new ArrayList<>();
    }
    return entities.stream()
        .filter(item -> item != null)
        .map(item -> create(item, clazz))
        .collect(Collectors.toList());
  }
}
