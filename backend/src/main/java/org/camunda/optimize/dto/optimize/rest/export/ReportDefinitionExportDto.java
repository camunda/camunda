/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.export;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.ReportType;

import javax.validation.constraints.NotNull;

@AllArgsConstructor
@NoArgsConstructor
@Data
@FieldNameConstants
public abstract class ReportDefinitionExportDto {
  private int sourceIndexVersion;
  @NotNull
  private ReportType reportType;
  @NotNull
  private String id;
  @NotNull
  private String name;
  private String collectionId;
  private boolean combined;
}
