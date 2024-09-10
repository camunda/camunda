/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.plan.process;

import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.VARIABLE_PREFIX;
import static io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan.PROCESS_RAW_PROCESS_INSTANCE_DATA_GROUP_BY_NONE;
import static io.camunda.optimize.service.export.CSVUtils.extractAllProcessInstanceDtoFieldKeys;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.filter.ProcessQueryFilterEnhancer;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.ProcessGroupByInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.export.CSVUtils;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class RawProcessInstanceDataGroupByNoneExecutionPlanInterpreterES
    extends AbstractProcessExecutionPlanInterpreterES {
  @Getter private final ProcessGroupByInterpreterFacadeES groupByInterpreter;
  @Getter private final ProcessViewInterpreterFacadeES viewInterpreter;
  @Getter private final OptimizeElasticsearchClient esClient;
  @Getter private final ProcessDefinitionReader processDefinitionReader;
  @Getter private final ProcessQueryFilterEnhancer queryFilterEnhancer;

  @Override
  public Set<ProcessExecutionPlan> getSupportedExecutionPlans() {
    return Set.of(PROCESS_RAW_PROCESS_INSTANCE_DATA_GROUP_BY_NONE);
  }

  @Override
  public CommandEvaluationResult<Object> interpret(
      ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> executionContext) {
    final CommandEvaluationResult<Object> commandResult = super.interpret(executionContext);
    addNewVariablesAndDtoFieldsToTableColumnConfig(executionContext, commandResult);
    return commandResult;
  }

  @SuppressWarnings("unchecked")
  private void addNewVariablesAndDtoFieldsToTableColumnConfig(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> executionContext,
      final CommandEvaluationResult<Object> result) {
    List<RawDataProcessInstanceDto> rawDataProcessInstanceDtos;
    try {
      rawDataProcessInstanceDtos = (List<RawDataProcessInstanceDto>) result.getFirstMeasureData();
    } catch (ClassCastException e) {
      throw new OptimizeRuntimeException("Unexpected report evaluation result type", e);
    }

    final List<String> variableNames =
        rawDataProcessInstanceDtos.stream()
            .flatMap(
                rawDataProcessInstanceDto ->
                    rawDataProcessInstanceDto.getVariables().keySet().stream())
            .map(varKey -> VARIABLE_PREFIX + varKey)
            .toList();

    TableColumnDto tableColumns =
        executionContext.getReportData().getConfiguration().getTableColumns();
    tableColumns.addNewAndRemoveUnexpectedVariableColumns(variableNames);
    tableColumns.addNewAndRemoveUnexpectedFlowNodeDurationColumns(
        CSVUtils.extractAllPrefixedFlowNodeKeys(rawDataProcessInstanceDtos));
    tableColumns.addCountColumns(CSVUtils.extractAllPrefixedCountKeys());
    tableColumns.addDtoColumns(extractAllProcessInstanceDtoFieldKeys());
  }
}
