package org.camunda.optimize.service.es.report.command.util;

import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.view.DecisionViewDto;

import static org.camunda.optimize.service.es.report.command.util.DecisionGroupByDtoCreator.createGroupDecisionByNone;
import static org.camunda.optimize.service.es.report.command.util.DecisionViewDtoCreator.createCountDecisionInstanceFrequencyView;
import static org.camunda.optimize.service.es.report.command.util.DecisionViewDtoCreator.createDecisionRawDataView;

public class DecisionReportDataCreator {

  public static DecisionReportDataDto createRawDecisionDataReport() {
    DecisionViewDto view = createDecisionRawDataView();
    DecisionGroupByDto groupByDto = createGroupDecisionByNone();

    DecisionReportDataDto reportData = new DecisionReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static DecisionReportDataDto createCountDecisionInstanceFrequencyGroupByNoneReport() {
    DecisionViewDto view = createCountDecisionInstanceFrequencyView();
    DecisionGroupByDto groupByDto = createGroupDecisionByNone();

    DecisionReportDataDto reportData = new DecisionReportDataDto();
    reportData.setView(view);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

}
