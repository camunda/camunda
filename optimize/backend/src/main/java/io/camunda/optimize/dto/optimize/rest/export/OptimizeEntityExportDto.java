/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.export;

import static io.camunda.optimize.dto.optimize.rest.export.ExportConstants.COMBINED_REPORT;
import static io.camunda.optimize.dto.optimize.rest.export.ExportConstants.DASHBOARD;
import static io.camunda.optimize.dto.optimize.rest.export.ExportConstants.SINGLE_DECISION_REPORT_STRING;
import static io.camunda.optimize.dto.optimize.rest.export.ExportConstants.SINGLE_PROCESS_REPORT_STRING;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.optimize.dto.optimize.rest.export.dashboard.DashboardDefinitionExportDto;
import io.camunda.optimize.dto.optimize.rest.export.report.CombinedProcessReportDefinitionExportDto;
import io.camunda.optimize.dto.optimize.rest.export.report.SingleDecisionReportDefinitionExportDto;
import io.camunda.optimize.dto.optimize.rest.export.report.SingleProcessReportDefinitionExportDto;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "exportEntityType",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = SingleProcessReportDefinitionExportDto.class,
      name = SINGLE_PROCESS_REPORT_STRING),
  @JsonSubTypes.Type(
      value = SingleDecisionReportDefinitionExportDto.class,
      name = SINGLE_DECISION_REPORT_STRING),
  @JsonSubTypes.Type(
      value = CombinedProcessReportDefinitionExportDto.class,
      name = COMBINED_REPORT),
  @JsonSubTypes.Type(value = DashboardDefinitionExportDto.class, name = DASHBOARD),
})
public abstract class OptimizeEntityExportDto {

  @NotNull private String id;
  @NotNull private ExportEntityType exportEntityType;
  @NotNull private String name;
  private String description;
  private int sourceIndexVersion;

  public static final class Fields {

    public static final String id = "id";
    public static final String exportEntityType = "exportEntityType";
    public static final String name = "name";
    public static final String description = "description";
    public static final String sourceIndexVersion = "sourceIndexVersion";
  }
}
