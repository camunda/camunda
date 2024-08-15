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
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $exportEntityType = getExportEntityType();
    result = result * PRIME + ($exportEntityType == null ? 43 : $exportEntityType.hashCode());
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    final Object $description = getDescription();
    result = result * PRIME + ($description == null ? 43 : $description.hashCode());
    result = result * PRIME + getSourceIndexVersion();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof OptimizeEntityExportDto)) {
      return false;
    }
    final OptimizeEntityExportDto other = (OptimizeEntityExportDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$exportEntityType = getExportEntityType();
    final Object other$exportEntityType = other.getExportEntityType();
    if (this$exportEntityType == null
        ? other$exportEntityType != null
        : !this$exportEntityType.equals(other$exportEntityType)) {
      return false;
    }
    final Object this$name = getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    final Object this$description = getDescription();
    final Object other$description = other.getDescription();
    if (this$description == null
        ? other$description != null
        : !this$description.equals(other$description)) {
      return false;
    }
    if (getSourceIndexVersion() != other.getSourceIndexVersion()) {
      return false;
    }
    return true;
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

  public static final class Fields {

    public static final String id = "id";
    public static final String exportEntityType = "exportEntityType";
    public static final String name = "name";
    public static final String description = "description";
    public static final String sourceIndexVersion = "sourceIndexVersion";
  }
}
