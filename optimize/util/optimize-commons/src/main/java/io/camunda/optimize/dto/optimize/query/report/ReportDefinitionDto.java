/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ReportType;
import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import io.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityType;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
public class ReportDefinitionDto<D extends ReportDataDto> implements CollectionEntity {

  protected String id;
  protected String name;
  protected String description;
  protected OffsetDateTime lastModified;
  protected OffsetDateTime created;
  protected String owner;
  protected String lastModifier;
  protected String collectionId;

  @Valid protected D data;

  private final boolean combined;

  private final ReportType reportType;

  protected ReportDefinitionDto(final D data, final Boolean combined, final ReportType reportType) {
    this.data = data;
    this.combined = combined;
    this.reportType = reportType;
  }

  @Override
  public EntityResponseDto toEntityDto(final RoleType roleType) {
    return new EntityResponseDto(
        getId(),
        getName(),
        getDescription(),
        getLastModified(),
        getCreated(),
        getOwner(),
        getLastModifier(),
        EntityType.REPORT,
        combined,
        reportType,
        roleType);
  }

  @JsonIgnore
  public DefinitionType getDefinitionType() {
    return reportType.toDefinitionType();
  }

  public static final class Fields {

    public static final String id = "id";
    public static final String name = "name";
    public static final String description = "description";
    public static final String lastModified = "lastModified";
    public static final String created = "created";
    public static final String owner = "owner";
    public static final String lastModifier = "lastModifier";
    public static final String collectionId = "collectionId";
    public static final String data = "data";
    public static final String combined = "combined";
    public static final String reportType = "reportType";
  }
}
