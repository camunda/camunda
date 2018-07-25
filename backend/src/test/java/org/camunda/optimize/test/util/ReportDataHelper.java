package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ViewDto;
import org.camunda.optimize.dto.optimize.query.report.group.GroupByDto;

import static org.camunda.optimize.service.es.report.command.util.GroupByDtoCreator.createGroupByFlowNode;
import static org.camunda.optimize.service.es.report.command.util.GroupByDtoCreator.createGroupByNone;
import static org.camunda.optimize.service.es.report.command.util.GroupByDtoCreator.createGroupByStartDateDto;
import static org.camunda.optimize.service.es.report.command.util.GroupByDtoCreator.createGroupByVariable;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.HEAT_VISUALIZATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.SINGLE_NUMBER_VISUALIZATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.TABLE_VISUALIZATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_COUNT_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_FLOW_NODE_ENTITY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_FREQUENCY_PROPERTY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_RAW_DATA_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createAverageFlowNodeDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createAverageProcessInstanceDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createCountProcessInstanceFrequencyView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createMaxFlowNodeDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createMaxProcessInstanceDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createMedianFlowNodeDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createMedianProcessInstanceDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createMinFlowNodeDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createMinProcessInstanceDurationView;


public class ReportDataHelper {

  public static ReportDataDto createReportDataViewRawAsTable(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {
    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        TABLE_VISUALIZATION,
        new ViewDto(VIEW_RAW_DATA_OPERATION),
        createGroupByNone()
    );
  }

  public static ReportDataDto createAverageProcessInstanceDurationGroupByStartDateReport(
      String processDefinitionKey,
      String processDefinitionVersion,
      String dateInterval
  ) {

    ViewDto view = createAverageProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByStartDateDto(dateInterval);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        TABLE_VISUALIZATION,
        view,
        groupByDto
    );
  }

  public static ReportDataDto createMinProcessInstanceDurationGroupByStartDateReport(
      String processDefinitionKey,
      String processDefinitionVersion,
      String dateInterval
  ) {

    ViewDto view = createMinProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByStartDateDto(dateInterval);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        TABLE_VISUALIZATION,
        view,
        groupByDto
    );
  }

  public static ReportDataDto createMaxProcessInstanceDurationGroupByStartDateReport(
      String processDefinitionKey,
      String processDefinitionVersion,
      String dateInterval
  ) {

    ViewDto view = createMaxProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByStartDateDto(dateInterval);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        TABLE_VISUALIZATION,
        view,
        groupByDto
    );
  }

  public static ReportDataDto createMedianProcessInstanceDurationGroupByStartDateReport(
      String processDefinitionKey,
      String processDefinitionVersion,
      String dateInterval
  ) {

    ViewDto view = createMedianProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByStartDateDto(dateInterval);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        TABLE_VISUALIZATION,
        view,
        groupByDto
    );
  }

  public static ReportDataDto createCountProcessInstanceFrequencyGroupByStartDate(
      String processDefinitionKey,
      String processDefinitionVersion,
      String dateInterval
  ) {

    ViewDto view = createCountProcessInstanceFrequencyView();
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

    ViewDto view = createCountProcessInstanceFrequencyView();
    GroupByDto groupByDto = createGroupByVariable(variableName, variableType);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        HEAT_VISUALIZATION,
        view,
        groupByDto
    );
  }

  public static ReportDataDto createAverageProcessInstanceDurationGroupByVariable(
      String processDefinitionKey,
      String processDefinitionVersion,
      String variableName,
      String variableType
  ) {

    ViewDto view = createAverageProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByVariable(variableName, variableType);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        HEAT_VISUALIZATION,
        view,
        groupByDto
    );
  }

  public static ReportDataDto createMinProcessInstanceDurationGroupByVariable(
      String processDefinitionKey,
      String processDefinitionVersion,
      String variableName,
      String variableType
  ) {

    ViewDto view = createMinProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByVariable(variableName, variableType);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        HEAT_VISUALIZATION,
        view,
        groupByDto
    );
  }

  public static ReportDataDto createMaxProcessInstanceDurationGroupByVariable(
      String processDefinitionKey,
      String processDefinitionVersion,
      String variableName,
      String variableType
  ) {

    ViewDto view = createMaxProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByVariable(variableName, variableType);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        HEAT_VISUALIZATION,
        view,
        groupByDto
    );
  }

  public static ReportDataDto createMedianProcessInstanceDurationGroupByVariable(
      String processDefinitionKey,
      String processDefinitionVersion,
      String variableName,
      String variableType
  ) {

    ViewDto view = createMedianProcessInstanceDurationView();
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
    ViewDto view = createAverageFlowNodeDurationView();

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

  public static ReportDataDto createAvgPiDurationHeatMapGroupByNone(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {

    ViewDto view = createAverageProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        HEAT_VISUALIZATION,
        view,
        groupByDto
    );
  }

  public static ReportDataDto createMinProcessInstanceDurationHeatMapGroupByNone(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {

    ViewDto view = createMinProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        HEAT_VISUALIZATION,
        view,
        groupByDto
    );
  }

  public static ReportDataDto createMaxProcessInstanceDurationHeatMapGroupByNone(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {

    ViewDto view = createMaxProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        HEAT_VISUALIZATION,
        view,
        groupByDto
    );
  }

  public static ReportDataDto createMedianProcessInstanceDurationHeatMapGroupByNone(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {

    ViewDto view = createMedianProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        HEAT_VISUALIZATION,
        view,
        groupByDto
    );
  }

  public static ReportDataDto createPiFrequencyCountGroupedByNone(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {
    ViewDto view = createCountProcessInstanceFrequencyView();
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
    ViewDto view = createCountProcessInstanceFrequencyView();

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

    ViewDto view = createAverageProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        SINGLE_NUMBER_VISUALIZATION,
        view,
        groupByDto
    );
  }

}
