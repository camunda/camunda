/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.distributedby.process;

import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.termAggregation;
import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.withSubaggregations;
import static io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy.PROCESS_DISTRIBUTED_BY_PROCESS;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.distributedby.process.ProcessDistributedByProcessInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessDistributedByProcessInterpreterOS
    extends AbstractProcessDistributedByInterpreterOS
    implements ProcessDistributedByProcessInterpreter {

  private final ProcessViewInterpreterFacadeOS viewInterpreter;
  private final ConfigurationService configurationService;
  private final ProcessDefinitionReader processDefinitionReader;

  public ProcessDistributedByProcessInterpreterOS(
      final ProcessViewInterpreterFacadeOS viewInterpreter,
      final ConfigurationService configurationService,
      final ProcessDefinitionReader processDefinitionReader) {
    this.viewInterpreter = viewInterpreter;
    this.configurationService = configurationService;
    this.processDefinitionReader = processDefinitionReader;
  }

  @Override
  public Set<ProcessDistributedBy> getSupportedDistributedBys() {
    return Set.of(PROCESS_DISTRIBUTED_BY_PROCESS);
  }

  @Override
  public Map<String, Aggregation> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery) {
    final Integer size =
        configurationService.getOpenSearchConfiguration().getAggregationBucketLimit();
    final Map<String, SortOrder> order = Map.of("_key", SortOrder.Asc);
    final Aggregation tenantAgg =
        new Aggregation.Builder()
            .terms(
                b ->
                    b.size(size)
                        .order(order)
                        .missing(FieldValue.of(MISSING_TENANT_KEY))
                        .field(tenantField(context)))
            .aggregations(viewInterpreter.createAggregations(context))
            .build();
    return Map.of(
        PROC_DEF_KEY_AGG,
        withSubaggregations(
            termAggregation(definitionKeyField(context), size, order),
            Map.of(
                PROC_DEF_VERSION_AGG,
                withSubaggregations(
                    termAggregation(definitionVersionField(context), size, order),
                    Map.of(TENANT_AGG, tenantAgg)))));
  }

  @Override
  public List<DistributedByResult> retrieveResult(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
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
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Map<String, List<ProcessBucket>> bucketsByDefKey = new HashMap<>();
    final StringTermsAggregate procDefKeyAgg = aggregations.get(PROC_DEF_KEY_AGG).sterms();
    if (procDefKeyAgg != null) {
      for (final StringTermsBucket keyBucket : procDefKeyAgg.buckets().array()) {
        final StringTermsAggregate procDefVersionAgg =
            keyBucket.aggregations().get(PROC_DEF_VERSION_AGG).sterms();
        if (procDefVersionAgg != null) {
          for (final StringTermsBucket versionBucket : procDefVersionAgg.buckets().array()) {
            final StringTermsAggregate tenantTermsAgg =
                versionBucket.aggregations().get(TENANT_AGG).sterms();
            if (tenantTermsAgg != null) {
              final List<ProcessBucket> bucketsForKey =
                  tenantTermsAgg.buckets().array().stream()
                      .map(
                          tenantBucket ->
                              new ProcessBucket(
                                  keyBucket.key(),
                                  versionBucket.key(),
                                  tenantBucket.key(),
                                  tenantBucket.docCount(),
                                  viewInterpreter.retrieveResult(
                                      response, tenantBucket.aggregations(), context)))
                      .toList();
              bucketsByDefKey
                  .computeIfAbsent(keyBucket.key(), key -> new ArrayList<>())
                  .addAll(bucketsForKey);
            }
          }
        }
      }
    }
    return bucketsByDefKey;
  }

  public ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return this.viewInterpreter;
  }

  @Override
  public ProcessDefinitionReader getProcessDefinitionReader() {
    return this.processDefinitionReader;
  }
}
