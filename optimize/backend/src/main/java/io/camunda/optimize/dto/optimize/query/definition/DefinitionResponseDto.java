/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.definition;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import io.camunda.optimize.dto.optimize.TenantDto;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DefinitionResponseDto extends SimpleDefinitionDto {

  private List<TenantDto> tenants;

  public DefinitionResponseDto(
      final String key,
      final String name,
      final DefinitionType type,
      final List<TenantDto> tenants,
      final String engine) {
    super(key, name, type, Collections.singleton(engine));
    if (key == null) {
      throw new IllegalArgumentException("Key cannot be null");
    }

    if (type == null) {
      throw new IllegalArgumentException("Type cannot be null");
    }

    if (tenants == null) {
      throw new IllegalArgumentException("Tenants cannot be null");
    }

    if (engine == null) {
      throw new IllegalArgumentException("Engine cannot be null");
    }

    this.tenants = tenants;
  }

  public DefinitionResponseDto(
      final String key,
      final String name,
      final DefinitionType type,
      final List<TenantDto> tenants,
      final Set<String> engines) {
    super(key, name, type, engines);
    if (key == null) {
      throw new IllegalArgumentException("Key cannot be null");
    }

    if (type == null) {
      throw new IllegalArgumentException("Type cannot be null");
    }

    if (tenants == null) {
      throw new IllegalArgumentException("Tenants cannot be null");
    }

    if (engines == null) {
      throw new IllegalArgumentException("Engines cannot be null");
    }

    this.tenants = tenants;
  }

  protected DefinitionResponseDto() {}

  public static DefinitionResponseDto from(
      final DefinitionWithTenantIdsDto definitionWithTenantIdsDto,
      final List<TenantDto> authorizedTenants) {
    return new DefinitionResponseDto(
        definitionWithTenantIdsDto.getKey(),
        definitionWithTenantIdsDto.getName(),
        definitionWithTenantIdsDto.getType(),
        authorizedTenants,
        definitionWithTenantIdsDto.getEngines());
  }

  public List<TenantDto> getTenants() {
    return tenants;
  }

  public void setTenants(final List<TenantDto> tenants) {
    if (tenants == null) {
      throw new IllegalArgumentException("Tenants cannot be null");
    }

    this.tenants = tenants;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof DefinitionResponseDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $tenants = getTenants();
    result = result * PRIME + ($tenants == null ? 43 : $tenants.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DefinitionResponseDto)) {
      return false;
    }
    final DefinitionResponseDto other = (DefinitionResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
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
    return "DefinitionResponseDto(super=" + super.toString() + ", tenants=" + getTenants() + ")";
  }
}
