/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.dmn;

import io.camunda.operate.webapp.rest.dto.CreatableFromEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceInputEntity;
import java.util.Objects;

public class DecisionInstanceInputDto
    implements CreatableFromEntity<DecisionInstanceInputDto, DecisionInstanceInputEntity> {

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

  @Override
  public DecisionInstanceInputDto fillFrom(final DecisionInstanceInputEntity inputEntity) {
    return setId(inputEntity.getId())
        .setName(inputEntity.getName())
        .setValue(inputEntity.getValue());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, value);
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
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(value, that.value);
  }
}
