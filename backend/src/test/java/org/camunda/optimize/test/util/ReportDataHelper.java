package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.processpart.ProcessPartDto;

import java.util.Arrays;

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

  public static SingleReportDataDto createReportDataViewRawAsTable(
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

  public static SingleReportDataDto createAverageProcessInstanceDurationGroupByStartDateReport(
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

  public static SingleReportDataDto createMinProcessInstanceDurationGroupByStartDateReport(
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

  public static SingleReportDataDto createMaxProcessInstanceDurationGroupByStartDateReport(
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

  public static SingleReportDataDto createMedianProcessInstanceDurationGroupByStartDateReport(
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

  public static SingleReportDataDto createCountProcessInstanceFrequencyGroupByStartDate(
      String processDefinitionKey,
      String processDefinitionVersion,
      String dateInterval
  ) {

    ViewDto view = createCountProcessInstanceFrequencyView();
    GroupByDto groupByDto = createGroupByStartDateDto(dateInterval);

    SingleReportDataDto reportData = createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        TABLE_VISUALIZATION,
        view,
        groupByDto
    );

    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  private static SingleReportDataDto createReportDataViewRaw(
    String processDefinitionKey,
    String processDefinitionVersion,
    String visualization,
    ViewDto viewDto,
    GroupByDto groupByDto
  ) {
    SingleReportDataDto reportData = new SingleReportDataDto();
    reportData.setProcessDefinitionKey(processDefinitionKey);
    reportData.setProcessDefinitionVersion(processDefinitionVersion);
    reportData.setVisualization(visualization);
    reportData.setView(viewDto);
    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  public static SingleReportDataDto createCountFlowNodeFrequencyGroupByFlowNode(
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

  public static SingleReportDataDto createCountProcessInstanceFrequencyGroupByVariable(
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

  public static SingleReportDataDto createAverageProcessInstanceDurationGroupByVariable(
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

  public static SingleReportDataDto createMinProcessInstanceDurationGroupByVariable(
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

  public static SingleReportDataDto createMaxProcessInstanceDurationGroupByVariable(
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

  public static SingleReportDataDto createMedianProcessInstanceDurationGroupByVariable(
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

  public static SingleReportDataDto createCountFlowNodeFrequencyGroupByFlowNodeNumber(
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

  public static SingleReportDataDto createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(
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

  public static SingleReportDataDto createMinFlowNodeDurationGroupByFlowNodeHeatmapReport(
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

  public static SingleReportDataDto createMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(
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

  public static SingleReportDataDto createMedianFlowNodeDurationGroupByFlowNodeHeatmapReport(
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

  public static SingleReportDataDto createAvgPiDurationHeatMapGroupByNone(
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

  public static SingleReportDataDto createAvgPiDurationHeatMapGroupByNoneWithProcessPart(
      String processDefinitionKey,
      String processDefinitionVersion,
      String startFlowNodeId,
      String endFlowNodeId
  ) {

    ViewDto view = createAverageProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByNone();
    ProcessPartDto processPartDto = createProcessPart(startFlowNodeId, endFlowNodeId);

    SingleReportDataDto reportDataViewRaw = createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      HEAT_VISUALIZATION,
      view,
      groupByDto
    );
    reportDataViewRaw.setProcessPart(processPartDto);
    return reportDataViewRaw;
  }

  public static SingleReportDataDto createMinProcessInstanceDurationHeatMapGroupByNone(
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

  public static SingleReportDataDto createMinPiDurationHeatMapGroupByNoneWithProcessPart(
      String processDefinitionKey,
      String processDefinitionVersion,
      String startFlowNodeId,
      String endFlowNodeId
  ) {

    ViewDto view = createMinProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByNone();
    ProcessPartDto processPartDto = createProcessPart(startFlowNodeId, endFlowNodeId);

    SingleReportDataDto reportDataViewRaw = createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      HEAT_VISUALIZATION,
      view,
      groupByDto
    );
    reportDataViewRaw.setProcessPart(processPartDto);
    return reportDataViewRaw;
  }

  public static SingleReportDataDto createMaxProcessInstanceDurationHeatMapGroupByNone(
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

  public static SingleReportDataDto createMaxPiDurationHeatMapGroupByNoneWithProcessPart(
      String processDefinitionKey,
      String processDefinitionVersion,
      String startFlowNodeId,
      String endFlowNodeId
  ) {

    ViewDto view = createMaxProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByNone();
    ProcessPartDto processPartDto = createProcessPart(startFlowNodeId, endFlowNodeId);

    SingleReportDataDto reportDataViewRaw = createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      HEAT_VISUALIZATION,
      view,
      groupByDto
    );
    reportDataViewRaw.setProcessPart(processPartDto);
    return reportDataViewRaw;
  }

  public static SingleReportDataDto createMedianProcessInstanceDurationHeatMapGroupByNone(
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

  public static SingleReportDataDto createMedianPiDurationHeatMapGroupByNoneWithProcessPart(
      String processDefinitionKey,
      String processDefinitionVersion,
      String startFlowNodeId,
      String endFlowNodeId
  ) {

    ViewDto view = createMedianProcessInstanceDurationView();
    GroupByDto groupByDto = createGroupByNone();
    ProcessPartDto processPartDto = createProcessPart(startFlowNodeId, endFlowNodeId);

    SingleReportDataDto reportDataViewRaw = createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      HEAT_VISUALIZATION,
      view,
      groupByDto
    );
    reportDataViewRaw.setProcessPart(processPartDto);
    return reportDataViewRaw;
  }

  public static SingleReportDataDto createPiFrequencyCountGroupedByNone(
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

  public static SingleReportDataDto createPiFrequencyCountGroupedByNoneAsNumber(String processDefinitionKey, String processDefinitionVersion) {
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

  public static SingleReportDataDto createAvgPiDurationAsNumberGroupByNone(
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

  public static CombinedReportDataDto createCombinedReport(String... reportIds) {
    CombinedReportDataDto combinedReportDataDto = new CombinedReportDataDto();
    combinedReportDataDto.setReportIds(Arrays.asList(reportIds));
    combinedReportDataDto.setConfiguration("aRandomConfiguration");
    return combinedReportDataDto;
  }

  public static ProcessPartDto createProcessPart(String start, String end) {
    ProcessPartDto processPartDto = new ProcessPartDto();
    processPartDto.setStart(start);
    processPartDto.setEnd(end);
    return processPartDto;
  }

}
