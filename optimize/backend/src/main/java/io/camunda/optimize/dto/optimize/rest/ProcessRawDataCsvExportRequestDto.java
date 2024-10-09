/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProcessRawDataCsvExportRequestDto {

  @NotNull private String processDefinitionKey;
  @NotEmpty private List<String> processDefinitionVersions = new ArrayList<>();
  @NotNull private List<String> tenantIds = Collections.singletonList(null);
  @NotNull private List<ProcessFilterDto<?>> filter = new ArrayList<>();
  @NotEmpty private List<String> includedColumns = new ArrayList<>();

  public ProcessRawDataCsvExportRequestDto(
      @NotNull final String processDefinitionKey,
      @NotEmpty final List<String> processDefinitionVersions,
      @NotNull final List<String> tenantIds,
      @NotNull final List<ProcessFilterDto<?>> filter,
      @NotEmpty final List<String> includedColumns) {
    this.processDefinitionKey = processDefinitionKey;
    this.processDefinitionVersions = processDefinitionVersions;
    this.tenantIds = tenantIds;
    this.filter = filter;
    this.includedColumns = includedColumns;
  }

  protected ProcessRawDataCsvExportRequestDto() {}

  public @NotNull String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public void setProcessDefinitionKey(@NotNull final String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
  }

  public @NotEmpty List<String> getProcessDefinitionVersions() {
    return processDefinitionVersions;
  }

  public void setProcessDefinitionVersions(@NotEmpty final List<String> processDefinitionVersions) {
    this.processDefinitionVersions = processDefinitionVersions;
  }

  public @NotNull List<String> getTenantIds() {
    return tenantIds;
  }

  public void setTenantIds(@NotNull final List<String> tenantIds) {
    this.tenantIds = tenantIds;
  }

  public @NotNull List<ProcessFilterDto<?>> getFilter() {
    return filter;
  }

  public void setFilter(@NotNull final List<ProcessFilterDto<?>> filter) {
    this.filter = filter;
  }

  public @NotEmpty List<String> getIncludedColumns() {
    return includedColumns;
  }

  public void setIncludedColumns(@NotEmpty final List<String> includedColumns) {
    this.includedColumns = includedColumns;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessRawDataCsvExportRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $processDefinitionKey = getProcessDefinitionKey();
    result =
        result * PRIME + ($processDefinitionKey == null ? 43 : $processDefinitionKey.hashCode());
    final Object $processDefinitionVersions = getProcessDefinitionVersions();
    result =
        result * PRIME
            + ($processDefinitionVersions == null ? 43 : $processDefinitionVersions.hashCode());
    final Object $tenantIds = getTenantIds();
    result = result * PRIME + ($tenantIds == null ? 43 : $tenantIds.hashCode());
    final Object $filter = getFilter();
    result = result * PRIME + ($filter == null ? 43 : $filter.hashCode());
    final Object $includedColumns = getIncludedColumns();
    result = result * PRIME + ($includedColumns == null ? 43 : $includedColumns.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ProcessRawDataCsvExportRequestDto)) {
      return false;
    }
    final ProcessRawDataCsvExportRequestDto other = (ProcessRawDataCsvExportRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$processDefinitionKey = getProcessDefinitionKey();
    final Object other$processDefinitionKey = other.getProcessDefinitionKey();
    if (this$processDefinitionKey == null
        ? other$processDefinitionKey != null
        : !this$processDefinitionKey.equals(other$processDefinitionKey)) {
      return false;
    }
    final Object this$processDefinitionVersions = getProcessDefinitionVersions();
    final Object other$processDefinitionVersions = other.getProcessDefinitionVersions();
    if (this$processDefinitionVersions == null
        ? other$processDefinitionVersions != null
        : !this$processDefinitionVersions.equals(other$processDefinitionVersions)) {
      return false;
    }
    final Object this$tenantIds = getTenantIds();
    final Object other$tenantIds = other.getTenantIds();
    if (this$tenantIds == null
        ? other$tenantIds != null
        : !this$tenantIds.equals(other$tenantIds)) {
      return false;
    }
    final Object this$filter = getFilter();
    final Object other$filter = other.getFilter();
    if (this$filter == null ? other$filter != null : !this$filter.equals(other$filter)) {
      return false;
    }
    final Object this$includedColumns = getIncludedColumns();
    final Object other$includedColumns = other.getIncludedColumns();
    if (this$includedColumns == null
        ? other$includedColumns != null
        : !this$includedColumns.equals(other$includedColumns)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ProcessRawDataCsvExportRequestDto(processDefinitionKey="
        + getProcessDefinitionKey()
        + ", processDefinitionVersions="
        + getProcessDefinitionVersions()
        + ", tenantIds="
        + getTenantIds()
        + ", filter="
        + getFilter()
        + ", includedColumns="
        + getIncludedColumns()
        + ")";
  }

  @NotEmpty
  private static List<String> $default$processDefinitionVersions() {
    return new ArrayList<>();
  }

  @NotNull
  private static List<String> $default$tenantIds() {
    return Collections.singletonList(null);
  }

  @NotNull
  private static List<ProcessFilterDto<?>> $default$filter() {
    return new ArrayList<>();
  }

  @NotEmpty
  private static List<String> $default$includedColumns() {
    return new ArrayList<>();
  }

  public static ProcessRawDataCsvExportRequestDtoBuilder builder() {
    return new ProcessRawDataCsvExportRequestDtoBuilder();
  }

  public static class ProcessRawDataCsvExportRequestDtoBuilder {

    private @NotNull String processDefinitionKey;
    private @NotEmpty List<String> processDefinitionVersions$value;
    private boolean processDefinitionVersions$set;
    private @NotNull List<String> tenantIds$value;
    private boolean tenantIds$set;
    private @NotNull List<ProcessFilterDto<?>> filter$value;
    private boolean filter$set;
    private @NotEmpty List<String> includedColumns$value;
    private boolean includedColumns$set;

    ProcessRawDataCsvExportRequestDtoBuilder() {}

    public ProcessRawDataCsvExportRequestDtoBuilder processDefinitionKey(
        @NotNull final String processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public ProcessRawDataCsvExportRequestDtoBuilder processDefinitionVersions(
        @NotEmpty final List<String> processDefinitionVersions) {
      processDefinitionVersions$value = processDefinitionVersions;
      processDefinitionVersions$set = true;
      return this;
    }

    public ProcessRawDataCsvExportRequestDtoBuilder tenantIds(
        @NotNull final List<String> tenantIds) {
      tenantIds$value = tenantIds;
      tenantIds$set = true;
      return this;
    }

    public ProcessRawDataCsvExportRequestDtoBuilder filter(
        @NotNull final List<ProcessFilterDto<?>> filter) {
      filter$value = filter;
      filter$set = true;
      return this;
    }

    public ProcessRawDataCsvExportRequestDtoBuilder includedColumns(
        @NotEmpty final List<String> includedColumns) {
      includedColumns$value = includedColumns;
      includedColumns$set = true;
      return this;
    }

    public ProcessRawDataCsvExportRequestDto build() {
      List<String> processDefinitionVersions$value = this.processDefinitionVersions$value;
      if (!processDefinitionVersions$set) {
        processDefinitionVersions$value =
            ProcessRawDataCsvExportRequestDto.$default$processDefinitionVersions();
      }
      List<String> tenantIds$value = this.tenantIds$value;
      if (!tenantIds$set) {
        tenantIds$value = ProcessRawDataCsvExportRequestDto.$default$tenantIds();
      }
      List<ProcessFilterDto<?>> filter$value = this.filter$value;
      if (!filter$set) {
        filter$value = ProcessRawDataCsvExportRequestDto.$default$filter();
      }
      List<String> includedColumns$value = this.includedColumns$value;
      if (!includedColumns$set) {
        includedColumns$value = ProcessRawDataCsvExportRequestDto.$default$includedColumns();
      }
      return new ProcessRawDataCsvExportRequestDto(
          processDefinitionKey,
          processDefinitionVersions$value,
          tenantIds$value,
          filter$value,
          includedColumns$value);
    }

    @Override
    public String toString() {
      return "ProcessRawDataCsvExportRequestDto.ProcessRawDataCsvExportRequestDtoBuilder(processDefinitionKey="
          + processDefinitionKey
          + ", processDefinitionVersions$value="
          + processDefinitionVersions$value
          + ", tenantIds$value="
          + tenantIds$value
          + ", filter$value="
          + filter$value
          + ", includedColumns$value="
          + includedColumns$value
          + ")";
    }
  }
}
