/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single;

import static io.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.ReportConstants;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Data
@NoArgsConstructor
public class ReportDataDefinitionDto {

  @NotEmpty private String identifier = UUID.randomUUID().toString();
  private String key;
  private String name;
  private String displayName;
  private List<String> versions = List.of(ALL_VERSIONS);
  private List<String> tenantIds = ReportConstants.DEFAULT_TENANT_IDS;

  public ReportDataDefinitionDto(final String key) {
    this.key = key;
  }

  public ReportDataDefinitionDto(final String key, final List<String> tenantIds) {
    this.key = key;
    this.tenantIds = tenantIds;
  }

  public ReportDataDefinitionDto(
      final String key,
      final String name,
      final List<String> versions,
      final List<String> tenantIds) {
    this.key = key;
    this.name = name;
    this.versions = versions;
    this.tenantIds = tenantIds;
  }

  public ReportDataDefinitionDto(
      final String key,
      final String name,
      final List<String> versions,
      final List<String> tenantIds,
      final String displayName) {
    this.key = key;
    this.name = name;
    this.versions = versions;
    this.tenantIds = tenantIds;
    this.displayName = displayName;
  }

  public ReportDataDefinitionDto(
      final String key, final List<String> versions, final List<String> tenantIds) {
    this.key = key;
    this.versions = versions;
    this.tenantIds = tenantIds;
  }

  public ReportDataDefinitionDto(final String identifier, final String key) {
    this.identifier = identifier;
    this.key = key;
  }

  public ReportDataDefinitionDto(
      final String identifier, final String key, final String displayName) {
    this.identifier = identifier;
    this.key = key;
    this.displayName = displayName;
  }

  public ReportDataDefinitionDto(
      final String identifier, final String key, final List<String> versions) {
    this.identifier = identifier;
    this.key = key;
    this.versions = versions;
  }

  @JsonIgnore
  public void setVersion(final String version) {
    versions = List.of(version);
  }

  public static final class Fields {

    public static final String identifier = "identifier";
    public static final String key = "key";
    public static final String name = "name";
    public static final String displayName = "displayName";
    public static final String versions = "versions";
    public static final String tenantIds = "tenantIds";
  }
}
