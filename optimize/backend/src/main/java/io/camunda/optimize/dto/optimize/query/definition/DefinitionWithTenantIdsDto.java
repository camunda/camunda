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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

@Data
@EqualsAndHashCode(callSuper = true)
public class DefinitionWithTenantIdsDto extends SimpleDefinitionDto {

  @NonNull private List<String> tenantIds;

  public DefinitionWithTenantIdsDto(
      @NonNull final String key,
      final String name,
      @NonNull final DefinitionType type,
      @NonNull final List<String> tenantIds,
      @NonNull final Set<String> engines) {
    super(key, name, type, engines);
    this.tenantIds = tenantIds;
  }

  public DefinitionWithTenantIdsDto(@NonNull List<String> tenantIds) {
    this.tenantIds = tenantIds;
  }

  protected DefinitionWithTenantIdsDto() {}

  public List<String> getTenantIds() {
    return TenantListHandlingUtil.sortAndReturnTenantIdList(tenantIds);
  }
}
