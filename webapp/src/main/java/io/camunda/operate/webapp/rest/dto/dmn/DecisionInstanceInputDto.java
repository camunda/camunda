/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest.dto.dmn;

import io.camunda.operate.entities.dmn.DecisionInstanceInputEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DecisionInstanceInputDto {

  private String id;
  private String name;
  private String value;

  public String getId() {
    return id;
  }

  public DecisionInstanceInputDto setId(final String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public DecisionInstanceInputDto setName(final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public DecisionInstanceInputDto setValue(final String value) {
    this.value = value;
    return this;
  }

  public static DecisionInstanceInputDto createFrom(DecisionInstanceInputEntity inputEntity) {
    if (inputEntity == null) {
      return null;
    }
    DecisionInstanceInputDto inputDto = new DecisionInstanceInputDto()
        .setId(inputEntity.getId())
        .setName(inputEntity.getName())
        .setValue(inputEntity.getValue());
    return inputDto;
  }

  public static List<DecisionInstanceInputDto> createFrom(List<DecisionInstanceInputEntity> inputEntities) {
    List<DecisionInstanceInputDto> result = new ArrayList<>();
    if (inputEntities != null) {
      for (DecisionInstanceInputEntity inputEntity: inputEntities) {
        if (inputEntity != null) {
          result.add(createFrom(inputEntity));
        }
      }
    }
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DecisionInstanceInputDto that = (DecisionInstanceInputDto) o;
    return Objects.equals(id, that.id) &&
        Objects.equals(name, that.name) &&
        Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, value);
  }
}
