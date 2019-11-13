/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.collection;

import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@NoArgsConstructor
public class SimpleCollectionDefinitionDto extends BaseCollectionDefinitionDto<CollectionDataDto> {
  
  public SimpleCollectionDefinitionDto(CollectionDataDto data, OffsetDateTime created, String id, String name,
                                       OffsetDateTime lastModified, String lastModifier, String owner) {
    super();
    this.data = data;
    this.created = created;
    this.id = id;
    this.name = name;
    this.lastModified = lastModified;
    this.lastModifier = lastModifier;
    this.owner = owner;
  }
}
