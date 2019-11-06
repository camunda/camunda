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
import java.util.Optional;

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

  public CollectionScopeEntryDto(final String id) {
    this(DefinitionType.valueOf(id.split(ID_SEGMENT_SEPARATOR)[0].toUpperCase()), id.split(ID_SEGMENT_SEPARATOR)[1]);
  }

  public CollectionScopeEntryDto(CollectionScopeEntryDto oldEntry) {
    this.definitionKey = oldEntry.definitionKey;
    this.definitionType = oldEntry.definitionType;
    this.tenants = oldEntry.tenants;
    this.id = convertTypeAndKeyToScopeEntryId(this.definitionType, this.definitionKey);
  }

  public CollectionScopeEntryDto(final DefinitionType definitionType, final String definitionKey) {
    this(definitionType, definitionKey, new ArrayList<>());
  }

  public CollectionScopeEntryDto(final DefinitionType definitionType,
                                 final String definitionKey,
                                 final List<String> tenants) {
    this.id = convertTypeAndKeyToScopeEntryId(definitionType, definitionKey);
    this.definitionType = definitionType;
    this.definitionKey = definitionKey;
    this.tenants = tenants;
  }

  public String getId() {
    return Optional.ofNullable(id).orElse(convertTypeAndKeyToScopeEntryId(definitionType, definitionKey));
  }

  private String convertTypeAndKeyToScopeEntryId(final DefinitionType definitionType, final String definitionKey) {
    return definitionType.getId() + ID_SEGMENT_SEPARATOR + definitionKey;
  }
}

