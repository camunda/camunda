/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.collection;

import lombok.Data;
import lombok.experimental.Accessors;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.TenantDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Accessors(chain = true)
public class CollectionScopeEntryResponseDto {

  private String id;
  private DefinitionType definitionType;
  private String definitionKey;
  private String definitionName;
  private List<TenantDto> tenants = new ArrayList<>();

  public String getDefinitionName() {
    return definitionName == null ? definitionKey : definitionName;
  }

  public List<String> getTenantIds() {
    return tenants.stream().map(TenantDto::getId).collect(Collectors.toList());
  }

  public static CollectionScopeEntryResponseDto from(CollectionScopeEntryDto scope,
                                                     List<TenantDto> authorizedTenantDtos) {
    return new CollectionScopeEntryResponseDto()
      .setId(scope.getId())
      .setDefinitionKey(scope.getDefinitionKey())
      .setDefinitionType(scope.getDefinitionType())
      .setTenants(authorizedTenantDtos);
  }
}

