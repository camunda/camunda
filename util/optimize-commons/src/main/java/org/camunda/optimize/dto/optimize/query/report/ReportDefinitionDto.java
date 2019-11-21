/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ReportType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;

import java.time.OffsetDateTime;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
public class ReportDefinitionDto<RD extends ReportDataDto> implements CollectionEntity {

  protected String id;
  protected String name;
  protected OffsetDateTime lastModified;
  protected OffsetDateTime created;
  protected String owner;
  protected String lastModifier;
  protected String collectionId;

  private RD data;

  private final Boolean combined;

  private final ReportType reportType;

  protected ReportDefinitionDto(RD data, Boolean combined, ReportType reportType) {
    this.data = data;
    this.combined = combined;
    this.reportType = reportType;
  }

  @Override
  public EntityDto toEntityDto() {
    return new EntityDto(
      getId(),
      getName(),
      getLastModified(),
      getCreated(),
      getOwner(),
      getLastModifier(),
      EntityType.REPORT,
      this.combined,
      this.reportType,
      // defaults to EDITOR, any authorization specific values have to be applied in responsible service layer
      RoleType.EDITOR
    );
  }

  @JsonIgnore
  public DefinitionType getDefinitionType() {
    return this.reportType.toDefinitionType();
  }

}
