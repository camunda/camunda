/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest.dto.dmn;

import io.camunda.operate.entities.dmn.DecisionInstanceOutputEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DecisionInstanceOutputDto {

  private String id;
  private String name;
  private String value;
  private String ruleId;
  private int ruleIndex;

  public String getId() {
    return id;
  }

  public DecisionInstanceOutputDto setId(final String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public DecisionInstanceOutputDto setName(final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public DecisionInstanceOutputDto setValue(final String value) {
    this.value = value;
    return this;
  }

  public String getRuleId() {
    return ruleId;
  }

  public DecisionInstanceOutputDto setRuleId(final String ruleId) {
    this.ruleId = ruleId;
    return this;
  }

  public int getRuleIndex() {
    return ruleIndex;
  }

  public DecisionInstanceOutputDto setRuleIndex(final int ruleIndex) {
    this.ruleIndex = ruleIndex;
    return this;
  }

  public static DecisionInstanceOutputDto createFrom(DecisionInstanceOutputEntity outputEntity) {
    if (outputEntity == null) {
      return null;
    }
    DecisionInstanceOutputDto outputDto = new DecisionInstanceOutputDto()
        .setId(outputEntity.getId())
        .setName(outputEntity.getName())
        .setValue(outputEntity.getValue())
        .setRuleId(outputEntity.getRuleId())
        .setRuleIndex(outputEntity.getRuleIndex());
    return outputDto;
  }

  public static List<DecisionInstanceOutputDto> createFrom(List<DecisionInstanceOutputEntity> outputEntities) {
    List<DecisionInstanceOutputDto> result = new ArrayList<>();
    if (outputEntities != null) {
      for (DecisionInstanceOutputEntity outputEntity: outputEntities) {
        if (outputEntity != null) {
          result.add(createFrom(outputEntity));
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
    final DecisionInstanceOutputDto that = (DecisionInstanceOutputDto) o;
    return ruleIndex == that.ruleIndex &&
        Objects.equals(id, that.id) &&
        Objects.equals(name, that.name) &&
        Objects.equals(value, that.value) &&
        Objects.equals(ruleId, that.ruleId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, value, ruleId, ruleIndex);
  }
}
