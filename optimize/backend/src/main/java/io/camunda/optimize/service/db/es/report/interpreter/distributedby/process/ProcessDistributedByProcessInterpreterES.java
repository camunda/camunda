/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.distributedby.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy.PROCESS_DISTRIBUTED_BY_PROCESS;
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
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewMeasure;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.DefinitionVersionHandlingUtil;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class ProcessDistributedByProcessInterpreterES
    extends AbstractProcessDistributedByInterpreterES {
  record ProcessBucket(
      String procDefKey,
      String version,
      String tenant,
      long docCount,
      CompositeCommandResult.ViewResult result) {}

  private static final String PROC_DEF_KEY_AGG = "processDefKeyAgg";
  private static final String PROC_DEF_VERSION_AGG = "processDefVersionAgg";
  private static final String TENANT_AGG = "tenantAgg";
  private static final String MISSING_TENANT_KEY = "noTenant____";

  @Getter private final ProcessViewInterpreterFacadeES viewInterpreter;
  private final ConfigurationService configurationService;
  private final ProcessDefinitionReader processDefinitionReader;

  @Override
  public Set<ProcessDistributedBy> getSupportedDistributedBys() {
    return Set.of(PROCESS_DISTRIBUTED_BY_PROCESS);
  }

  @Override
  public List<AggregationBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final QueryBuilder baseQueryBuilder) {
    final TermsAggregationBuilder tenantAgg =
        AggregationBuilders.terms(TENANT_AGG)
            .size(configurationService.getElasticSearchConfiguration().getAggregationBucketLimit())
            .order(BucketOrder.key(true))
            .missing(MISSING_TENANT_KEY)
            .field(tenantField(context));
    viewInterpreter.createAggregations(context).forEach(tenantAgg::subAggregation);
    return Collections.singletonList(
        AggregationBuilders.terms(PROC_DEF_KEY_AGG)
            .size(configurationService.getElasticSearchConfiguration().getAggregationBucketLimit())
            .order(BucketOrder.key(true))
            .field(definitionKeyField(context))
            .subAggregation(
                AggregationBuilders.terms(PROC_DEF_VERSION_AGG)
                    .size(
                        configurationService
                            .getElasticSearchConfiguration()
                            .getAggregationBucketLimit())
                    .order(BucketOrder.key(true))
                    .field(definitionVersionField(context))
                    .subAggregation(tenantAgg)));
  }

  @Override
  public List<DistributedByResult> retrieveResult(
      SearchResponse response,
      Aggregations aggregations,
      ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final List<CompositeCommandResult.DistributedByResult> results = new ArrayList<>();
    final Map<String, List<ProcessBucket>> bucketsByDefKey =
        extractBucketsByDefKey(response, aggregations, context);
    for (final ReportDataDefinitionDto definition : context.getReportData().getDefinitions()) {
      final CompositeCommandResult.ViewResult result;
      if (bucketsByDefKey.containsKey(definition.getKey())) {
        result = calculateMergedResult(bucketsByDefKey, definition, context);
      } else {
        result = viewInterpreter.createEmptyResult(context);
      }
      results.add(
          createDistributedByResult(
              definition.getIdentifier(), definition.getDisplayName(), result));
    }
    return results;
  }

  @Override
  public List<CompositeCommandResult.DistributedByResult> createEmptyResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return context.getReportData().getDefinitions().stream()
        .map(
            definitionSource ->
                createDistributedByResult(
                    definitionSource.getIdentifier(),
                    definitionSource.getDisplayName(),
                    viewInterpreter.createEmptyResult(context)))
        .toList();
  }

  private CompositeCommandResult.ViewResult calculateMergedResult(
      final Map<String, List<ProcessBucket>> bucketsByDefKey,
      final ReportDataDefinitionDto definition,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final List<ProcessBucket> processBuckets =
        extractResultsToMergeForDefinitionSource(bucketsByDefKey, definition);
    if (processBuckets.isEmpty()) {
      return viewInterpreter.createEmptyResult(context);
    }
    final List<CompositeCommandResult.ViewMeasure> viewMeasures = new ArrayList<>();
    if (context.getPlan().getView().isFrequency()) {
      final Double totalCount =
          processBuckets.stream()
              .map(ProcessBucket::result)
              .mapToDouble(result -> result.getViewMeasures().get(0).getValue())
              .sum();
      viewMeasures.add(CompositeCommandResult.ViewMeasure.builder().value(totalCount).build());
    } else if (context.getPlan().getView() == PROCESS_VIEW_USER_TASK_DURATION) {
      for (final UserTaskDurationTime userTaskDurationTime :
          context.getReportConfiguration().getUserTaskDurationTimes()) {
        for (final AggregationDto aggregationType :
            context.getReportConfiguration().getAggregationTypes()) {
          final Double mergedAggResult =
              calculateMergedAggregationResult(
                  processBuckets, aggregationType, userTaskDurationTime);
          viewMeasures.add(
              CompositeCommandResult.ViewMeasure.builder()
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
            CompositeCommandResult.ViewMeasure.builder()
                .aggregationType(aggregationType)
                .value(mergedAggResult)
                .build());
      }
    }
    return CompositeCommandResult.ViewResult.builder().viewMeasures(viewMeasures).build();
  }

  private Double calculateMergedAggregationResult(
      final List<ProcessBucket> processBuckets,
      final AggregationDto aggregationType,
      final UserTaskDurationTime userTaskDurationTime) {
    final Map<AggregationDto, List<CompositeCommandResult.ViewMeasure>> measuresByAggType =
        processBuckets.stream()
            .map(ProcessBucket::result)
            .flatMap(results -> results.getViewMeasures().stream())
            .filter(measure -> measure.getUserTaskDurationTime() == userTaskDurationTime)
            .collect(Collectors.groupingBy(CompositeCommandResult.ViewMeasure::getAggregationType));
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
    if (!useAllVersions && useLatestVersion) {
      return Optional.of(processDefinitionReader.getLatestVersionToKey(definition.getKey()));
    }
    return Optional.empty();
  }

  private Map<String, List<ProcessBucket>> extractBucketsByDefKey(
      final SearchResponse response,
      final Aggregations aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Map<String, List<ProcessBucket>> bucketsByDefKey = new HashMap<>();
    final Terms procDefKeyAgg = aggregations.get(PROC_DEF_KEY_AGG);
    if (procDefKeyAgg != null) {
      for (final Terms.Bucket keyBucket : procDefKeyAgg.getBuckets()) {
        final Terms procDefVersionAgg = keyBucket.getAggregations().get(PROC_DEF_VERSION_AGG);
        if (procDefVersionAgg != null) {
          for (final Terms.Bucket versionBucket : procDefVersionAgg.getBuckets()) {
            final Terms tenantTermsAgg = versionBucket.getAggregations().get(TENANT_AGG);
            if (tenantTermsAgg != null) {
              final List<ProcessBucket> bucketsForKey =
                  tenantTermsAgg.getBuckets().stream()
                      .map(
                          tenantBucket ->
                              new ProcessBucket(
                                  keyBucket.getKeyAsString(),
                                  versionBucket.getKeyAsString(),
                                  tenantBucket.getKeyAsString(),
                                  tenantBucket.getDocCount(),
                                  viewInterpreter.retrieveResult(
                                      response, tenantBucket.getAggregations(), context)))
                      .toList();
              bucketsByDefKey
                  .computeIfAbsent(keyBucket.getKeyAsString(), key -> new ArrayList<>())
                  .addAll(bucketsForKey);
            }
          }
        }
      }
    }
    return bucketsByDefKey;
  }

  private String definitionKeyField(final ExecutionContext<ProcessReportDataDto, ?> context) {
    return isProcessReport(context)
        ? ProcessInstanceDto.Fields.processDefinitionKey
        : ProcessInstanceDto.Fields.flowNodeInstances
            + "."
            + FlowNodeInstanceDto.Fields.definitionKey;
  }

  private String definitionVersionField(final ExecutionContext<ProcessReportDataDto, ?> context) {
    return isProcessReport(context)
        ? ProcessInstanceDto.Fields.processDefinitionVersion
        : ProcessInstanceDto.Fields.flowNodeInstances
            + "."
            + FlowNodeInstanceDto.Fields.definitionVersion;
  }

  private String tenantField(final ExecutionContext<ProcessReportDataDto, ?> context) {
    return isProcessReport(context)
        ? ProcessInstanceDto.Fields.tenantId
        : ProcessInstanceDto.Fields.flowNodeInstances + "." + FlowNodeInstanceDto.Fields.tenantId;
  }

  private boolean isProcessReport(final ExecutionContext<ProcessReportDataDto, ?> context) {
    return context.getReportData().getView().getEntity() == ProcessViewEntity.PROCESS_INSTANCE;
  }
}
