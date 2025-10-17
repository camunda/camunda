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
import java.util.Objects;

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
    return Objects.hash(configuration, roles, scope);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CollectionDataDto that = (CollectionDataDto) o;
    return Objects.equals(configuration, that.configuration)
        && Objects.equals(roles, that.roles)
        && Objects.equals(scope, that.scope);
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
