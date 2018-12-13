package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.query.report.VariableType;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;

import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createAverageProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createAverageProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createAverageProcessInstanceDurationGroupByVariable;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createAverageProcessInstanceDurationGroupByVariableWithProcessPart;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createAvgPiDurationHeatMapGroupByNone;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createAvgPiDurationHeatMapGroupByNoneWithProcessPart;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountFlowNodeFrequencyGroupByFlowNode;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountProcessInstanceFrequencyGroupByStartDate;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountProcessInstanceFrequencyGroupByVariable;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createMaxFlowNodeDurationGroupByFlowNodeHeatmapReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createMaxPiDurationHeatMapGroupByNoneWithProcessPart;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createMaxProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createMaxProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createMaxProcessInstanceDurationGroupByVariable;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createMaxProcessInstanceDurationGroupByVariableWithProcessPart;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createMaxProcessInstanceDurationHeatMapGroupByNone;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createMedianFlowNodeDurationGroupByFlowNodeHeatmapReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createMedianPiDurationHeatMapGroupByNoneWithProcessPart;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createMedianProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createMedianProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createMedianProcessInstanceDurationGroupByVariable;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createMedianProcessInstanceDurationGroupByVariableWithProcessPart;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createMedianProcessInstanceDurationHeatMapGroupByNone;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createMinFlowNodeDurationGroupByFlowNodeHeatmapReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createMinPiDurationHeatMapGroupByNoneWithProcessPart;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createMinProcessInstanceDurationGroupByStartDateReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createMinProcessInstanceDurationGroupByStartDateWithProcessPartReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createMinProcessInstanceDurationGroupByVariable;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createMinProcessInstanceDurationGroupByVariableWithProcessPart;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createMinProcessInstanceDurationHeatMapGroupByNone;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createPiFrequencyCountGroupedByNone;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable;

public class ProcessReportDataBuilder {

  private ProcessReportDataType reportDataType;

  private String processDefinitionKey;
  private String processDefinitionVersion;
  private String variableName;
  private VariableType variableType;
  private GroupByDateUnit dateInterval;
  private String startFlowNodeId;
  private String endFlowNodeId;

  private List<ProcessFilterDto> filter = new ArrayList<>();

  public static ProcessReportDataBuilder createReportData() {
    return new ProcessReportDataBuilder();
  }

  public ProcessReportDataDto build() {
    ProcessReportDataDto reportData = new ProcessReportDataDto();
    switch (reportDataType) {
      case RAW_DATA:
        reportData = createProcessReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
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

  public ProcessReportDataBuilder setReportDataType(ProcessReportDataType reportDataType) {
    this.reportDataType = reportDataType;
    return this;
  }

  public ProcessReportDataBuilder setProcessDefinitionKey(String processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public ProcessReportDataBuilder setProcessDefinitionVersion(String processDefinitionVersion) {
    this.processDefinitionVersion = processDefinitionVersion;
    return this;
  }

  public ProcessReportDataBuilder setVariableName(String variableName) {
    this.variableName = variableName;
    return this;
  }

  public ProcessReportDataBuilder setVariableType(VariableType variableType) {
    this.variableType = variableType;
    return this;
  }

  public ProcessReportDataBuilder setDateInterval(GroupByDateUnit dateInterval) {
    this.dateInterval = dateInterval;
    return this;
  }

  public ProcessReportDataBuilder setStartFlowNodeId(String startFlowNodeId) {
    this.startFlowNodeId = startFlowNodeId;
    return this;
  }

  public ProcessReportDataBuilder setEndFlowNodeId(String endFlowNodeId) {
    this.endFlowNodeId = endFlowNodeId;
    return this;
  }

  public ProcessReportDataBuilder setFilter(ProcessFilterDto newFilter) {
    this.filter.add(newFilter);
    return this;
  }

  public ProcessReportDataBuilder setFilter(List<ProcessFilterDto> newFilter) {
    this.filter.addAll(newFilter);
    return this;
  }
}
