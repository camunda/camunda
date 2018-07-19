package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ViewDto;
import org.camunda.optimize.dto.optimize.query.report.group.FlowNodesGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.group.GroupByDto;
import org.camunda.optimize.dto.optimize.query.report.group.NoneGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.group.StartDateGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.group.value.StartDateGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.group.value.VariableGroupByValueDto;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.DATE_UNIT_DAY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.HEAT_VISUALIZATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.SINGLE_NUMBER_VISUALIZATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.TABLE_VISUALIZATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_AVERAGE_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_COUNT_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_DURATION_PROPERTY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_FLOW_NODE_ENTITY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_FREQUENCY_PROPERTY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_MAX_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_MEDIAN_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_MIN_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_PROCESS_INSTANCE_ENTITY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_RAW_DATA_OPERATION;


public class ReportDataHelper {

  public static ReportDataDto createReportRawDataGroupByFlowNodesAsTable(String processDefinitionKey, String processDefinitionVersion) {
    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        TABLE_VISUALIZATION,
        new ViewDto(VIEW_RAW_DATA_OPERATION),
        createGroupByFlowNode()
    );
  }

  public static ReportDataDto createReportRawDataGroupByStartDateAsTable(String processDefinitionKey, String processDefinitionVersion) {
    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        TABLE_VISUALIZATION,
        new ViewDto(VIEW_RAW_DATA_OPERATION),
        createGroupByStartDateDto(DATE_UNIT_DAY)
    );
  }

  public static ReportDataDto createReportDataViewRawAsTable(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {
    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        TABLE_VISUALIZATION,
        new ViewDto(VIEW_RAW_DATA_OPERATION),
        null
    );
  }

  public static ReportDataDto createAvgPIDurationGroupByStartDateReport(
      String processDefinitionKey,
      String processDefinitionVersion,
      String dateInterval
  ) {

    ViewDto view = createAvgPIDurationView();
    GroupByDto groupByDto = createGroupByStartDateDto(dateInterval);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        TABLE_VISUALIZATION,
        view,
        groupByDto
    );
  }

  public static ReportDataDto createPICountFrequencyGroupByStartDate(
      String processDefinitionKey,
      String processDefinitionVersion,
      String dateInterval
  ) {

    ViewDto view = createCountPiFrequencyView();
    GroupByDto groupByDto = createGroupByStartDateDto(dateInterval);

    ReportDataDto reportData = createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        TABLE_VISUALIZATION,
        view,
        groupByDto
    );

    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  private static GroupByDto createGroupByStartDateDto(String dateInterval) {
    StartDateGroupByDto groupByDto = new StartDateGroupByDto();
    StartDateGroupByValueDto valueDto = new StartDateGroupByValueDto();
    valueDto.setUnit(dateInterval);
    groupByDto.setValue(valueDto);
    return groupByDto;
  }

  public static ReportDataDto createReportDataViewRaw(
      String processDefinitionKey,
      String processDefinitionVersion,
      String visualization,
      ViewDto viewDto,
      GroupByDto groupByDto
  ) {
    ReportDataDto reportData = new ReportDataDto();
    reportData.setProcessDefinitionKey(processDefinitionKey);
    reportData.setProcessDefinitionVersion(processDefinitionVersion);
    reportData.setVisualization(visualization);
    reportData.setView(viewDto);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static ReportDataDto createCountFlowNodeFrequencyGroupByFlowNode(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {

    ViewDto view = new ViewDto();
    view.setOperation(VIEW_COUNT_OPERATION);
    view.setEntity(VIEW_FLOW_NODE_ENTITY);
    view.setProperty(VIEW_FREQUENCY_PROPERTY);


    GroupByDto groupByDto = createGroupByFlowNode();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        HEAT_VISUALIZATION,
        view,
        groupByDto
    );
  }

  public static ReportDataDto createCountProcessInstanceFrequencyGroupByVariable(
      String processDefinitionKey,
      String processDefinitionVersion,
      String variableName,
      String variableType
  ) {

    ViewDto view = createCountPiFrequencyView();
    GroupByDto groupByDto = createGroupByVariable(variableName, variableType);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        HEAT_VISUALIZATION,
        view,
        groupByDto
    );
  }

  public static ReportDataDto createAvgProcessInstanceDurationGroupByVariable(
      String processDefinitionKey,
      String processDefinitionVersion,
      String variableName,
      String variableType
  ) {

    ViewDto view = createAvgPIDurationView();
    GroupByDto groupByDto = createGroupByVariable(variableName, variableType);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        HEAT_VISUALIZATION,
        view,
        groupByDto
    );
  }

  public static ReportDataDto createCountFlowNodeFrequencyGroupByFlowNoneNumber(
    String processDefinitionKey,
    String processDefinitionVersion
  ) {

    ViewDto view = new ViewDto();
    view.setOperation(VIEW_COUNT_OPERATION);
    view.setEntity(VIEW_FLOW_NODE_ENTITY);
    view.setProperty(VIEW_FREQUENCY_PROPERTY);


    GroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      SINGLE_NUMBER_VISUALIZATION,
      view,
      groupByDto
    );
  }

  public static ReportDataDto createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {
    ViewDto view = createAvgFlowNodeDurationView();

    GroupByDto groupByDto = createGroupByFlowNode();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        HEAT_VISUALIZATION,
        view,
        groupByDto
    );
  }

  public static ReportDataDto createMinFlowNodeDurationGroupByFlowNodeHeatmapReport(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {
    ViewDto view = createMinFlowNodeDurationView();
    GroupByDto groupByDto = createGroupByFlowNode();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        HEAT_VISUALIZATION,
        view,
        groupByDto
    );
  }

  public static ReportDataDto createMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {
    ViewDto view = createMaxFlowNodeDurationView();
    GroupByDto groupByDto = createGroupByFlowNode();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        HEAT_VISUALIZATION,
        view,
        groupByDto
    );
  }

  public static ReportDataDto createMedianFlowNodeDurationGroupByFlowNodeHeatmapReport(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {
    ViewDto view = createMedianFlowNodeDurationView();
    GroupByDto groupByDto = createGroupByFlowNode();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        HEAT_VISUALIZATION,
        view,
        groupByDto
    );
  }

  private static GroupByDto createGroupByFlowNode() {
    return new FlowNodesGroupByDto();
  }

  private static GroupByDto createGroupByVariable(String variableName, String variableType) {
    VariableGroupByValueDto groupByValueDto = new VariableGroupByValueDto();
    groupByValueDto.setName(variableName);
    groupByValueDto.setType(variableType);
    VariableGroupByDto groupByDto = new VariableGroupByDto();
    groupByDto.setValue(groupByValueDto);
    return groupByDto;
  }

  public static ReportDataDto createAvgPiDurationHeatMapGroupByNone(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {

    ViewDto view = createAvgPIDurationView();
    GroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        HEAT_VISUALIZATION,
        view,
        groupByDto
    );
  }

  private static GroupByDto createGroupByNone() {
    return new NoneGroupByDto();
  }

  public static ReportDataDto createPiFrequencyCountGroupedByNone(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {
    ViewDto view = createCountPiFrequencyView();
    GroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        HEAT_VISUALIZATION,
        view,
        groupByDto
    );
  }

  public static ReportDataDto createPiFrequencyCountGroupedByNoneAsNumber(String processDefinitionKey, String processDefinitionVersion) {
    ViewDto view = createCountPiFrequencyView();

    GroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        //does not really affect backend, since command object is instantiated based on
        //group by criterion
        SINGLE_NUMBER_VISUALIZATION,
        view,
        groupByDto
    );
  }

  public static ReportDataDto createAvgPiDurationAsNumberGroupByNone(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {

    ViewDto view = createAvgPIDurationView();
    GroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        SINGLE_NUMBER_VISUALIZATION,
        view,
        groupByDto
    );
  }

  private static ViewDto createCountPiFrequencyView() {
    ViewDto view = new ViewDto();
    view.setOperation(VIEW_COUNT_OPERATION);
    view.setEntity(VIEW_PROCESS_INSTANCE_ENTITY);
    view.setProperty(VIEW_FREQUENCY_PROPERTY);
    return view;
  }

  private static ViewDto createAvgPIDurationView() {
    ViewDto view = new ViewDto();
    view.setOperation(VIEW_AVERAGE_OPERATION);
    view.setEntity(VIEW_PROCESS_INSTANCE_ENTITY);
    view.setProperty(VIEW_DURATION_PROPERTY);
    return view;
  }

  private static ViewDto createAvgFlowNodeDurationView() {
    ViewDto view = new ViewDto();
    view.setOperation(VIEW_AVERAGE_OPERATION);
    view.setEntity(VIEW_FLOW_NODE_ENTITY);
    view.setProperty(VIEW_DURATION_PROPERTY);
    return view;
  }

  private static ViewDto createMinFlowNodeDurationView() {
    ViewDto view = new ViewDto();
    view.setOperation(VIEW_MIN_OPERATION);
    view.setEntity(VIEW_FLOW_NODE_ENTITY);
    view.setProperty(VIEW_DURATION_PROPERTY);
    return view;
  }

  private static ViewDto createMaxFlowNodeDurationView() {
    ViewDto view = new ViewDto();
    view.setOperation(VIEW_MAX_OPERATION);
    view.setEntity(VIEW_FLOW_NODE_ENTITY);
    view.setProperty(VIEW_DURATION_PROPERTY);
    return view;
  }

  private static ViewDto createMedianFlowNodeDurationView() {
    ViewDto view = new ViewDto();
    view.setOperation(VIEW_MEDIAN_OPERATION);
    view.setEntity(VIEW_FLOW_NODE_ENTITY);
    view.setProperty(VIEW_DURATION_PROPERTY);
    return view;
  }
}
