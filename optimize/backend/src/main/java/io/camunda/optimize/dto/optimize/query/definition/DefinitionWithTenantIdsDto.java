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
import io.camunda.optimize.service.util.TenantListHandlingUtil;
import java.util.List;
import java.util.Set;

public class DefinitionWithTenantIdsDto extends SimpleDefinitionDto {

  private List<String> tenantIds;

  public DefinitionWithTenantIdsDto(
      final String key,
      final String name,
      final DefinitionType type,
      final List<String> tenantIds,
      final Set<String> engines) {
    super(key, name, type, engines);
    if (key == null) {
      throw new IllegalArgumentException("Key cannot be null");
    }
    if (type == null) {
      throw new IllegalArgumentException("Type cannot be null");
    }
    if (tenantIds == null) {
      throw new IllegalArgumentException("TenantIds cannot be null");
    }
    if (engines == null) {
      throw new IllegalArgumentException("Engines cannot be null");
    }

    this.tenantIds = tenantIds;
  }

  public DefinitionWithTenantIdsDto(final List<String> tenantIds) {
    this.tenantIds = tenantIds;
  }

  protected DefinitionWithTenantIdsDto() {}

  public List<String> getTenantIds() {
    return TenantListHandlingUtil.sortAndReturnTenantIdList(tenantIds);
  }

  public void setTenantIds(final List<String> tenantIds) {
    if (tenantIds == null) {
      throw new IllegalArgumentException("TenantIds cannot be null");
    }

    this.tenantIds = tenantIds;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof DefinitionWithTenantIdsDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "DefinitionWithTenantIdsDto(tenantIds=" + getTenantIds() + ")";
  }
}
