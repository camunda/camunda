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

  public List<String> getTenantIds() {
    return tenants.stream().map(TenantDto::getId).collect(Collectors.toList());
  }

  public static CollectionScopeEntryResponseDto from(
      final CollectionScopeEntryDto scope, final List<TenantDto> authorizedTenantDtos) {
    final CollectionScopeEntryResponseDto collectionScopeEntryResponseDto =
        new CollectionScopeEntryResponseDto();
    collectionScopeEntryResponseDto.setId(scope.getId());
    collectionScopeEntryResponseDto.setDefinitionKey(scope.getDefinitionKey());
    collectionScopeEntryResponseDto.setDefinitionType(scope.getDefinitionType());
    collectionScopeEntryResponseDto.setTenants(authorizedTenantDtos);
    return collectionScopeEntryResponseDto;
  }

  public String getId() {
    return this.id;
  }

  public DefinitionType getDefinitionType() {
    return this.definitionType;
  }

  public String getDefinitionKey() {
    return this.definitionKey;
  }

  public List<TenantDto> getTenants() {
    return this.tenants;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setDefinitionType(DefinitionType definitionType) {
    this.definitionType = definitionType;
  }

  public void setDefinitionKey(String definitionKey) {
    this.definitionKey = definitionKey;
  }

  public void setDefinitionName(String definitionName) {
    this.definitionName = definitionName;
  }

  public void setTenants(List<TenantDto> tenants) {
    this.tenants = tenants;
  }

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
    final Object this$id = this.getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$definitionType = this.getDefinitionType();
    final Object other$definitionType = other.getDefinitionType();
    if (this$definitionType == null
        ? other$definitionType != null
        : !this$definitionType.equals(other$definitionType)) {
      return false;
    }
    final Object this$definitionKey = this.getDefinitionKey();
    final Object other$definitionKey = other.getDefinitionKey();
    if (this$definitionKey == null
        ? other$definitionKey != null
        : !this$definitionKey.equals(other$definitionKey)) {
      return false;
    }
    final Object this$definitionName = this.getDefinitionName();
    final Object other$definitionName = other.getDefinitionName();
    if (this$definitionName == null
        ? other$definitionName != null
        : !this$definitionName.equals(other$definitionName)) {
      return false;
    }
    final Object this$tenants = this.getTenants();
    final Object other$tenants = other.getTenants();
    if (this$tenants == null ? other$tenants != null : !this$tenants.equals(other$tenants)) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CollectionScopeEntryResponseDto;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = this.getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $definitionType = this.getDefinitionType();
    result = result * PRIME + ($definitionType == null ? 43 : $definitionType.hashCode());
    final Object $definitionKey = this.getDefinitionKey();
    result = result * PRIME + ($definitionKey == null ? 43 : $definitionKey.hashCode());
    final Object $definitionName = this.getDefinitionName();
    result = result * PRIME + ($definitionName == null ? 43 : $definitionName.hashCode());
    final Object $tenants = this.getTenants();
    result = result * PRIME + ($tenants == null ? 43 : $tenants.hashCode());
    return result;
  }

  public String toString() {
    return "CollectionScopeEntryResponseDto(id="
        + this.getId()
        + ", definitionType="
        + this.getDefinitionType()
        + ", definitionKey="
        + this.getDefinitionKey()
        + ", definitionName="
        + this.getDefinitionName()
        + ", tenants="
        + this.getTenants()
        + ")";
  }
}
