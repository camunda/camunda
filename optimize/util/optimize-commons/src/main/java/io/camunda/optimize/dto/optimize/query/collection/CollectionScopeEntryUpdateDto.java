/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.collection;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CollectionScopeEntryUpdateDto {

  private List<String> tenants = new ArrayList<>();

  public CollectionScopeEntryUpdateDto(CollectionScopeEntryDto scopeEntryDto) {
    this.tenants = scopeEntryDto.getTenants();
  }

  public CollectionScopeEntryUpdateDto(List<String> tenants) {
    this.tenants = tenants;
  }

  protected CollectionScopeEntryUpdateDto() {}
}
