/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.collection;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
@Accessors(chain = true)
public class CollectionScopeEntryRestDto {

  private DefinitionType definitionType;
  private String definitionKey;
  private String definitionName;
  private List<TenantDto> tenants = new ArrayList<>();

  public String getDefinitionName() {
    return definitionName == null? definitionKey : definitionName;
  }
}

