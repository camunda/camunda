/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.export;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.ReportType;

import java.time.OffsetDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public abstract class ReportDefinitionExportDto {
  private int sourceIndexVersion;
  private ReportType reportType;
  private String id;
  private String name;
  private OffsetDateTime created;
  private String collectionId;
  private boolean combined;
}
