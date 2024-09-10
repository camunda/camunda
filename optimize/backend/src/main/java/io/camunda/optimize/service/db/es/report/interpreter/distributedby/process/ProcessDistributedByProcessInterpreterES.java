/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.distributedby.process;

import static io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy.PROCESS_DISTRIBUTED_BY_PROCESS;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.distributedby.process.ProcessDistributedByProcessInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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
    extends AbstractProcessDistributedByInterpreterES
    implements ProcessDistributedByProcessInterpreter {
  @Getter private final ProcessViewInterpreterFacadeES viewInterpreter;
  private final ConfigurationService configurationService;
  @Getter private final ProcessDefinitionReader processDefinitionReader;

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
    return retrieveResult(bucketsByDefKey, context);
  }

  @Override
  public ViewResult emptyViewResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return viewInterpreter.createEmptyResult(context);
  }

  @Override
  public List<CompositeCommandResult.DistributedByResult> createEmptyResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return emptyResult(context);
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
}
