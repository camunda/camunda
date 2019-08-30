/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Data
public class EntityDto {
  private String id;
  private String name;
  private OffsetDateTime lastModified;
  private OffsetDateTime created;
  private String owner;
  private String lastModifier;
  private EntityType entityType;
  private EntityData data;

  public EntityDto(final String id, final String name, final OffsetDateTime lastModified,
                   final OffsetDateTime created, final String owner, final String lastModifier,
                   final EntityType entityType) {
    this(id, name, lastModified, created, owner, lastModifier, entityType, new EntityData());
  }
}
