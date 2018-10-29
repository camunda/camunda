package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.FilterDto;

import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.test.util.ReportDataHelper.createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport;
import static org.camunda.optimize.test.util.ReportDataHelper.createAverageProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.test.util.ReportDataHelper.createAverageProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.test.util.ReportDataHelper.createAverageProcessInstanceDurationGroupByVariable;
import static org.camunda.optimize.test.util.ReportDataHelper.createAverageProcessInstanceDurationGroupByVariableWithProcessPart;
import static org.camunda.optimize.test.util.ReportDataHelper.createAvgPiDurationHeatMapGroupByNone;
import static org.camunda.optimize.test.util.ReportDataHelper.createAvgPiDurationHeatMapGroupByNoneWithProcessPart;
import static org.camunda.optimize.test.util.ReportDataHelper.createCountFlowNodeFrequencyGroupByFlowNode;
import static org.camunda.optimize.test.util.ReportDataHelper.createCountProcessInstanceFrequencyGroupByStartDate;
import static org.camunda.optimize.test.util.ReportDataHelper.createCountProcessInstanceFrequencyGroupByVariable;
import static org.camunda.optimize.test.util.ReportDataHelper.createMaxFlowNodeDurationGroupByFlowNodeHeatmapReport;
import static org.camunda.optimize.test.util.ReportDataHelper.createMaxPiDurationHeatMapGroupByNoneWithProcessPart;
import static org.camunda.optimize.test.util.ReportDataHelper.createMaxProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.test.util.ReportDataHelper.createMaxProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.test.util.ReportDataHelper.createMaxProcessInstanceDurationGroupByVariable;
import static org.camunda.optimize.test.util.ReportDataHelper.createMaxProcessInstanceDurationGroupByVariableWithProcessPart;
import static org.camunda.optimize.test.util.ReportDataHelper.createMaxProcessInstanceDurationHeatMapGroupByNone;
import static org.camunda.optimize.test.util.ReportDataHelper.createMedianFlowNodeDurationGroupByFlowNodeHeatmapReport;
import static org.camunda.optimize.test.util.ReportDataHelper.createMedianPiDurationHeatMapGroupByNoneWithProcessPart;
import static org.camunda.optimize.test.util.ReportDataHelper.createMedianProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.test.util.ReportDataHelper.createMedianProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.test.util.ReportDataHelper.createMedianProcessInstanceDurationGroupByVariable;
import static org.camunda.optimize.test.util.ReportDataHelper.createMedianProcessInstanceDurationGroupByVariableWithProcessPart;
import static org.camunda.optimize.test.util.ReportDataHelper.createMedianProcessInstanceDurationHeatMapGroupByNone;
import static org.camunda.optimize.test.util.ReportDataHelper.createMinFlowNodeDurationGroupByFlowNodeHeatmapReport;
import static org.camunda.optimize.test.util.ReportDataHelper.createMinPiDurationHeatMapGroupByNoneWithProcessPart;
import static org.camunda.optimize.test.util.ReportDataHelper.createMinProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.test.util.ReportDataHelper.createMinProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.test.util.ReportDataHelper.createMinProcessInstanceDurationGroupByVariable;
import static org.camunda.optimize.test.util.ReportDataHelper.createMinProcessInstanceDurationGroupByVariableWithProcessPart;
import static org.camunda.optimize.test.util.ReportDataHelper.createMinProcessInstanceDurationHeatMapGroupByNone;
import static org.camunda.optimize.test.util.ReportDataHelper.createPiFrequencyCountGroupedByNone;
import static org.camunda.optimize.test.util.ReportDataHelper.createReportDataViewRawAsTable;

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
