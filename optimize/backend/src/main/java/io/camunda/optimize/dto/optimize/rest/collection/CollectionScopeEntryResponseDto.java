/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.collection;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.TenantDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CollectionScopeEntryResponseDto {

  private String id;
  private DefinitionType definitionType;
  private String definitionKey;
  private String definitionName;
  private List<TenantDto> tenants = new ArrayList<>();

  public CollectionScopeEntryResponseDto() {}

  public String getDefinitionName() {
    return definitionName == null ? definitionKey : definitionName;
  }

  public CollectionScopeEntryResponseDto setDefinitionName(final String definitionName) {
    this.definitionName = definitionName;
    return this;
  }

  public List<String> getTenantIds() {
    return tenants.stream().map(TenantDto::getId).collect(Collectors.toList());
  }

  public static CollectionScopeEntryResponseDto from(
      final CollectionScopeEntryDto scope, final List<TenantDto> authorizedTenantDtos) {
    return new CollectionScopeEntryResponseDto()
        .setId(scope.getId())
        .setDefinitionKey(scope.getDefinitionKey())
        .setDefinitionType(scope.getDefinitionType())
        .setTenants(authorizedTenantDtos);
  }

  public String getId() {
    return id;
  }

  public CollectionScopeEntryResponseDto setId(final String id) {
    this.id = id;
    return this;
  }

  public DefinitionType getDefinitionType() {
    return definitionType;
  }

  public CollectionScopeEntryResponseDto setDefinitionType(final DefinitionType definitionType) {
    this.definitionType = definitionType;
    return this;
  }

  public String getDefinitionKey() {
    return definitionKey;
  }

  public CollectionScopeEntryResponseDto setDefinitionKey(final String definitionKey) {
    this.definitionKey = definitionKey;
    return this;
  }

  public List<TenantDto> getTenants() {
    return tenants;
  }

  public CollectionScopeEntryResponseDto setTenants(final List<TenantDto> tenants) {
    this.tenants = tenants;
    return this;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CollectionScopeEntryResponseDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $definitionType = getDefinitionType();
    result = result * PRIME + ($definitionType == null ? 43 : $definitionType.hashCode());
    final Object $definitionKey = getDefinitionKey();
    result = result * PRIME + ($definitionKey == null ? 43 : $definitionKey.hashCode());
    final Object $definitionName = getDefinitionName();
    result = result * PRIME + ($definitionName == null ? 43 : $definitionName.hashCode());
    final Object $tenants = getTenants();
    result = result * PRIME + ($tenants == null ? 43 : $tenants.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CollectionScopeEntryResponseDto)) {
      return false;
    }
    final CollectionScopeEntryResponseDto other = (CollectionScopeEntryResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$definitionType = getDefinitionType();
    final Object other$definitionType = other.getDefinitionType();
    if (this$definitionType == null
        ? other$definitionType != null
        : !this$definitionType.equals(other$definitionType)) {
      return false;
    }
    final Object this$definitionKey = getDefinitionKey();
    final Object other$definitionKey = other.getDefinitionKey();
    if (this$definitionKey == null
        ? other$definitionKey != null
        : !this$definitionKey.equals(other$definitionKey)) {
      return false;
    }
    final Object this$definitionName = getDefinitionName();
    final Object other$definitionName = other.getDefinitionName();
    if (this$definitionName == null
        ? other$definitionName != null
        : !this$definitionName.equals(other$definitionName)) {
      return false;
    }
    final Object this$tenants = getTenants();
    final Object other$tenants = other.getTenants();
    if (this$tenants == null ? other$tenants != null : !this$tenants.equals(other$tenants)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "CollectionScopeEntryResponseDto(id="
        + getId()
        + ", definitionType="
        + getDefinitionType()
        + ", definitionKey="
        + getDefinitionKey()
        + ", definitionName="
        + getDefinitionName()
        + ", tenants="
        + getTenants()
        + ")";
  }
}
