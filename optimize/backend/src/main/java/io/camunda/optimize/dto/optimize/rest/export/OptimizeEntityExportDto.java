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

  public OptimizeEntityExportDto(
      @NotNull final String id,
      @NotNull final ExportEntityType exportEntityType,
      @NotNull final String name,
      final String description,
      final int sourceIndexVersion) {
    this.id = id;
    this.exportEntityType = exportEntityType;
    this.name = name;
    this.description = description;
    this.sourceIndexVersion = sourceIndexVersion;
  }

  public OptimizeEntityExportDto() {}

  public @NotNull String getId() {
    return id;
  }

  public void setId(@NotNull final String id) {
    this.id = id;
  }

  public @NotNull ExportEntityType getExportEntityType() {
    return exportEntityType;
  }

  public void setExportEntityType(@NotNull final ExportEntityType exportEntityType) {
    this.exportEntityType = exportEntityType;
  }

  public @NotNull String getName() {
    return name;
  }

  public void setName(@NotNull final String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String description) {
    this.description = description;
  }

  public int getSourceIndexVersion() {
    return sourceIndexVersion;
  }

  public void setSourceIndexVersion(final int sourceIndexVersion) {
    this.sourceIndexVersion = sourceIndexVersion;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof OptimizeEntityExportDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "OptimizeEntityExportDto(id="
        + getId()
        + ", exportEntityType="
        + getExportEntityType()
        + ", name="
        + getName()
        + ", description="
        + getDescription()
        + ", sourceIndexVersion="
        + getSourceIndexVersion()
        + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String id = "id";
    public static final String exportEntityType = "exportEntityType";
    public static final String name = "name";
    public static final String description = "description";
    public static final String sourceIndexVersion = "sourceIndexVersion";
  }
}
