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
import java.util.Objects;

public class CollectionScopeEntryUpdateDto {

  private List<String> tenants = new ArrayList<>();

  public CollectionScopeEntryUpdateDto(final CollectionScopeEntryDto scopeEntryDto) {
    tenants = scopeEntryDto.getTenants();
  }

  public CollectionScopeEntryUpdateDto(final List<String> tenants) {
    this.tenants = tenants;
  }

  protected CollectionScopeEntryUpdateDto() {}

  public List<String> getTenants() {
    return tenants;
  }

  public void setTenants(final List<String> tenants) {
    this.tenants = tenants;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CollectionScopeEntryUpdateDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(tenants);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CollectionScopeEntryUpdateDto that = (CollectionScopeEntryUpdateDto) o;
    return Objects.equals(tenants, that.tenants);
  }

  @Override
  public String toString() {
    return "CollectionScopeEntryUpdateDto(tenants=" + getTenants() + ")";
  }
}
