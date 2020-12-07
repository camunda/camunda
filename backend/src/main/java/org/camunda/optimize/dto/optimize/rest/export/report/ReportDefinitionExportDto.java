/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.export.report;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;

import javax.validation.constraints.NotNull;

@AllArgsConstructor
@NoArgsConstructor
@FieldNameConstants
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class ReportDefinitionExportDto extends OptimizeEntityExportDto {
  private int sourceIndexVersion;
  @NotNull
  private String id;
  @NotNull
  private String name;
  private String collectionId;
}
