/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.collection;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollectionDataDto {

  protected Object configuration = new HashMap<>();
  private List<CollectionRoleRequestDto> roles = new ArrayList<>();
  private List<CollectionScopeEntryDto> scope = new ArrayList<>();

  public CollectionDataDto() {}

  public Object getConfiguration() {
    return configuration;
  }

  public void setConfiguration(final Object configuration) {
    this.configuration = configuration;
  }

  public List<CollectionRoleRequestDto> getRoles() {
    return roles;
  }

  public void setRoles(final List<CollectionRoleRequestDto> roles) {
    this.roles = roles;
  }

  public List<CollectionScopeEntryDto> getScope() {
    return scope;
  }

  public void setScope(final List<CollectionScopeEntryDto> scope) {
    this.scope = scope;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CollectionDataDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $configuration = getConfiguration();
    result = result * PRIME + ($configuration == null ? 43 : $configuration.hashCode());
    final Object $roles = getRoles();
    result = result * PRIME + ($roles == null ? 43 : $roles.hashCode());
    final Object $scope = getScope();
    result = result * PRIME + ($scope == null ? 43 : $scope.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CollectionDataDto)) {
      return false;
    }
    final CollectionDataDto other = (CollectionDataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$configuration = getConfiguration();
    final Object other$configuration = other.getConfiguration();
    if (this$configuration == null
        ? other$configuration != null
        : !this$configuration.equals(other$configuration)) {
      return false;
    }
    final Object this$roles = getRoles();
    final Object other$roles = other.getRoles();
    if (this$roles == null ? other$roles != null : !this$roles.equals(other$roles)) {
      return false;
    }
    final Object this$scope = getScope();
    final Object other$scope = other.getScope();
    if (this$scope == null ? other$scope != null : !this$scope.equals(other$scope)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "CollectionDataDto(configuration="
        + getConfiguration()
        + ", roles="
        + getRoles()
        + ", scope="
        + getScope()
        + ")";
  }

  public enum Fields {
    configuration,
    roles,
    scope
  }
}
