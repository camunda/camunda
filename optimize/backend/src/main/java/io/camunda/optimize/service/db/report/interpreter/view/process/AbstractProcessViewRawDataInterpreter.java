/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.view.process;

import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.VARIABLE_PREFIX;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_RAW_DATA;
import static io.camunda.optimize.service.export.CSVUtils.extractAllProcessInstanceDtoFieldKeys;

import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.view.ViewInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import io.camunda.optimize.service.export.CSVUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public abstract class AbstractProcessViewRawDataInterpreter
    implements ViewInterpreter<ProcessReportDataDto, ProcessExecutionPlan> {

  public static final String SORT_SCRIPT =
      """
          if (doc[params.duration].size() == 0) {
            params.currentTime - doc[params.startDate].value.toInstant().toEpochMilli()
          } else {
             doc[params.duration].value
          }
          """;
  public static final String NUMBER_OF_USER_TASKS_SCRIPT =
      """
          Optional.ofNullable(params._source.flowNodeInstances)
            .map(list -> list.stream().filter(item -> item.flowNodeType.equals('userTask')).count())
            .orElse(0L)
          """;
  protected static final String CURRENT_TIME = "currentTime";
  protected static final String PARAMS_CURRENT_TIME = "params." + CURRENT_TIME;
  protected static final String DATE_FORMAT = "dateFormat";
  protected static final String FLOW_NODE_IDS_TO_DURATIONS = "flowNodeIdsToDurations";
  protected static final String NUMBER_OF_USER_TASKS = "numberOfUserTasks";
  protected static final String GET_FLOW_NODE_DURATIONS_SCRIPT =
      """
          def flowNodeInstanceIdToDuration = new HashMap();
          def dateFormatter = new SimpleDateFormat(params.dateFormat);
          for (flowNodeInstance in params._source.flowNodeInstances) {
            if (flowNodeInstance.totalDurationInMs != null) {
              if (flowNodeInstanceIdToDuration.containsKey(flowNodeInstance.flowNodeId)) {
                def currentDuration = flowNodeInstanceIdToDuration.get(flowNodeInstance.flowNodeId);
                flowNodeInstanceIdToDuration.put(flowNodeInstance.flowNodeId, flowNodeInstance.totalDurationInMs + currentDuration)
              } else {
                flowNodeInstanceIdToDuration.put(flowNodeInstance.flowNodeId, flowNodeInstance.totalDurationInMs)
              }
            } else {
              if (flowNodeInstance.startDate != null) {
                def duration = params.currentTime - dateFormatter.parse(flowNodeInstance.startDate).getTime();
                if (flowNodeInstanceIdToDuration.containsKey(flowNodeInstance.flowNodeId)) {
                  def currentDuration = flowNodeInstanceIdToDuration.get(flowNodeInstance.flowNodeId);
                  flowNodeInstanceIdToDuration.put(flowNodeInstance.flowNodeId, duration + currentDuration)
                } else {
                  flowNodeInstanceIdToDuration.put(flowNodeInstance.flowNodeId, duration)
                }
              }
            }
          }
          return flowNodeInstanceIdToDuration;
          """;

  public Set<ProcessView> getSupportedViews() {
    return Set.of(PROCESS_VIEW_RAW_DATA);
  }

  @Override
  public ViewResult createEmptyResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return ViewResult.builder().rawData(new ArrayList<>()).build();
  }

  protected void addNewVariablesAndDtoFieldsToTableColumnConfig(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final List<RawDataProcessInstanceDto> rawData) {
    final List<String> variableNames =
        rawData.stream()
            .flatMap(
                rawDataProcessInstanceDto ->
                    rawDataProcessInstanceDto.getVariables().keySet().stream())
            .map(varKey -> VARIABLE_PREFIX + varKey)
            .toList();

    final TableColumnDto tableColumns = context.getReportConfiguration().getTableColumns();
    tableColumns.addCountColumns(CSVUtils.extractAllPrefixedCountKeys());
    tableColumns.addNewAndRemoveUnexpectedVariableColumns(variableNames);
    tableColumns.addNewAndRemoveUnexpectedFlowNodeDurationColumns(
        CSVUtils.extractAllPrefixedFlowNodeKeys(rawData));
    tableColumns.addDtoColumns(extractAllProcessInstanceDtoFieldKeys());
  }

  protected List<String> defKeysToTarget(final List<ReportDataDefinitionDto> definitions) {
    return definitions.stream()
        .map(ReportDataDefinitionDto::getKey)
        .filter(Objects::nonNull)
        .toList();
  }

  protected String sortByField(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return context
        .getReportConfiguration()
        .getSorting()
        .flatMap(ReportSortingDto::getBy)
        .orElse(ProcessInstanceIndex.START_DATE);
  }
}
