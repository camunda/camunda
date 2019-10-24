/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.decision.util;

import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;

import static org.camunda.optimize.service.es.report.command.decision.util.DecisionGroupByDtoCreator.createGroupDecisionByNone;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionViewDtoCreator.createDecisionRawDataView;

public class DecisionReportDataCreator {

  public static DecisionReportDataDto createRawDecisionDataReport() {
    DecisionViewDto view = createDecisionRawDataView();
    DecisionGroupByDto groupByDto = createGroupDecisionByNone();

    DecisionReportDataDto reportData = new DecisionReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }
}
