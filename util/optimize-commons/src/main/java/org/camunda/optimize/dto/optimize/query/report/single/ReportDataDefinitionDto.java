/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.ReportConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@AllArgsConstructor
@Builder
@Data
@FieldNameConstants
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportDataDefinitionDto {
  private String key;
  private String name;
  private String displayName;
  @Builder.Default
  private List<String> versions = new ArrayList<>();
  @Builder.Default
  private List<String> tenantIds = ReportConstants.DEFAULT_TENANT_IDS;

  @JsonIgnore
  public void setVersion(final String version) {
    this.versions = Arrays.asList(version);
  }
}
