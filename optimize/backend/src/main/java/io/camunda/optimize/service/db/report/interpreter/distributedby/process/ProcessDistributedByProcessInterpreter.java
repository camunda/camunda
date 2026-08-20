/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.distributedby.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_USER_TASK_DURATION;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.process.FlowNodeInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewMeasure;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.DefinitionVersionHandlingUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public interface ProcessDistributedByProcessInterpreter {

  String PROC_DEF_KEY_AGG = "processDefKeyAgg";
  String PROC_DEF_VERSION_AGG = "processDefVersionAgg";
  String TENANT_AGG = "tenantAgg";
  String MISSING_TENANT_KEY = "noTenant____";

  ProcessDefinitionReader getProcessDefinitionReader();

  ViewResult emptyViewResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context);

  default String definitionKeyField(final ExecutionContext<ProcessReportDataDto, ?> context) {
    return isProcessReport(context)
        ? ProcessInstanceDto.Fields.processDefinitionKey
        : ProcessInstanceDto.Fields.flowNodeInstances
            + "."
            + FlowNodeInstanceDto.Fields.definitionKey;
  }

  default String definitionVersionField(final ExecutionContext<ProcessReportDataDto, ?> context) {
    return isProcessReport(context)
        ? ProcessInstanceDto.Fields.processDefinitionVersion
        : ProcessInstanceDto.Fields.flowNodeInstances
            + "."
            + FlowNodeInstanceDto.Fields.definitionVersion;
  }

  default String tenantField(final ExecutionContext<ProcessReportDataDto, ?> context) {
    return isProcessReport(context)
        ? ProcessInstanceDto.Fields.tenantId
        : ProcessInstanceDto.Fields.flowNodeInstances + "." + FlowNodeInstanceDto.Fields.tenantId;
  }

  default List<DistributedByResult> emptyResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return context.getReportData().getDefinitions().stream()
        .map(
            definitionSource ->
                createDistributedByResult(
                    definitionSource.getIdentifier(),
                    definitionSource.getDisplayName(),
                    emptyViewResult(context)))
        .toList();
  }

  default List<DistributedByResult> retrieveResult(
      final Map<String, List<ProcessBucket>> bucketsByDefKey,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return context.getReportData().getDefinitions().stream()
        .map(
            definition -> {
              final CompositeCommandResult.ViewResult result =
                  bucketsByDefKey.containsKey(definition.getKey())
                      ? calculateMergedResult(bucketsByDefKey, definition, context)
                      : emptyViewResult(context);
              return createDistributedByResult(
                  definition.getIdentifier(), definition.getDisplayName(), result);
            })
        .toList();
  }

  private CompositeCommandResult.ViewResult calculateMergedResult(
      final Map<String, List<ProcessBucket>> bucketsByDefKey,
      final ReportDataDefinitionDto definition,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final List<ProcessBucket> processBuckets =
        extractResultsToMergeForDefinitionSource(bucketsByDefKey, definition);
    if (processBuckets.isEmpty()) {
      return emptyViewResult(context);
    }
    final List<ViewMeasure> viewMeasures = new ArrayList<>();
    if (context.getPlan().getView().isFrequency()) {
      final Double totalCount =
          processBuckets.stream()
              .map(ProcessBucket::result)
              .mapToDouble(result -> result.getViewMeasures().get(0).getValue())
              .sum();
      viewMeasures.add(ViewMeasure.builder().value(totalCount).build());
    } else if (context.getPlan().getView() == PROCESS_VIEW_USER_TASK_DURATION) {
      for (final UserTaskDurationTime userTaskDurationTime :
          context.getReportConfiguration().getUserTaskDurationTimes()) {
        for (final AggregationDto aggregationType :
            context.getReportConfiguration().getAggregationTypes()) {
          final Double mergedAggResult =
              calculateMergedAggregationResult(
                  processBuckets, aggregationType, userTaskDurationTime);
          viewMeasures.add(
              ViewMeasure.builder()
                  .aggregationType(aggregationType)
                  .userTaskDurationTime(userTaskDurationTime)
                  .value(mergedAggResult)
                  .build());
        }
      }
    } else {
      for (final AggregationDto aggregationType :
          context.getReportConfiguration().getAggregationTypes()) {
        final Double mergedAggResult =
            calculateMergedAggregationResult(processBuckets, aggregationType, null);
        viewMeasures.add(
            ViewMeasure.builder().aggregationType(aggregationType).value(mergedAggResult).build());
      }
    }
    return CompositeCommandResult.ViewResult.builder().viewMeasures(viewMeasures).build();
  }

  private Double calculateMergedAggregationResult(
      final List<ProcessBucket> processBuckets,
      final AggregationDto aggregationType,
      final UserTaskDurationTime userTaskDurationTime) {
    final Map<AggregationDto, List<ViewMeasure>> measuresByAggType =
        processBuckets.stream()
            .map(ProcessBucket::result)
            .flatMap(results -> results.getViewMeasures().stream())
            .filter(measure -> measure.getUserTaskDurationTime() == userTaskDurationTime)
            .collect(Collectors.groupingBy(ViewMeasure::getAggregationType));
    final Double mergedAggResult;
    switch (aggregationType.getType()) {
      case MAX ->
          mergedAggResult =
              measuresByAggType.getOrDefault(aggregationType, Collections.emptyList()).stream()
                  .mapToDouble(ViewMeasure::getValue)
                  .max()
                  .orElse(0.0);
      case MIN ->
          mergedAggResult =
              measuresByAggType.getOrDefault(aggregationType, Collections.emptyList()).stream()
                  .mapToDouble(ViewMeasure::getValue)
                  .min()
                  .orElse(0.0);
      case SUM ->
          mergedAggResult =
              measuresByAggType.getOrDefault(aggregationType, Collections.emptyList()).stream()
                  .mapToDouble(ViewMeasure::getValue)
                  .sum();
      case AVERAGE -> {
        final double totalDocCount =
            processBuckets.stream().mapToDouble(ProcessBucket::docCount).sum();
        // We must check to avoid a potential division by zero
        if (totalDocCount == 0) {
          mergedAggResult = null;
        } else {
          final double totalValueSum =
              processBuckets.stream()
                  .map(
                      bucket -> {
                        final Optional<ViewMeasure> avgMeasure =
                            bucket.result.getViewMeasures().stream()
                                .filter(
                                    measure ->
                                        measure.getAggregationType().getType()
                                            == AggregationType.AVERAGE)
                                .findFirst();
                        return avgMeasure.map(measure -> Pair.of(bucket, measure.getValue()));
                      })
                  .filter(Optional::isPresent)
                  .map(Optional::get)
                  .mapToDouble(pair -> pair.getLeft().docCount * pair.getRight())
                  .sum();
          mergedAggResult = totalValueSum / totalDocCount;
        }
      }
      // We cannot support percentile aggregation types with this distribution as the information
      // is lost on merging
      // of buckets
      case PERCENTILE -> mergedAggResult = null;
      default ->
          throw new OptimizeRuntimeException(
              String.format("%s is not a valid Aggregation type", aggregationType));
    }
    return mergedAggResult;
  }

  private List<ProcessBucket> extractResultsToMergeForDefinitionSource(
      final Map<String, List<ProcessBucket>> bucketsByDefKey,
      final ReportDataDefinitionDto definition) {
    final boolean useAllVersions =
        DefinitionVersionHandlingUtil.isDefinitionVersionSetToAll(definition.getVersions());
    final boolean useLatestVersion =
        DefinitionVersionHandlingUtil.isDefinitionVersionSetToLatest(definition.getVersions());
    final Optional<String> latestVersion =
        getLatestVersionForDefinition(definition, useAllVersions, useLatestVersion);
    return bucketsByDefKey.get(definition.getKey()).stream()
        .filter(
            bucketForKey -> {
              if (useAllVersions) {
                return true;
              } else if (useLatestVersion && latestVersion.isPresent()) {
                return bucketForKey.version.equals(latestVersion.get());
              } else {
                return (definition.getVersions().contains(bucketForKey.version));
              }
            })
        .filter(
            bucketForKey ->
                (definition.getTenantIds().contains(bucketForKey.tenant))
                    || (bucketForKey.tenant.equals(MISSING_TENANT_KEY)
                        && definition.getTenantIds().contains(null)))
        .collect(Collectors.toList());
  }

  private Optional<String> getLatestVersionForDefinition(
      final ReportDataDefinitionDto definition,
      final boolean useAllVersions,
      final boolean useLatestVersion) {
    return !useAllVersions && useLatestVersion
        ? Optional.of(getProcessDefinitionReader().getLatestVersionToKey(definition.getKey()))
        : Optional.empty();
  }

  private boolean isProcessReport(final ExecutionContext<ProcessReportDataDto, ?> context) {
    return context.getReportData().getView().getEntity() == ProcessViewEntity.PROCESS_INSTANCE;
  }

  record ProcessBucket(
      String procDefKey,
      String version,
      String tenant,
      long docCount,
      CompositeCommandResult.ViewResult result) {}
}
