/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

package org.camunda.optimize.dto.optimize.rest.definition;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.camunda.optimize.dto.optimize.rest.TenantRestDto;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
public class DefinitionVersionsWithTenantsRestDto {
  @NonNull
  private String key;
  private String name;
  @NonNull
  private List<DefinitionVersionWithTenantsRestDto> versions;
  @NonNull
  private List<TenantRestDto> allTenants;

  // to be removed with OPT-2574
  @Deprecated
  public List<TenantRestDto> getTenants() {
    return allTenants;
  }

  // to be removed with OPT-2574
  @Deprecated
  public void setTenants(final List<TenantRestDto> tenants) {
    // noop
  }
}
