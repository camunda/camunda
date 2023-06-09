/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.RoleType;

import java.time.OffsetDateTime;

@FieldNameConstants
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Data
public class EntityResponseDto {
  private String id;
  private String name;
  private String description;
  private OffsetDateTime lastModified;
  private OffsetDateTime created;
  private String owner;
  private String lastModifier;
  private EntityType entityType;
  private EntityData data;
  private Boolean combined;
  private ReportType reportType;
  private RoleType currentUserRole;

  public EntityResponseDto(final String id, final String name, final String description, final OffsetDateTime lastModified,
                           final OffsetDateTime created, final String owner, final String lastModifier,
                           final EntityType entityType, final EntityData data, final RoleType currentUserRole) {
    this(id, name, description, lastModified, created, owner, lastModifier, entityType, data, null, null, currentUserRole);
  }

  public EntityResponseDto(final String id, final String name, final String description, final OffsetDateTime lastModified,
                           final OffsetDateTime created, final String owner, final String lastModifier,
                           final EntityType entityType, final Boolean combined,
                           final ReportType reportType, final RoleType currentUserRole) {
    this(
      id,
      name,
      description,
      lastModified,
      created,
      owner,
      lastModifier,
      entityType,
      new EntityData(),
      combined,
      reportType,
      currentUserRole
    );
  }
}
