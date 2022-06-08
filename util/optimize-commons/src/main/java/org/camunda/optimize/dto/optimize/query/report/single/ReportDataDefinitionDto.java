/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.ReportConstants;

import javax.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;

@AllArgsConstructor
@Data
@FieldNameConstants
@NoArgsConstructor
public class ReportDataDefinitionDto {
  @NotEmpty
  private String identifier = UUID.randomUUID().toString();
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

  public ReportDataDefinitionDto(final String key, final String name, final List<String> versions,
                                 final List<String> tenantIds) {
    this.key = key;
    this.name = name;
    this.versions = versions;
    this.tenantIds = tenantIds;
  }

  public ReportDataDefinitionDto(final String key, final String name, final List<String> versions,
                                 final List<String> tenantIds, final String displayName) {
    this.key = key;
    this.name = name;
    this.versions = versions;
    this.tenantIds = tenantIds;
    this.displayName = displayName;
  }

  public ReportDataDefinitionDto(final String key, final List<String> versions, final List<String> tenantIds) {
    this.key = key;
    this.versions = versions;
    this.tenantIds = tenantIds;
  }

  public ReportDataDefinitionDto(final String identifier, final String key) {
    this.identifier = identifier;
    this.key = key;
  }

  public ReportDataDefinitionDto(final String identifier, final String key, final String displayName) {
    this.identifier = identifier;
    this.key = key;
    this.displayName = displayName;
  }

  public ReportDataDefinitionDto(final String identifier, final String key, final List<String> versions) {
    this.identifier = identifier;
    this.key = key;
    this.versions = versions;
  }

  @JsonIgnore
  public void setVersion(final String version) {
    this.versions = List.of(version);
  }
}
