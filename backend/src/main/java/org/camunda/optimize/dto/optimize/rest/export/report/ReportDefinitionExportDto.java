/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.export.report;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.rest.export.ExportEntityType;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;

@NoArgsConstructor
@FieldNameConstants
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class ReportDefinitionExportDto extends OptimizeEntityExportDto {
  private String collectionId;

  protected ReportDefinitionExportDto(final String id, final ExportEntityType exportEntityType,
                                      final int sourceIndexVersion, final String name,
                                      final String collectionId) {
    super(id, exportEntityType, name, sourceIndexVersion);
    this.collectionId = collectionId;
  }
}
