/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;

import java.util.List;
import java.util.Optional;

@FieldNameConstants(asEnum = true)
public abstract class SingleReportDataDto implements ReportDataDto, Combinable {

  @Getter @Setter private SingleReportConfigurationDto configuration = new SingleReportConfigurationDto();

  public abstract String getDefinitionKey();
  public abstract List<String> getDefinitionVersions();
  public abstract String getDefinitionName();
  public abstract List<String> getTenantIds();

  @JsonIgnore
  public boolean isDefinitionVersionSetToAll() {
    Optional<String> allVersionSelected = getDefinitionVersions().stream()
      .filter(ReportConstants.ALL_VERSIONS::equalsIgnoreCase)
      .findFirst();
    return allVersionSelected.isPresent();
  }

  @JsonIgnore
  public boolean hasMultipleVersionsSet() {
    return getDefinitionVersions().size() > 1;
  }
}
