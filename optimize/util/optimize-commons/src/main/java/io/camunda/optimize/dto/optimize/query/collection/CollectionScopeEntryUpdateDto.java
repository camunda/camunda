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
    final int PRIME = 59;
    int result = 1;
    final Object $tenants = getTenants();
    result = result * PRIME + ($tenants == null ? 43 : $tenants.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CollectionScopeEntryUpdateDto)) {
      return false;
    }
    final CollectionScopeEntryUpdateDto other = (CollectionScopeEntryUpdateDto) o;
    if (!other.canEqual((Object) this)) {
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
    return "CollectionScopeEntryUpdateDto(tenants=" + getTenants() + ")";
  }
}
