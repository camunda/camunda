/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.collection;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class CollectionScopeEntryUpdateDto {
  private List<String> tenants = new ArrayList<>();
  private List<String> versions;

  public CollectionScopeEntryUpdateDto(CollectionScopeEntryDto scopeEntryDto) {
    this.tenants = scopeEntryDto.getTenants();
    this.versions = scopeEntryDto.getVersions();
  }
}
