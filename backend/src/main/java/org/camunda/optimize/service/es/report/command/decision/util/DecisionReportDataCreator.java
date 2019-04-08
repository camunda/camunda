/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.decision.util;

import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByMatchedRuleDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;

import static org.camunda.optimize.service.es.report.command.decision.util.DecisionGroupByDtoCreator.createGroupDecisionByEvaluationDateTime;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionGroupByDtoCreator.createGroupDecisionByInputVariable;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionGroupByDtoCreator.createGroupDecisionByNone;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionGroupByDtoCreator.createGroupDecisionByOutputVariable;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionViewDtoCreator.createCountFrequencyView;
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

  public static DecisionReportDataDto createCountFrequencyGroupByNoneReport() {
    DecisionViewDto view = createCountFrequencyView();
    DecisionGroupByDto groupByDto = createGroupDecisionByNone();

    DecisionReportDataDto reportData = new DecisionReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static DecisionReportDataDto createCountFrequencyGroupByEvaluationDateTimeReport() {
    DecisionViewDto view = createCountFrequencyView();
    DecisionGroupByDto groupByDto = createGroupDecisionByEvaluationDateTime();

    DecisionReportDataDto reportData = new DecisionReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static DecisionReportDataDto createCountFrequencyGroupByInputVariableReport() {
    DecisionViewDto view = createCountFrequencyView();
    DecisionGroupByDto groupByDto = createGroupDecisionByInputVariable();

    DecisionReportDataDto reportData = new DecisionReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static DecisionReportDataDto createCountFrequencyGroupByOutputVariableReport() {
    DecisionViewDto view = createCountFrequencyView();
    DecisionGroupByDto groupByDto = createGroupDecisionByOutputVariable();

    DecisionReportDataDto reportData = new DecisionReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static DecisionReportDataDto createCountFrequencyGroupByMatchedRuleReport() {
    DecisionViewDto view = createCountFrequencyView();
    DecisionGroupByDto groupByDto = new DecisionGroupByMatchedRuleDto();

    DecisionReportDataDto reportData = new DecisionReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }
}
