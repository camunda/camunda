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
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DefinitionResponseDto extends SimpleDefinitionDto {
  @NonNull private List<TenantDto> tenants;

  public DefinitionResponseDto(
      @NonNull final String key,
      final String name,
      @NonNull final DefinitionType type,
      @NonNull final List<TenantDto> tenants,
      @NonNull final String engine) {
    super(key, name, type, Collections.singleton(engine));
    this.tenants = tenants;
  }

  public DefinitionResponseDto(
      @NonNull final String key,
      final String name,
      @NonNull final DefinitionType type,
      @NonNull final List<TenantDto> tenants,
      @NonNull final Set<String> engines) {
    super(key, name, type, engines);
    this.tenants = tenants;
  }

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
}
