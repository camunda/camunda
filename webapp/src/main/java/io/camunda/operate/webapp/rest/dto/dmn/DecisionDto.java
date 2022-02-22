/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest.dto.dmn;

import io.camunda.operate.entities.dmn.definition.DecisionDefinitionEntity;
import io.camunda.operate.webapp.rest.dto.CreatableFromEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel("Decision object")
public class DecisionDto implements CreatableFromEntity<DecisionDto, DecisionDefinitionEntity> {

  @ApiModelProperty(value = "Unique id of the decision, must be used when filtering instances by decision ids.")
  private String id;
  private String name;
  private int version;
  private String decisionId;

  public String getId() {
    return id;
  }

  public DecisionDto setId(final String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public DecisionDto setName(final String name) {
    this.name = name;
    return this;
  }

  public int getVersion() {
    return version;
  }

  public DecisionDto setVersion(final int version) {
    this.version = version;
    return this;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public DecisionDto setDecisionId(final String decisionId) {
    this.decisionId = decisionId;
    return this;
  }

  @Override
  public DecisionDto fillFrom(final DecisionDefinitionEntity decisionEntity) {
    return this
        .setId(decisionEntity.getId())
        .setDecisionId(decisionEntity.getDecisionId())
        .setName(decisionEntity.getName())
        .setVersion(decisionEntity.getVersion());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    DecisionDto that = (DecisionDto) o;

    if (version != that.version)
      return false;
    if (id != null ? !id.equals(that.id) : that.id != null)
      return false;
    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;
    return decisionId != null ? decisionId.equals(that.decisionId) : that.decisionId == null;
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (name != null ? name.hashCode() : 0);
    result = 31 * result + version;
    result = 31 * result + (decisionId != null ? decisionId.hashCode() : 0);
    return result;
  }

}
