/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single;

import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;

import java.util.List;

public abstract class SingleReportDataDto implements ReportDataDto, Combinable {

  @Getter @Setter protected SingleReportConfigurationDto configuration = new SingleReportConfigurationDto();

  public abstract String getDefinitionKey();
  public abstract String getDefinitionVersion();
  public abstract List<String> getTenantIds();
}
