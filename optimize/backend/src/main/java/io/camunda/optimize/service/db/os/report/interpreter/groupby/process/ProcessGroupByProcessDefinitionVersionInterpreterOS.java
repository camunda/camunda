/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process;

import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.termAggregation;
import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.withSubaggregations;
import static io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy.PROCESS_GROUP_BY_PROCESS_DEFINITION_VERSION;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_DEFINITION_VERSION;

import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.ProcessDistributedByInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessGroupBy;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessGroupByProcessDefinitionVersionInterpreterOS
    extends AbstractProcessGroupByInterpreterOS {

  private static final String PROCESS_DEFINITION_VERSION_AGGREGATION =
      "processDefinitionVersionAgg";

  private final ConfigurationService configurationService;
  private final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter;
  private final ProcessViewInterpreterFacadeOS viewInterpreter;

  public ProcessGroupByProcessDefinitionVersionInterpreterOS(
      final ConfigurationService configurationService,
      final ProcessDistributedByInterpreterFacadeOS distributedByInterpreter,
      final ProcessViewInterpreterFacadeOS viewInterpreter) {
    this.configurationService = configurationService;
    this.distributedByInterpreter = distributedByInterpreter;
    this.viewInterpreter = viewInterpreter;
  }

  @Override
  public Set<ProcessGroupBy> getSupportedGroupBys() {
    return Set.of(PROCESS_GROUP_BY_PROCESS_DEFINITION_VERSION);
  }

  @Override
  public Optional<String> getBaselineCountAggregationField(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    // Percentage reports are relative to the unfiltered instance count. When grouped by version,
    // that denominator must be computed per version, so we aggregate baseline counts on the
    // version field. Other views use the report-level baseline count.
    return context.getReportData().getViewProperties().contains(ViewProperty.PERCENTAGE)
        ? Optional.of(PROCESS_DEFINITION_VERSION)
        : Optional.empty();
  }

  @Override
  public Map<String, Aggregation> createAggregation(
      final Query query,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final int size = configurationService.getOpenSearchConfiguration().getAggregationBucketLimit();
    final Map<String, SortOrder> order = Map.of("_key", SortOrder.Asc);
    final Aggregation processDefinitionVersionAggregation =
        withSubaggregations(
            termAggregation(PROCESS_DEFINITION_VERSION, size, order),
            distributedByInterpreter.createAggregations(context, query));
    return Map.of(PROCESS_DEFINITION_VERSION_AGGREGATION, processDefinitionVersionAggregation);
  }

  @Override
  protected void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse<RawResult> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final StringTermsAggregate processDefinitionVersionAggregation =
        response.aggregations().get(PROCESS_DEFINITION_VERSION_AGGREGATION).sterms();
    final List<GroupByResult> groupedData = new ArrayList<>();
    // Save the global baseline count so we can restore it after the loop; the per-bucket override
    // is only relevant inside the view interpreter and must not leak into the report-level count.
    final long globalBaselineCount = context.getUnfilteredTotalInstanceCount();
    final Map<String, Long> perGroupCounts = context.getUnfilteredInstanceCountsByGroupKey();
    try {
      for (final StringTermsBucket processDefinitionVersionBucket :
          processDefinitionVersionAggregation.buckets().array()) {
        final String processDefinitionVersion = processDefinitionVersionBucket.key();
        if (!perGroupCounts.isEmpty()) {
          context.setUnfilteredTotalInstanceCount(
              perGroupCounts.getOrDefault(processDefinitionVersion, 0L));
        }
        final List<CompositeCommandResult.DistributedByResult> distributedByResult =
            distributedByInterpreter.retrieveResult(
                response, processDefinitionVersionBucket.aggregations(), context);
        groupedData.add(
            GroupByResult.createGroupByResult(processDefinitionVersion, distributedByResult));
      }
    } finally {
      context.setUnfilteredTotalInstanceCount(globalBaselineCount);
    }
    compositeCommandResult.setGroups(groupedData);
    compositeCommandResult.setGroupByKeyOfNumericType(true);
    compositeCommandResult.setDistributedByKeyOfNumericType(
        distributedByInterpreter.isKeyOfNumericType(context));
  }

  @Override
  public ProcessDistributedByInterpreterFacadeOS getDistributedByInterpreter() {
    return distributedByInterpreter;
  }

  @Override
  public ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return viewInterpreter;
  }
}
