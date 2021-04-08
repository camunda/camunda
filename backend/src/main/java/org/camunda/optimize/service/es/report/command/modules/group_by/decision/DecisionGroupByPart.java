/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.decision;

import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;

import static org.camunda.optimize.service.util.InstanceIndexUtil.getDecisionInstanceIndexAliasName;

public abstract class DecisionGroupByPart extends GroupByPart<DecisionReportDataDto> {

  @Override
  protected String getIndexName(final ExecutionContext<DecisionReportDataDto> context) {
    return getDecisionInstanceIndexAliasName(context.getReportData().getDecisionDefinitionKey());
  }
}
