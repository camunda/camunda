/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.definition;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.dto.optimize.TenantDto;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DefinitionResponseDto extends SimpleDefinitionDto {
  @NonNull
  private List<TenantDto> tenants;

  public DefinitionResponseDto(@NonNull final String key,
                               final String name,
                               @NonNull final DefinitionType type,
                               final Boolean isEventProcess,
                               @NonNull final List<TenantDto> tenants,
                               @NonNull final String engine) {
    super(key, name, type, isEventProcess, Collections.singleton(engine));
    this.tenants = tenants;
  }

  public DefinitionResponseDto(@NonNull final String key,
                               final String name,
                               @NonNull final DefinitionType type,
                               final Boolean isEventProcess,
                               @NonNull final List<TenantDto> tenants,
                               @NonNull final Set<String> engines) {
    super(key, name, type, isEventProcess, engines);
    this.tenants = tenants;
  }

  public DefinitionResponseDto(@NonNull final String key,
                               final String name,
                               @NonNull final DefinitionType type,
                               @NonNull final List<TenantDto> tenants,
                               @NonNull final String engine) {
    super(key, name, type, false, Collections.singleton(engine));
    this.tenants = tenants;
  }

  public static DefinitionResponseDto from(
    final DefinitionWithTenantIdsDto definitionWithTenantIdsDto,
    final List<TenantDto> authorizedTenants) {
    return new DefinitionResponseDto(
      definitionWithTenantIdsDto.getKey(),
      definitionWithTenantIdsDto.getName(),
      definitionWithTenantIdsDto.getType(),
      definitionWithTenantIdsDto.getIsEventProcess(),
      authorizedTenants,
      definitionWithTenantIdsDto.getEngines()
    );
  }
}
