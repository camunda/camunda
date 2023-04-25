/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.export;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.rest.export.dashboard.DashboardDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.CombinedProcessReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleDecisionReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;

import javax.validation.constraints.NotNull;

import static org.camunda.optimize.dto.optimize.rest.export.ExportConstants.COMBINED_REPORT;
import static org.camunda.optimize.dto.optimize.rest.export.ExportConstants.DASHBOARD;
import static org.camunda.optimize.dto.optimize.rest.export.ExportConstants.SINGLE_DECISION_REPORT_STRING;
import static org.camunda.optimize.dto.optimize.rest.export.ExportConstants.SINGLE_PROCESS_REPORT_STRING;

@AllArgsConstructor
@NoArgsConstructor
@FieldNameConstants
@Data
@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "exportEntityType", visible = true
)
@JsonSubTypes({
  @JsonSubTypes.Type(value = SingleProcessReportDefinitionExportDto.class, name = SINGLE_PROCESS_REPORT_STRING),
  @JsonSubTypes.Type(value = SingleDecisionReportDefinitionExportDto.class, name = SINGLE_DECISION_REPORT_STRING),
  @JsonSubTypes.Type(value = CombinedProcessReportDefinitionExportDto.class, name = COMBINED_REPORT),
  @JsonSubTypes.Type(value = DashboardDefinitionExportDto.class, name = DASHBOARD),
})
public abstract class OptimizeEntityExportDto {
  @NotNull
  private String id;
  @NotNull
  private ExportEntityType exportEntityType;
  @NotNull
  private String name;
  private String description;
  private int sourceIndexVersion;
}
