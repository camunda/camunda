/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.collection;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.persistence.TenantDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
@NoArgsConstructor
@FieldNameConstants(asEnum = true)
public class CollectionScopeEntryDto {
  private String id;
  private String definitionType;
  private String definitionKey;
  private List<String> tenants = new ArrayList<>();
  private List<String> versions = new ArrayList<>();

  public CollectionScopeEntryDto(String id) {
    this.definitionType = id.split(":")[0].toLowerCase();
    this.definitionKey = id.split(":")[1];
  }

  public String getId() {
    return Optional.ofNullable(id).orElse(definitionType + ":" + definitionKey);
  }
}

