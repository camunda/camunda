/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by.process;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessDistributedByDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.util.DefinitionVersionHandlingUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;

@Component
@RequiredArgsConstructor
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ProcessDistributedByProcess extends ProcessDistributedByPart {

  private static final String PROC_DEF_KEY_AGG = "processDefKeyAgg";
  private static final String PROC_DEF_VERSION_AGG = "processDefVersionAgg";
  private static final String TENANT_AGG = "tenantAgg";
  private static final String MISSING_TENANT_KEY = "noTenant____";

  private final ConfigurationService configurationService;
  private final ProcessDefinitionReader processDefinitionReader;

  @Override
  public List<AggregationBuilder> createAggregations(final ExecutionContext<ProcessReportDataDto> context) {
    return viewPart.createAggregations(context)
      .stream().map(viewAgg -> AggregationBuilders
        .terms(PROC_DEF_KEY_AGG)
        .size(configurationService.getEsAggregationBucketLimit())
        .order(BucketOrder.key(true))
        .field(ProcessInstanceDto.Fields.processDefinitionKey)
        .subAggregation(
          AggregationBuilders.terms(PROC_DEF_VERSION_AGG)
            .size(configurationService.getEsAggregationBucketLimit())
            .order(BucketOrder.key(true))
            .field(ProcessInstanceDto.Fields.processDefinitionVersion)
            .subAggregation(
              AggregationBuilders.terms(TENANT_AGG)
                .size(configurationService.getEsAggregationBucketLimit())
                .order(BucketOrder.key(true))
                .missing(MISSING_TENANT_KEY)
                .field(ProcessInstanceDto.Fields.tenantId)
                .subAggregation(viewAgg)
            )
        )).collect(Collectors.toList());
  }

  @Override
  public List<CompositeCommandResult.DistributedByResult> retrieveResult(final SearchResponse response,
                                                                         final Aggregations aggregations,
                                                                         final ExecutionContext<ProcessReportDataDto> context) {
    Map<String, List<ProcessBucket>> bucketsByDefKey = extractBucketsByDefKey(response, aggregations, context);
    List<CompositeCommandResult.DistributedByResult> results = new ArrayList<>();
    for (ReportDataDefinitionDto definition : context.getReportData().getDefinitions()) {
      if (bucketsByDefKey.containsKey(definition.getKey())) {
        final Double combinedResult = extractResultsToMergeForDefinitionSource(bucketsByDefKey, definition).stream()
          .mapToDouble(result -> result.getViewMeasures().get(0).getValue())
          .sum();
        results.add(createDistributedByResult(
          definition.getIdentifier(),
          definition.getDisplayName(),
          CompositeCommandResult.ViewResult.builder()
            .viewMeasure(CompositeCommandResult.ViewMeasure.builder().value(combinedResult).build())
            .build()
        ));
      } else {
        results.add(createDistributedByResult(
          definition.getIdentifier(),
          definition.getDisplayName(),
          viewPart.createEmptyResult(context)
        ));
      }
    }
    return results;
  }

  private List<CompositeCommandResult.ViewResult> extractResultsToMergeForDefinitionSource(
    final Map<String, List<ProcessBucket>> bucketsByDefKey, final ReportDataDefinitionDto definition) {
    final boolean useAllVersions =
      DefinitionVersionHandlingUtil.isDefinitionVersionSetToAll(definition.getVersions());
    final boolean useLatestVersion =
      DefinitionVersionHandlingUtil.isDefinitionVersionSetToLatest(definition.getVersions());
    final Optional<String> latestVersion = getLatestVersionForDefinition(
      definition, useAllVersions, useLatestVersion);
    return bucketsByDefKey.get(definition.getKey())
      .stream()
      .filter(bucketForKey -> {
        if (useAllVersions) {
          return true;
        } else if (useLatestVersion && latestVersion.isPresent()) {
          return bucketForKey.getVersion().equals(latestVersion.get());
        } else {
          return (definition.getVersions().contains(bucketForKey.getVersion()));
        }
      })
      .filter(bucketForKey -> (definition.getTenantIds().contains(bucketForKey.getTenant())) ||
        (bucketForKey.getTenant().equals(MISSING_TENANT_KEY) && definition.getTenantIds().contains(null)))
      .map(ProcessBucket::getResult)
      .collect(Collectors.toList());
  }

  private Optional<String> getLatestVersionForDefinition(final ReportDataDefinitionDto definition,
                                                         final boolean useAllVersions,
                                                         final boolean useLatestVersion) {
    if (!useAllVersions && useLatestVersion) {
      return Optional.of(processDefinitionReader.getLatestVersionToKey(definition.getKey()));
    }
    return Optional.empty();
  }

  private Map<String, List<ProcessBucket>> extractBucketsByDefKey(final SearchResponse response,
                                                                  final Aggregations aggregations,
                                                                  final ExecutionContext<ProcessReportDataDto> context) {
    Map<String, List<ProcessBucket>> bucketsByDefKey = new HashMap<>();
    final Terms procDefKeyAgg = aggregations.get(PROC_DEF_KEY_AGG);
    if (procDefKeyAgg != null) {
      for (Terms.Bucket keyBucket : procDefKeyAgg.getBuckets()) {
        final Terms procDefVersionAgg = keyBucket.getAggregations().get(PROC_DEF_VERSION_AGG);
        if (procDefVersionAgg != null) {
          for (Terms.Bucket versionBucket : procDefVersionAgg.getBuckets()) {
            final Terms tenantTermsAgg = versionBucket.getAggregations().get(TENANT_AGG);
            if (tenantTermsAgg != null) {
              final List<ProcessBucket> bucketsForKey = tenantTermsAgg.getBuckets().stream()
                .map(tenantBucket -> new ProcessBucket(
                  keyBucket.getKeyAsString(),
                  versionBucket.getKeyAsString(),
                  tenantBucket.getKeyAsString(),
                  viewPart.retrieveResult(response, tenantBucket.getAggregations(), context)
                )).collect(Collectors.toList());
              bucketsByDefKey.computeIfAbsent(keyBucket.getKeyAsString(), key -> new ArrayList<>())
                .addAll(bucketsForKey);
            }
          }
        }
      }
    }
    return bucketsByDefKey;
  }

  @Override
  protected void addAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setDistributedBy(new ProcessDistributedByDto());
  }

  @AllArgsConstructor
  @Getter
  private static class ProcessBucket {
    private final String procDefKey;
    private final String version;
    private final String tenant;
    private final CompositeCommandResult.ViewResult result;
  }

}
