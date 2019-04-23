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
  /**
   * The end event the branch analysis is referred to.
   */
  protected String end;

  /**
   * The gateway the branch analysis is referred to.
   */
  protected String gateway;
  protected String processDefinitionKey;
  protected String processDefinitionVersion;

  protected List<ProcessFilterDto> filter = new ArrayList<>();
}
