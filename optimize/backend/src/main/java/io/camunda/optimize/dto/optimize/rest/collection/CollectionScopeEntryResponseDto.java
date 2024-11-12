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

  public void setId(final String id) {
    this.id = id;
  }

  public void setDefinitionType(final DefinitionType definitionType) {
    this.definitionType = definitionType;
  }

  public void setDefinitionKey(final String definitionKey) {
    this.definitionKey = definitionKey;
  }

  public void setDefinitionName(final String definitionName) {
    this.definitionName = definitionName;
  }

  public void setTenants(final List<TenantDto> tenants) {
    this.tenants = tenants;
  }

  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CollectionScopeEntryResponseDto;
  }

  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
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
