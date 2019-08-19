/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report;

import lombok.Data;
import org.camunda.optimize.dto.optimize.query.collection.CollectionEntity;

import java.time.OffsetDateTime;

@Data
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

}
