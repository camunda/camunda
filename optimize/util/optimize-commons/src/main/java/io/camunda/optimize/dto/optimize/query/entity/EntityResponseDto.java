/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.entity;

import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.dto.optimize.RoleType;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

  public EntityResponseDto(
      final String id,
      final String name,
      final String description,
      final OffsetDateTime lastModified,
      final OffsetDateTime created,
      final String owner,
      final String lastModifier,
      final EntityType entityType,
      final EntityData data,
      final RoleType currentUserRole) {
    this(
        id,
        name,
        description,
        lastModified,
        created,
        owner,
        lastModifier,
        entityType,
        data,
        null,
        null,
        currentUserRole);
  }

  public EntityResponseDto(
      final String id,
      final String name,
      final String description,
      final OffsetDateTime lastModified,
      final OffsetDateTime created,
      final String owner,
      final String lastModifier,
      final EntityType entityType,
      final Boolean combined,
      final ReportType reportType,
      final RoleType currentUserRole) {
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
        currentUserRole);
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String name = "name";
    public static final String description = "description";
    public static final String lastModified = "lastModified";
    public static final String created = "created";
    public static final String owner = "owner";
    public static final String lastModifier = "lastModifier";
    public static final String entityType = "entityType";
    public static final String data = "data";
    public static final String combined = "combined";
    public static final String reportType = "reportType";
    public static final String currentUserRole = "currentUserRole";
  }
}
