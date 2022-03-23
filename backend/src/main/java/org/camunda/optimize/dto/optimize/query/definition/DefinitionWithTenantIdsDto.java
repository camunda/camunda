/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.definition;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.SimpleDefinitionDto;
import org.camunda.optimize.service.util.TenantListHandlingUtil;

import java.util.List;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Data
@EqualsAndHashCode(callSuper = true)
public class DefinitionWithTenantIdsDto extends SimpleDefinitionDto {
  @NonNull
  private List<String> tenantIds;

  public DefinitionWithTenantIdsDto(@NonNull final String key,
                                    final String name,
                                    @NonNull final DefinitionType type,
                                    final Boolean isEventProcess,
                                    @NonNull final List<String> tenantIds,
                                    @NonNull final Set<String> engines) {
    super(key, name, type, isEventProcess, engines);
    this.tenantIds = tenantIds;
  }

  public List<String> getTenantIds() {
    return TenantListHandlingUtil.sortAndReturnTenantIdList(tenantIds);
  }
}
