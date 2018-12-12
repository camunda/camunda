package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;

import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.service.es.report.command.decision.util.DecisionGroupByDtoCreator.createGroupDecisionByNone;
import static org.camunda.optimize.service.es.report.command.decision.util.DecisionViewDtoCreator.createDecisionRawDataView;

public class DecisionReportDataBuilder {

  private DecisionReportDataType reportDataType;

  private String decisionDefinitionKey;
  private String decisionDefinitionVersion;

  private List<DecisionFilterDto> filter = new ArrayList<>();

  public static DecisionReportDataBuilder createReportData() {
    return new DecisionReportDataBuilder();
  }

  public DecisionReportDataDto build() {
    DecisionReportDataDto reportData;
    switch (reportDataType) {
      case RAW_DATA:
        reportData = createDecisionReportDataViewRawAsTable(decisionDefinitionKey, decisionDefinitionVersion);
        break;
      default:
        throw new IllegalStateException("Unsupported type: " + reportDataType);
    }
    reportData.setFilter(this.filter);
    return reportData;
  }

  public DecisionReportDataBuilder setReportDataType(DecisionReportDataType reportDataType) {
    this.reportDataType = reportDataType;
    return this;
  }

  public DecisionReportDataBuilder setDecisionDefinitionKey(String decisionDefinitionKey) {
    this.decisionDefinitionKey = decisionDefinitionKey;
    return this;
  }

  public DecisionReportDataBuilder setDecisionDefinitionVersion(String decisionDefinitionVersion) {
    this.decisionDefinitionVersion = decisionDefinitionVersion;
    return this;
  }

  public DecisionReportDataBuilder setFilter(DecisionFilterDto newFilter) {
    this.filter.add(newFilter);
    return this;
  }

  public DecisionReportDataBuilder setFilter(List<DecisionFilterDto> newFilter) {
    this.filter.addAll(newFilter);
    return this;
  }

  public static DecisionReportDataDto createDecisionReportDataViewRawAsTable(String decisionDefinitionKey,
                                                                             String decisionDefinitionVersion) {
    final DecisionReportDataDto decisionReportDataDto = new DecisionReportDataDto();
    decisionReportDataDto.setDecisionDefinitionKey(decisionDefinitionKey);
    decisionReportDataDto.setDecisionDefinitionVersion(decisionDefinitionVersion);
    decisionReportDataDto.setVisualization(DecisionVisualization.TABLE);
    decisionReportDataDto.setView(createDecisionRawDataView());
    decisionReportDataDto.setGroupBy(createGroupDecisionByNone());
    return decisionReportDataDto;
  }

}
