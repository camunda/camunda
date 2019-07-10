/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.util;

import com.google.common.collect.Sets;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportHyperMapResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResult;
import org.camunda.optimize.dto.optimize.query.report.single.result.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class GroupByFlowNodeCommandUtil {

  private GroupByFlowNodeCommandUtil() {
  }

  public static <MAP extends ProcessReportMapResult<V>, V extends Comparable> void filterResultData(
    final CommandContext<SingleProcessReportDefinitionDto> commandContext,
    final ReportEvaluationResult<MAP, SingleProcessReportDefinitionDto> evaluationResult) {

    final ProcessReportDataDto reportData = commandContext.getReportDefinition().getData();
    // if version is set to all, filter for only latest flow nodes
    if (ReportConstants.ALL_VERSIONS.equalsIgnoreCase(reportData.getProcessDefinitionVersion())) {
      getProcessDefinitionIfAvailable(commandContext, reportData)
        .ifPresent(processDefinition -> {
          final Map<String, String> flowNodeNames = processDefinition.getFlowNodeNames();

          final List<MapResultEntryDto<V>> collect = evaluationResult.getResultAsDto()
            .getData()
            .stream()
            .filter(resultEntry -> flowNodeNames.containsKey(resultEntry.getKey()))
            .collect(Collectors.toList());

          evaluationResult.getResultAsDto().setData(collect);
        });
    }
  }

  public static void filterHyperMapResultData(
    final CommandContext<SingleProcessReportDefinitionDto> commandContext,
    final ReportEvaluationResult<ProcessReportHyperMapResult, SingleProcessReportDefinitionDto> evaluationResult) {

    final ProcessReportDataDto reportData = commandContext.getReportDefinition().getData();
    // if version is set to all, filter for only latest flow nodes
    if (ReportConstants.ALL_VERSIONS.equalsIgnoreCase(reportData.getProcessDefinitionVersion())) {
      getProcessDefinitionIfAvailable(commandContext, reportData)
        .ifPresent(processDefinition -> {
          final Map<String, String> userTaskNames = processDefinition.getUserTaskNames();

          evaluationResult.getResultAsDto()
            .getData()
            .forEach(hyperMapEntry -> {
              List<MapResultEntryDto<Long>> hyperMapValue = hyperMapEntry
                .getValue()
                .stream()
                .filter(resultEntry -> userTaskNames.containsKey(resultEntry.getKey()))
                .collect(Collectors.toList());
              hyperMapEntry.setValue(hyperMapValue);
            });
        });
    }
  }

  public static <MAP extends ProcessReportMapResult<V>, V extends Comparable> void enrichResultData(
    final CommandContext<SingleProcessReportDefinitionDto> commandContext,
    final ReportEvaluationResult<MAP, SingleProcessReportDefinitionDto> evaluationResult,
    final Supplier<V> createNewEmptyResult,
    final Function<ProcessDefinitionOptimizeDto, Map<String, String>> flowNodeNameExtractor) {

    final ProcessReportDataDto reportData = commandContext.getReportDefinition().getData();
    getProcessDefinitionIfAvailable(commandContext, reportData)
      .ifPresent(processDefinition -> {
        final Map<String, String> flowNodeNames = flowNodeNameExtractor.apply(processDefinition);

        Set<String> flowNodeKeysWithResult = new HashSet<>();
        evaluationResult.getResultAsDto().getData().forEach(entry -> {
          entry.setLabel(flowNodeNames.get(entry.getKey()));
          flowNodeKeysWithResult.add(entry.getKey());
        });
        Set<String> allFlowNodeKeys = flowNodeNames.keySet();
        Set<String> difference = Sets.difference(allFlowNodeKeys, flowNodeKeysWithResult);
        difference.forEach(flowNodeKey -> {
          MapResultEntryDto<V> emptyResult = new MapResultEntryDto<>(flowNodeKey, createNewEmptyResult.get());
          emptyResult.setLabel(flowNodeNames.get(flowNodeKey));
          evaluationResult.getResultAsDto().getData().add(emptyResult);
        });

      });
  }

  public static void enrichResultData(
    final CommandContext<SingleProcessReportDefinitionDto> commandContext,
    final ReportEvaluationResult<ProcessReportHyperMapResult, SingleProcessReportDefinitionDto> evaluationResult) {

    final ProcessReportDataDto reportData = commandContext.getReportDefinition().getData();
    getProcessDefinitionIfAvailable(commandContext, reportData)
      .ifPresent(processDefinition -> {
        final Map<String, String> flowNodeNames = processDefinition.getUserTaskNames();

        List<HyperMapResultEntryDto<Long>> resultData = evaluationResult.getResultAsDto().getData();
        resultData
          .forEach(groupedByKeyEntry -> groupedByKeyEntry.getValue().forEach(
            distributedByEntry -> {
              distributedByEntry.setLabel(flowNodeNames.get(distributedByEntry.getKey()));
            })
          );
        Set<String> allFlowNodeKeys = flowNodeNames.keySet();
        resultData.forEach(groupByEntry -> {
          allFlowNodeKeys.stream()
            .filter(key -> !groupByEntry.getDataEntryForKey(key).isPresent())
            .forEach(taskId -> groupByEntry.getValue()
              // We are enriching the Result Data with not executed user tasks.
              // For those user tasks, count value is set to null since in the front-end
              // we handle the cases of null (not being executed) and
              // 0 (it might have been executed, but has a very low value) differently.
              .add(new MapResultEntryDto<>(taskId, null, flowNodeNames.get(taskId))));
          groupByEntry.getValue()
            .sort(Comparator.comparing(MapResultEntryDto::getLabel));
        });
      });
  }

  private static Optional<ProcessDefinitionOptimizeDto> getProcessDefinitionIfAvailable(
    final CommandContext<SingleProcessReportDefinitionDto> commandContext,
    final ProcessReportDataDto reportData) {

    return commandContext
      .getProcessDefinitionReader()
      .getFullyImportedProcessDefinition(
        reportData.getProcessDefinitionKey(),
        reportData.getProcessDefinitionVersion(),
        reportData.getTenantIds().stream()
          // to get a null value if the first element is either absent or null
          .map(Optional::ofNullable).findFirst().flatMap(Function.identity())
          .orElse(null)
      );
  }

}
