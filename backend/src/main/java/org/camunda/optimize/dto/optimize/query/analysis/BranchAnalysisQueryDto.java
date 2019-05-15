/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.analysis;

import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;

import java.util.ArrayList;
import java.util.List;

@Data
public class BranchAnalysisQueryDto {
  private String end;
  private String gateway;
  private String processDefinitionKey;
  private String processDefinitionVersion;
  private List<String> tenantIds = new ArrayList<>();

  private List<ProcessFilterDto> filter = new ArrayList<>();
}
