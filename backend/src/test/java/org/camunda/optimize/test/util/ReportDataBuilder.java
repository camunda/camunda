package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.FilterDto;

import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createAverageProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createAverageProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createAverageProcessInstanceDurationGroupByVariable;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createAverageProcessInstanceDurationGroupByVariableWithProcessPart;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createAvgPiDurationHeatMapGroupByNone;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createAvgPiDurationHeatMapGroupByNoneWithProcessPart;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createCountFlowNodeFrequencyGroupByFlowNode;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createCountProcessInstanceFrequencyGroupByStartDate;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createCountProcessInstanceFrequencyGroupByVariable;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createMaxFlowNodeDurationGroupByFlowNodeHeatmapReport;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createMaxPiDurationHeatMapGroupByNoneWithProcessPart;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createMaxProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createMaxProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createMaxProcessInstanceDurationGroupByVariable;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createMaxProcessInstanceDurationGroupByVariableWithProcessPart;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createMaxProcessInstanceDurationHeatMapGroupByNone;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createMedianFlowNodeDurationGroupByFlowNodeHeatmapReport;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createMedianPiDurationHeatMapGroupByNoneWithProcessPart;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createMedianProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createMedianProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createMedianProcessInstanceDurationGroupByVariable;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createMedianProcessInstanceDurationGroupByVariableWithProcessPart;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createMedianProcessInstanceDurationHeatMapGroupByNone;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createMinFlowNodeDurationGroupByFlowNodeHeatmapReport;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createMinPiDurationHeatMapGroupByNoneWithProcessPart;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createMinProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createMinProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createMinProcessInstanceDurationGroupByVariable;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createMinProcessInstanceDurationGroupByVariableWithProcessPart;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createMinProcessInstanceDurationHeatMapGroupByNone;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createPiFrequencyCountGroupedByNone;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper.createReportDataViewRawAsTable;

public class ReportDataBuilder {

  private ReportDataType reportDataType;

  private String processDefinitionKey;
  private String processDefinitionVersion;
  private String variableName;
  private String variableType;
  private String dateInterval;
  private String startFlowNodeId;
  private String endFlowNodeId;

  private List<FilterDto> filter = new ArrayList<>();

  public static ReportDataBuilder createReportData() {
    return new ReportDataBuilder();
  }

  public SingleReportDataDto build() {
    SingleReportDataDto reportData = new SingleReportDataDto();
    switch (reportDataType) {
      case RAW_DATA:
        reportData = createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
        break;
      case AVG_PROC_INST_DUR_GROUP_BY_NONE:
        reportData = createAvgPiDurationHeatMapGroupByNone(
          processDefinitionKey,
          processDefinitionVersion
        );
        break;
      case AVG_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART:
        reportData = createAvgPiDurationHeatMapGroupByNoneWithProcessPart(
          processDefinitionKey,
          processDefinitionVersion,
          startFlowNodeId,
          endFlowNodeId
        );
        break;
      case AVG_PROC_INST_DUR_GROUP_BY_START_DATE:
        reportData = createAverageProcessInstanceDurationGroupByStartDateReport(
          processDefinitionKey,
          processDefinitionVersion,
          dateInterval
        );
        break;
      case AVG_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART:
        reportData = createAverageProcessInstanceDurationGroupByStartDateWithProcessPartReport(
          processDefinitionKey,
          processDefinitionVersion,
          dateInterval,
          startFlowNodeId,
          endFlowNodeId
        );
        break;
      case AVG_PROC_INST_DUR_GROUP_BY_VARIABLE:
        reportData = createAverageProcessInstanceDurationGroupByVariable(
          processDefinitionKey,
          processDefinitionVersion,
          variableName,
          variableType
        );
        break;
      case AVG_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART:
        reportData = createAverageProcessInstanceDurationGroupByVariableWithProcessPart(
          processDefinitionKey,
          processDefinitionVersion,
          variableName,
          variableType,
          startFlowNodeId,
          endFlowNodeId
        );
        break;
      case MIN_PROC_INST_DUR_GROUP_BY_NONE:
        reportData = createMinProcessInstanceDurationHeatMapGroupByNone(
          processDefinitionKey,
          processDefinitionVersion
        );
        break;
      case MIN_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART:
        reportData = createMinPiDurationHeatMapGroupByNoneWithProcessPart(
          processDefinitionKey,
          processDefinitionVersion,
          startFlowNodeId,
          endFlowNodeId
        );
        break;
      case MIN_PROC_INST_DUR_GROUP_BY_START_DATE:
        reportData = createMinProcessInstanceDurationGroupByStartDateReport(
          processDefinitionKey,
          processDefinitionVersion,
          dateInterval
        );
        break;
      case MIN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART:
        reportData = createMinProcessInstanceDurationGroupByStartDateWithProcessPartReport(
          processDefinitionKey,
          processDefinitionVersion,
          dateInterval,
          startFlowNodeId,
          endFlowNodeId
        );
        break;
      case MIN_PROC_INST_DUR_GROUP_BY_VARIABLE:
        reportData = createMinProcessInstanceDurationGroupByVariable(
          processDefinitionKey,
          processDefinitionVersion,
          variableName,
          variableType
        );
        break;
      case MIN_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART:
        reportData = createMinProcessInstanceDurationGroupByVariableWithProcessPart(
          processDefinitionKey,
          processDefinitionVersion,
          variableName,
          variableType,
          startFlowNodeId,
          endFlowNodeId
        );
        break;
      case MAX_PROC_INST_DUR_GROUP_BY_START_DATE:
        reportData = createMaxProcessInstanceDurationGroupByStartDateReport(
          processDefinitionKey,
          processDefinitionVersion,
          dateInterval
        );
        break;
      case MAX_PROC_INST_DUR_GROUP_BY_NONE:
        reportData = createMaxProcessInstanceDurationHeatMapGroupByNone(
          processDefinitionKey,
          processDefinitionVersion
        );
        break;
      case MAX_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART:
        reportData = createMaxPiDurationHeatMapGroupByNoneWithProcessPart(
          processDefinitionKey,
          processDefinitionVersion,
          startFlowNodeId,
          endFlowNodeId
        );
        break;
      case MAX_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART:
        reportData = createMaxProcessInstanceDurationGroupByStartDateWithProcessPartReport(
          processDefinitionKey,
          processDefinitionVersion,
          dateInterval,
          startFlowNodeId,
          endFlowNodeId
        );
        break;
      case MAX_PROC_INST_DUR_GROUP_BY_VARIABLE:
        reportData = createMaxProcessInstanceDurationGroupByVariable(
          processDefinitionKey,
          processDefinitionVersion,
          variableName,
          variableType
        );
        break;
      case MAX_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART:
        reportData = createMaxProcessInstanceDurationGroupByVariableWithProcessPart(
          processDefinitionKey,
          processDefinitionVersion,
          variableName,
          variableType,
          startFlowNodeId,
          endFlowNodeId
        );
        break;
      case MEDIAN_PROC_INST_DUR_GROUP_BY_NONE:
        reportData = createMedianProcessInstanceDurationHeatMapGroupByNone(
          processDefinitionKey,
          processDefinitionVersion
        );
        break;
      case MEDIAN_PROC_INST_DUR_GROUP_BY_NONE_WITH_PART:
        reportData = createMedianPiDurationHeatMapGroupByNoneWithProcessPart(
          processDefinitionKey,
          processDefinitionVersion,
          startFlowNodeId,
          endFlowNodeId
        );
        break;
      case MEDIAN_PROC_INST_DUR_GROUP_BY_START_DATE:
        reportData = createMedianProcessInstanceDurationGroupByStartDateReport(
          processDefinitionKey,
          processDefinitionVersion,
          dateInterval
        );
        break;
      case MEDIAN_PROC_INST_DUR_GROUP_BY_START_DATE_WITH_PART:
        reportData = createMedianProcessInstanceDurationGroupByStartDateWithProcessPartReport(
          processDefinitionKey,
          processDefinitionVersion,
          dateInterval,
          startFlowNodeId,
          endFlowNodeId
        );
        break;
      case MEDIAN_PROC_INST_DUR_GROUP_BY_VARIABLE:
        reportData = createMedianProcessInstanceDurationGroupByVariable(
          processDefinitionKey,
          processDefinitionVersion,
          variableName,
          variableType
        );
        break;
      case MEDIAN_PROC_INST_DUR_GROUP_BY_VARIABLE_WITH_PART:
        reportData = createMedianProcessInstanceDurationGroupByVariableWithProcessPart(
          processDefinitionKey,
          processDefinitionVersion,
          variableName,
          variableType,
          startFlowNodeId,
          endFlowNodeId
        );
        break;
      case COUNT_PROC_INST_FREQ_GROUP_BY_NONE:
        reportData = createPiFrequencyCountGroupedByNone(
          processDefinitionKey,
          processDefinitionVersion
        );
        break;
      case COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE:
        reportData = createCountProcessInstanceFrequencyGroupByStartDate(
          processDefinitionKey,
          processDefinitionVersion,
          dateInterval
        );
        break;
      case COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE:
        reportData = createCountFlowNodeFrequencyGroupByFlowNode(
          processDefinitionKey,
          processDefinitionVersion
        );
        break;
      case COUNT_PROC_INST_FREQ_GROUP_BY_VARIABLE:
        reportData = createCountProcessInstanceFrequencyGroupByVariable(
          processDefinitionKey,
          processDefinitionVersion,
          variableName,
          variableType
        );
        break;
      case AVG_FLOW_NODE_DUR_GROUP_BY_FLOW_NODE:
        reportData = createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(
          processDefinitionKey,
          processDefinitionVersion
        );
        break;
      case MIN_FLOW_NODE_DUR_GROUP_BY_FLOW_NODE:
        reportData = createMinFlowNodeDurationGroupByFlowNodeHeatmapReport(
          processDefinitionKey,
          processDefinitionVersion
        );
        break;
      case MAX_FLOW_NODE_DUR_GROUP_BY_FLOW_NODE:
        reportData = createMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(
          processDefinitionKey,
          processDefinitionVersion
        );
        break;
      case MEDIAN_FLOW_NODE_DUR_GROUP_BY_FLOW_NODE:
        reportData = createMedianFlowNodeDurationGroupByFlowNodeHeatmapReport(
          processDefinitionKey,
          processDefinitionVersion
        );
        break;
    }
    reportData.setFilter(this.filter);
    return reportData;
  }

  public ReportDataBuilder setReportDataType(ReportDataType reportDataType) {
    this.reportDataType = reportDataType;
    return this;
  }

  public ReportDataBuilder setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public ReportDataBuilder setProcessDefinitionVersion(String processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
    return this;
  }

  public ReportDataBuilder setVariableName(String variableName) {
    this.variableName = variableName;
    return this;
  }

  public ReportDataBuilder setVariableType(String variableType) {
    this.variableType = variableType;
    return this;
  }

  public ReportDataBuilder setDateInterval(String dateInterval) {
    this.dateInterval = dateInterval;
    return this;
  }

  public ReportDataBuilder setStartFlowNodeId(String startFlowNodeId) {
    this.startFlowNodeId = startFlowNodeId;
    return this;
  }

  public ReportDataBuilder setEndFlowNodeId(String endFlowNodeId) {
    this.endFlowNodeId = endFlowNodeId;
    return this;
  }

  public ReportDataBuilder setFilter(FilterDto newFilter) {
    this.filter.add(newFilter);
    return this;
  }

  public ReportDataBuilder setFilter(List<FilterDto> newFilter) {
    this.filter.addAll(newFilter);
    return this;
  }
}
