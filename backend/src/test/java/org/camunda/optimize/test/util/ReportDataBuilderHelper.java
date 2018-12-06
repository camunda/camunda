package org.camunda.optimize.test.util;

import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.parameters.ParametersDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.parameters.ProcessPartDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewOperation;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;

import java.util.Arrays;

import static org.camunda.optimize.service.es.report.command.util.GroupByDtoCreator.createGroupByFlowNode;
import static org.camunda.optimize.service.es.report.command.util.GroupByDtoCreator.createGroupByNone;
import static org.camunda.optimize.service.es.report.command.util.GroupByDtoCreator.createGroupByStartDateDto;
import static org.camunda.optimize.service.es.report.command.util.GroupByDtoCreator.createGroupByVariable;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createAverageFlowNodeDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createAverageProcessInstanceDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createCountProcessInstanceFrequencyView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createMaxFlowNodeDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createMaxProcessInstanceDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createMedianFlowNodeDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createMedianProcessInstanceDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createMinFlowNodeDurationView;
import static org.camunda.optimize.service.es.report.command.util.ViewDtoCreator.createMinProcessInstanceDurationView;


public class ReportDataBuilderHelper {

  public static ProcessReportDataDto createProcessReportDataViewRawAsTable(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {
    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.TABLE,
        new ProcessViewDto(ProcessViewOperation.RAW),
        createGroupByNone()
    );
  }

  public static ProcessReportDataDto createAverageProcessInstanceDurationGroupByStartDateReport(
      String processDefinitionKey,
      String processDefinitionVersion,
      GroupByDateUnit dateInterval
  ) {

    ProcessViewDto view = createAverageProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto(dateInterval);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.TABLE,
        view,
        groupByDto
    );
  }

  public static ProcessReportDataDto createAverageProcessInstanceDurationGroupByStartDateWithProcessPartReport(
      String processDefinitionKey,
      String processDefinitionVersion,
      GroupByDateUnit dateInterval,
      String startFlowNodeId,
      String endFlowNodeId
  ) {

    ProcessViewDto view = createAverageProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto(dateInterval);
    ProcessPartDto processPartDto = createProcessPart(startFlowNodeId, endFlowNodeId);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.TABLE,
        view,
        groupByDto,
        processPartDto
    );
  }

  public static ProcessReportDataDto createMinProcessInstanceDurationGroupByStartDateReport(
      String processDefinitionKey,
      String processDefinitionVersion,
      GroupByDateUnit dateInterval
  ) {

    ProcessViewDto view = createMinProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto(dateInterval);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.TABLE,
        view,
        groupByDto
    );
  }

  public static ProcessReportDataDto createMinProcessInstanceDurationGroupByStartDateWithProcessPartReport(
      String processDefinitionKey,
      String processDefinitionVersion,
      GroupByDateUnit dateInterval,
      String startFlowNodeId,
      String endFlowNodeId
  ) {

    ProcessViewDto view = createMinProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto(dateInterval);
    ProcessPartDto processPartDto = createProcessPart(startFlowNodeId, endFlowNodeId);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.TABLE,
        view,
        groupByDto,
        processPartDto
    );
  }

  public static ProcessReportDataDto createMaxProcessInstanceDurationGroupByStartDateReport(
      String processDefinitionKey,
      String processDefinitionVersion,
      GroupByDateUnit dateInterval
  ) {

    ProcessViewDto view = createMaxProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto(dateInterval);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.TABLE,
        view,
        groupByDto
    );
  }

  public static ProcessReportDataDto createMaxProcessInstanceDurationGroupByStartDateWithProcessPartReport(
      String processDefinitionKey,
      String processDefinitionVersion,
      GroupByDateUnit dateInterval,
      String flowNodeStartId,
      String flowNodeEndId
  ) {

    ProcessViewDto view = createMaxProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto(dateInterval);
    ProcessPartDto processPartDto = createProcessPart(flowNodeStartId, flowNodeEndId);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.TABLE,
        view,
        groupByDto,
        processPartDto
    );
  }

  public static ProcessReportDataDto createMedianProcessInstanceDurationGroupByStartDateReport(
      String processDefinitionKey,
      String processDefinitionVersion,
      GroupByDateUnit dateInterval
  ) {

    ProcessViewDto view = createMedianProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto(dateInterval);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.TABLE,
        view,
        groupByDto
    );
  }

  public static ProcessReportDataDto createMedianProcessInstanceDurationGroupByStartDateWithProcessPartReport(
      String processDefinitionKey,
      String processDefinitionVersion,
      GroupByDateUnit dateInterval,
      String startFlowNodeId,
      String endFlowNodeId
  ) {

    ProcessViewDto view = createMedianProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto(dateInterval);
    ProcessPartDto processPartDto = createProcessPart(startFlowNodeId, endFlowNodeId);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.TABLE,
        view,
        groupByDto,
        processPartDto
    );
  }

  public static ProcessReportDataDto createCountProcessInstanceFrequencyGroupByStartDate(
      String processDefinitionKey,
      String processDefinitionVersion,
      GroupByDateUnit dateInterval
  ) {

    ProcessViewDto view = createCountProcessInstanceFrequencyView();
    ProcessGroupByDto groupByDto = createGroupByStartDateDto(dateInterval);

    ProcessReportDataDto reportData = createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.TABLE,
        view,
        groupByDto
    );

    reportData.setGroupBy(groupByDto);
    return reportData;
  }

  private static ProcessReportDataDto createReportDataViewRaw(
    String processDefinitionKey,
    String processDefinitionVersion,
    ProcessVisualization visualization,
    ProcessViewDto viewDto,
    ProcessGroupByDto groupByDto
  ) {
    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      visualization,
      viewDto,
      groupByDto,
      null
    );
  }

  private static ProcessReportDataDto createReportDataViewRaw(
    String processDefinitionKey,
    String processDefinitionVersion,
    ProcessVisualization visualization,
    ProcessViewDto viewDto,
    ProcessGroupByDto groupByDto,
    ProcessPartDto processPartDto
  ) {
    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setProcessDefinitionKey(processDefinitionKey);
    reportData.setProcessDefinitionVersion(processDefinitionVersion);
    reportData.setVisualization(visualization);
    reportData.setView(viewDto);
    reportData.setGroupBy(groupByDto);
    reportData.setParameters(new ParametersDto(processPartDto));
    return reportData;
  }

  public static ProcessReportDataDto createCountFlowNodeFrequencyGroupByFlowNode(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {

    ProcessViewDto view = new ProcessViewDto();
    view.setOperation(ProcessViewOperation.COUNT);
    view.setEntity(ProcessViewEntity.FLOW_NODE);
    view.setProperty(ProcessViewProperty.FREQUENCY);


    ProcessGroupByDto groupByDto = createGroupByFlowNode();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.HEAT,
        view,
        groupByDto
    );
  }

  public static ProcessReportDataDto createCountProcessInstanceFrequencyGroupByVariable(
      String processDefinitionKey,
      String processDefinitionVersion,
      String variableName,
      String variableType
  ) {

    ProcessViewDto view = createCountProcessInstanceFrequencyView();
    ProcessGroupByDto groupByDto = createGroupByVariable(variableName, variableType);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.HEAT,
        view,
        groupByDto
    );
  }

  public static ProcessReportDataDto createAverageProcessInstanceDurationGroupByVariable(
      String processDefinitionKey,
      String processDefinitionVersion,
      String variableName,
      String variableType
  ) {

    ProcessViewDto view = createAverageProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByVariable(variableName, variableType);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.HEAT,
        view,
        groupByDto
    );
  }

  public static ProcessReportDataDto createAverageProcessInstanceDurationGroupByVariableWithProcessPart(
      String processDefinitionKey,
      String processDefinitionVersion,
      String variableName,
      String variableType,
      String startFlowNodeId,
      String endFlowNodeId
  ) {
    ProcessReportDataDto reportData =
      createAverageProcessInstanceDurationGroupByVariable(
      processDefinitionKey,
      processDefinitionVersion,
      variableName,
      variableType
    );
    reportData.getParameters().setProcessPart(createProcessPart(startFlowNodeId, endFlowNodeId));
    return reportData;
  }

  public static ProcessReportDataDto createMinProcessInstanceDurationGroupByVariable(
      String processDefinitionKey,
      String processDefinitionVersion,
      String variableName,
      String variableType
  ) {

    ProcessViewDto view = createMinProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByVariable(variableName, variableType);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.HEAT,
        view,
        groupByDto
    );
  }

  public static ProcessReportDataDto createMinProcessInstanceDurationGroupByVariableWithProcessPart (
      String processDefinitionKey,
      String processDefinitionVersion,
      String variableName,
      String variableType,
      String startFlowNodeId,
      String endFlowNodeId
  ) {

    ProcessReportDataDto reportData = createMinProcessInstanceDurationGroupByVariable(
      processDefinitionKey,
      processDefinitionVersion,
      variableName,
      variableType
    );
    reportData.getParameters().setProcessPart(createProcessPart(startFlowNodeId, endFlowNodeId));
    return reportData;
  }

  public static ProcessReportDataDto createMaxProcessInstanceDurationGroupByVariable(
      String processDefinitionKey,
      String processDefinitionVersion,
      String variableName,
      String variableType
  ) {

    ProcessViewDto view = createMaxProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByVariable(variableName, variableType);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.HEAT,
        view,
        groupByDto
    );
  }

  public static ProcessReportDataDto createMaxProcessInstanceDurationGroupByVariableWithProcessPart (
      String processDefinitionKey,
      String processDefinitionVersion,
      String variableName,
      String variableType,
      String startFlowNodeId,
      String endFlowNodeId
  ) {

    ProcessReportDataDto reportData = createMaxProcessInstanceDurationGroupByVariable(
      processDefinitionKey,
      processDefinitionVersion,
      variableName,
      variableType
    );
    reportData.getParameters().setProcessPart(createProcessPart(startFlowNodeId, endFlowNodeId));
    return reportData;
  }

  public static ProcessReportDataDto createMedianProcessInstanceDurationGroupByVariable(
      String processDefinitionKey,
      String processDefinitionVersion,
      String variableName,
      String variableType
  ) {

    ProcessViewDto view = createMedianProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByVariable(variableName, variableType);

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.HEAT,
        view,
        groupByDto
    );
  }

  public static ProcessReportDataDto createMedianProcessInstanceDurationGroupByVariableWithProcessPart (
      String processDefinitionKey,
      String processDefinitionVersion,
      String variableName,
      String variableType,
      String startFlowNodeId,
      String endFlowNodeId
  ) {

    ProcessReportDataDto reportData =
      createMedianProcessInstanceDurationGroupByVariable(
      processDefinitionKey,
      processDefinitionVersion,
      variableName,
      variableType
    );
    reportData.getParameters().setProcessPart(createProcessPart(startFlowNodeId, endFlowNodeId));
    return reportData;
  }

  public static ProcessReportDataDto createCountFlowNodeFrequencyGroupByFlowNodeNumber(
    String processDefinitionKey,
    String processDefinitionVersion
  ) {

    ProcessViewDto view = new ProcessViewDto();
    view.setOperation(ProcessViewOperation.COUNT);
    view.setEntity(ProcessViewEntity.FLOW_NODE);
    view.setProperty(ProcessViewProperty.FREQUENCY);


    ProcessGroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      ProcessVisualization.NUMBER,
      view,
      groupByDto
    );
  }

  public static ProcessReportDataDto createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {
    ProcessViewDto view = createAverageFlowNodeDurationView();

    ProcessGroupByDto groupByDto = createGroupByFlowNode();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.HEAT,
        view,
        groupByDto
    );
  }

  public static ProcessReportDataDto createMinFlowNodeDurationGroupByFlowNodeHeatmapReport(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {
    ProcessViewDto view = createMinFlowNodeDurationView();
    ProcessGroupByDto groupByDto = createGroupByFlowNode();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.HEAT,
        view,
        groupByDto
    );
  }

  public static ProcessReportDataDto createMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {
    ProcessViewDto view = createMaxFlowNodeDurationView();
    ProcessGroupByDto groupByDto = createGroupByFlowNode();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.HEAT,
        view,
        groupByDto
    );
  }

  public static ProcessReportDataDto createMedianFlowNodeDurationGroupByFlowNodeHeatmapReport(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {
    ProcessViewDto view = createMedianFlowNodeDurationView();
    ProcessGroupByDto groupByDto = createGroupByFlowNode();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.HEAT,
        view,
        groupByDto
    );
  }

  public static ProcessReportDataDto createAvgPiDurationHeatMapGroupByNone(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {

    ProcessViewDto view = createAverageProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.HEAT,
        view,
        groupByDto
    );
  }

  public static ProcessReportDataDto createAvgPiDurationHeatMapGroupByNoneWithProcessPart(
      String processDefinitionKey,
      String processDefinitionVersion,
      String startFlowNodeId,
      String endFlowNodeId
  ) {

    ProcessViewDto view = createAverageProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();
    ProcessPartDto processPartDto = createProcessPart(startFlowNodeId, endFlowNodeId);

    return createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      ProcessVisualization.HEAT,
      view,
      groupByDto,
      processPartDto
    );
  }

  public static ProcessReportDataDto createMinProcessInstanceDurationHeatMapGroupByNone(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {

    ProcessViewDto view = createMinProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.HEAT,
        view,
        groupByDto
    );
  }

  public static ProcessReportDataDto createMinPiDurationHeatMapGroupByNoneWithProcessPart(
      String processDefinitionKey,
      String processDefinitionVersion,
      String startFlowNodeId,
      String endFlowNodeId
  ) {

    ProcessViewDto view = createMinProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();
    ProcessPartDto processPartDto = createProcessPart(startFlowNodeId, endFlowNodeId);

    ProcessReportDataDto reportDataViewRaw = createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      ProcessVisualization.HEAT,
      view,
      groupByDto
    );
    reportDataViewRaw.getParameters().setProcessPart(processPartDto);
    return reportDataViewRaw;
  }

  public static ProcessReportDataDto createMaxProcessInstanceDurationHeatMapGroupByNone(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {

    ProcessViewDto view = createMaxProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.HEAT,
        view,
        groupByDto
    );
  }

  public static ProcessReportDataDto createMaxPiDurationHeatMapGroupByNoneWithProcessPart(
      String processDefinitionKey,
      String processDefinitionVersion,
      String startFlowNodeId,
      String endFlowNodeId
  ) {

    ProcessViewDto view = createMaxProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();
    ProcessPartDto processPartDto = createProcessPart(startFlowNodeId, endFlowNodeId);

    ProcessReportDataDto reportDataViewRaw = createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      ProcessVisualization.HEAT,
      view,
      groupByDto
    );
    reportDataViewRaw.getParameters().setProcessPart(processPartDto);
    return reportDataViewRaw;
  }

  public static ProcessReportDataDto createMedianProcessInstanceDurationHeatMapGroupByNone(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {

    ProcessViewDto view = createMedianProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.HEAT,
        view,
        groupByDto
    );
  }

  public static ProcessReportDataDto createMedianPiDurationHeatMapGroupByNoneWithProcessPart(
      String processDefinitionKey,
      String processDefinitionVersion,
      String startFlowNodeId,
      String endFlowNodeId
  ) {

    ProcessViewDto view = createMedianProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();
    ProcessPartDto processPartDto = createProcessPart(startFlowNodeId, endFlowNodeId);

    ProcessReportDataDto reportDataViewRaw = createReportDataViewRaw(
      processDefinitionKey,
      processDefinitionVersion,
      ProcessVisualization.HEAT,
      view,
      groupByDto
    );
    reportDataViewRaw.getParameters().setProcessPart(processPartDto);
    return reportDataViewRaw;
  }

  public static ProcessReportDataDto createPiFrequencyCountGroupedByNone(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {
    ProcessViewDto view = createCountProcessInstanceFrequencyView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.NUMBER,
        view,
        groupByDto
    );
  }

  public static ProcessReportDataDto createPiFrequencyCountGroupedByNoneAsNumber(String processDefinitionKey, String processDefinitionVersion) {
    ProcessViewDto view = createCountProcessInstanceFrequencyView();

    ProcessGroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        //does not really affect backend, since command object is instantiated based on
        //group by criterion
        ProcessVisualization.NUMBER,
        view,
        groupByDto
    );
  }

  public static ProcessReportDataDto createAvgPiDurationAsNumberGroupByNone(
      String processDefinitionKey,
      String processDefinitionVersion
  ) {

    ProcessViewDto view = createAverageProcessInstanceDurationView();
    ProcessGroupByDto groupByDto = createGroupByNone();

    return createReportDataViewRaw(
        processDefinitionKey,
        processDefinitionVersion,
        ProcessVisualization.NUMBER,
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

  private static ProcessPartDto createProcessPart(String start, String end) {
    ProcessPartDto processPartDto = new ProcessPartDto();
    processPartDto.setStart(start);
    processPartDto.setEnd(end);
    return processPartDto;
  }

}
