/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
import org.camunda.optimize.dto.optimize.rest.TenantRestDto;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DefinitionWithTenantsDto extends SimpleDefinitionDto {
  @NonNull
  private List<TenantRestDto> tenants;

  public DefinitionWithTenantsDto(@NonNull final String key,
                                  final String name,
                                  @NonNull final DefinitionType type,
                                  final Boolean isEventProcess,
                                  @NonNull final List<TenantRestDto> tenants) {
    super(key, name, type, isEventProcess);
    this.tenants = tenants;
  }

  public DefinitionWithTenantsDto(@NonNull final String key,
                                  final String name,
                                  @NonNull final DefinitionType type,
                                  @NonNull final List<TenantRestDto> tenants) {
    super(key, name, type, false);
    this.tenants = tenants;
  }
}
