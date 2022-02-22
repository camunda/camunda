/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest.dto;

import io.camunda.operate.entities.OperateEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class DtoCreator {

  public static <T extends CreatableFromEntity<T, E>, E extends OperateEntity> T create(E from,
      Class<T> clazz) {
    if (from == null) {
      return null;
    }
    try {
      T newDto = clazz.getDeclaredConstructor().newInstance();
      newDto.fillFrom(from);
      return newDto;
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new OperateRuntimeException("Not implemented");
    }
  }

  public static <T extends CreatableFromEntity<T, E>, E extends OperateEntity> List<T> create(
      List<E> entities, Class<T> clazz) {
    if (entities == null) {
      return new ArrayList<>();
    }
    return entities.stream().filter(item -> item != null)
        .map(item -> create(item, clazz))
        .collect(Collectors.toList());
  }

}
