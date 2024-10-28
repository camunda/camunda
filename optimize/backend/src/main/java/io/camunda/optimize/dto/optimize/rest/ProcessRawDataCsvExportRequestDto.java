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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
  private static List<String> defaultProcessDefinitionVersions() {
    return new ArrayList<>();
  }

  @NotNull
  private static List<String> defaultTenantIds() {
    return Collections.singletonList(null);
  }

  @NotNull
  private static List<ProcessFilterDto<?>> defaultFilter() {
    return new ArrayList<>();
  }

  @NotEmpty
  private static List<String> defaultIncludedColumns() {
    return new ArrayList<>();
  }

  public static ProcessRawDataCsvExportRequestDtoBuilder builder() {
    return new ProcessRawDataCsvExportRequestDtoBuilder();
  }

  public static class ProcessRawDataCsvExportRequestDtoBuilder {

    private @NotNull String processDefinitionKey;
    private @NotEmpty List<String> processDefinitionVersionsValue;
    private boolean processDefinitionVersionsSet;
    private @NotNull List<String> tenantIdsValue;
    private boolean tenantIdsSet;
    private @NotNull List<ProcessFilterDto<?>> filterValue;
    private boolean filterSet;
    private @NotEmpty List<String> includedColumnsValue;
    private boolean includedColumnsSet;

    ProcessRawDataCsvExportRequestDtoBuilder() {}

    public ProcessRawDataCsvExportRequestDtoBuilder processDefinitionKey(
        @NotNull final String processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public ProcessRawDataCsvExportRequestDtoBuilder processDefinitionVersions(
        @NotEmpty final List<String> processDefinitionVersions) {
      processDefinitionVersionsValue = processDefinitionVersions;
      processDefinitionVersionsSet = true;
      return this;
    }

    public ProcessRawDataCsvExportRequestDtoBuilder tenantIds(
        @NotNull final List<String> tenantIds) {
      tenantIdsValue = tenantIds;
      tenantIdsSet = true;
      return this;
    }

    public ProcessRawDataCsvExportRequestDtoBuilder filter(
        @NotNull final List<ProcessFilterDto<?>> filter) {
      filterValue = filter;
      filterSet = true;
      return this;
    }

    public ProcessRawDataCsvExportRequestDtoBuilder includedColumns(
        @NotEmpty final List<String> includedColumns) {
      includedColumnsValue = includedColumns;
      includedColumnsSet = true;
      return this;
    }

    public ProcessRawDataCsvExportRequestDto build() {
      List<String> processDefinitionVersionsValue = this.processDefinitionVersionsValue;
      if (!processDefinitionVersionsSet) {
        processDefinitionVersionsValue =
            ProcessRawDataCsvExportRequestDto.defaultProcessDefinitionVersions();
      }
      List<String> tenantIdsValue = this.tenantIdsValue;
      if (!tenantIdsSet) {
        tenantIdsValue = ProcessRawDataCsvExportRequestDto.defaultTenantIds();
      }
      List<ProcessFilterDto<?>> filterValue = this.filterValue;
      if (!filterSet) {
        filterValue = ProcessRawDataCsvExportRequestDto.defaultFilter();
      }
      List<String> includedColumnsValue = this.includedColumnsValue;
      if (!includedColumnsSet) {
        includedColumnsValue = ProcessRawDataCsvExportRequestDto.defaultIncludedColumns();
      }
      return new ProcessRawDataCsvExportRequestDto(
          processDefinitionKey,
          processDefinitionVersionsValue,
          tenantIdsValue,
          filterValue,
          includedColumnsValue);
    }

    @Override
    public String toString() {
      return "ProcessRawDataCsvExportRequestDto.ProcessRawDataCsvExportRequestDtoBuilder(processDefinitionKey="
          + processDefinitionKey
          + ", processDefinitionVersionsValue="
          + processDefinitionVersionsValue
          + ", tenantIdsValue="
          + tenantIdsValue
          + ", filterValue="
          + filterValue
          + ", includedColumnsValue="
          + includedColumnsValue
          + ")";
    }
  }
}
