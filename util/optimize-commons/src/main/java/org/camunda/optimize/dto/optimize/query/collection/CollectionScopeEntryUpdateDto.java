/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.collection;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CollectionScopeEntryUpdateDto {
  private List<String> tenants = new ArrayList<>();

  public CollectionScopeEntryUpdateDto(CollectionScopeEntryDto scopeEntryDto) {
    this.tenants = scopeEntryDto.getTenants();
  }
}
