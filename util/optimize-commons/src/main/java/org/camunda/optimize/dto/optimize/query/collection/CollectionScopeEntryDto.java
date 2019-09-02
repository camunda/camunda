/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.collection;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.DefinitionType;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldNameConstants(asEnum = true)
public class CollectionScopeEntryDto {
  private static final String ID_SEGMENT_SEPARATOR = ":";

  @Setter(value = AccessLevel.PROTECTED)
  private String id;
  private DefinitionType definitionType;
  private String definitionKey;
  private List<String> tenants = new ArrayList<>();
  private List<String> versions = new ArrayList<>();

  public CollectionScopeEntryDto(final String id) {
    this(DefinitionType.valueOf(id.split(ID_SEGMENT_SEPARATOR)[0].toUpperCase()), id.split(ID_SEGMENT_SEPARATOR)[1]);
  }

  public CollectionScopeEntryDto(final DefinitionType definitionType, final String definitionKey) {
    this(definitionType, definitionKey, new ArrayList<>(), new ArrayList<>());
  }

  public CollectionScopeEntryDto(final DefinitionType definitionType, final String definitionKey,
                                 final List<String> tenants, final List<String> versions) {
    this.id = definitionType.getId() + ID_SEGMENT_SEPARATOR + definitionKey;
    this.definitionType = definitionType;
    this.definitionKey = definitionKey;
    this.tenants = tenants;
    this.versions = versions;
  }

}

