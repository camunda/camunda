/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.identity;

import static io.camunda.optimize.service.db.es.filter.util.ModelElementFilterQueryUtilES.createUserTaskFlowNodeTypeFilter;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.FilterAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.NestedAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import co.elastic.clients.util.NamedValue;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.filter.util.ModelElementFilterQueryUtilES;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.AbstractProcessGroupByInterpreterES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.groupby.process.identity.ProcessGroupByIdentityInterpreterHelper;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class AbstractProcessGroupByIdentityInterpreterES
    extends AbstractProcessGroupByInterpreterES {
  private static final String GROUP_BY_IDENTITY_TERMS_AGGREGATION = "identities";
  private static final String FLOW_NODES_AGGREGATION = "flowNodeInstances";
  private static final String USER_TASKS_AGGREGATION = "userTasks";
  private static final String FILTERED_USER_TASKS_AGGREGATION = "filteredUserTasks";
  // temporary GROUP_BY_IDENTITY_MISSING_KEY to ensure no overlap between this label and userTask
  // names
  private static final String GROUP_BY_IDENTITY_MISSING_KEY = "unassignedUserTasks___";

  protected abstract ConfigurationService getConfigurationService();

  protected abstract DefinitionService getDefinitionService();

  protected abstract ProcessGroupByIdentityInterpreterHelper getHelper();

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregation(
      final BoolQuery boolQuery,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder().nested(n -> n.path(FLOW_NODE_INSTANCES));
    builder.aggregations(
        USER_TASKS_AGGREGATION,
        Aggregation.of(
            a ->
                a.filter(f -> f.bool(createUserTaskFlowNodeTypeFilter().build()))
                    .aggregations(
                        // it's possible to do report evaluations over several definitions
                        // versions. However, only the most recent
                        // one is used to decide which user tasks should be taken into
                        // account. To make sure that we only fetch
                        // assignees related to this definition version we filter for
                        // userTasks that only occur in the latest
                        // version.
                        FILTERED_USER_TASKS_AGGREGATION,
                        Aggregation.of(
                            aa ->
                                aa.filter(
                                        f ->
                                            f.bool(
                                                ModelElementFilterQueryUtilES
                                                    .createInclusiveFlowNodeIdFilterQuery(
                                                        context.getReportData(),
                                                        getHelper()
                                                            .getUserTaskIds(
                                                                context.getReportData()),
                                                        context.getFilterContext(),
                                                        getDefinitionService())
                                                    .build()))
                                    .aggregations(
                                        GROUP_BY_IDENTITY_TERMS_AGGREGATION,
                                        Aggregation.of(
                                            aaa -> {
                                              final Aggregation.Builder.ContainerBuilder terms =
                                                  aaa.terms(
                                                      t ->
                                                          t.size(
                                                                  getConfigurationService()
                                                                      .getElasticSearchConfiguration()
                                                                      .getAggregationBucketLimit())
                                                              .field(
                                                                  FLOW_NODE_INSTANCES
                                                                      + "."
                                                                      + getIdentityField())
                                                              .order(
                                                                  NamedValue.of(
                                                                      "_key",
                                                                      co.elastic.clients
                                                                          .elasticsearch._types
                                                                          .SortOrder.Asc))
                                                              .missing(
                                                                  GROUP_BY_IDENTITY_MISSING_KEY));
                                              getDistributedByInterpreter()
                                                  .createAggregations(context, boolQuery)
                                                  .forEach(
                                                      (k, v) -> terms.aggregations(k, v.build()));
                                              return terms;
                                            }))))));

    return Map.of(FLOW_NODES_AGGREGATION, builder);
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final ResponseBody<?> response,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Map<String, Aggregate> aggregations = response.aggregations();
    final NestedAggregate flowNodes = aggregations.get(FLOW_NODES_AGGREGATION).nested();
    final FilterAggregate userTasks = flowNodes.aggregations().get(USER_TASKS_AGGREGATION).filter();
    final FilterAggregate filteredUserTasks =
        userTasks.aggregations().get(FILTERED_USER_TASKS_AGGREGATION).filter();
    final List<GroupByResult> groupedData =
        getByIdentityAggregationResults(response, filteredUserTasks, context);

    compositeCommandResult.setGroups(groupedData);
    compositeCommandResult.setGroupBySorting(
        context
            .getReportConfiguration()
            .getSorting()
            .orElseGet(() -> new ReportSortingDto(ReportSortingDto.SORT_BY_LABEL, SortOrder.ASC)));
  }

  protected abstract String getIdentityField();

  protected abstract IdentityType getIdentityType();

  private List<GroupByResult> getByIdentityAggregationResults(
      final ResponseBody<?> response,
      final FilterAggregate filteredUserTasks,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final StringTermsAggregate byIdentityAggregation =
        filteredUserTasks.aggregations().get(GROUP_BY_IDENTITY_TERMS_AGGREGATION).sterms();
    final List<GroupByResult> groupedData = new ArrayList<>();
    for (final StringTermsBucket identityBucket : byIdentityAggregation.buckets().array()) {
      final String key = identityBucket.key().stringValue();
      final List<DistributedByResult> distributedByResults =
          getDistributedByInterpreter()
              .retrieveResult(response, identityBucket.aggregations(), context);

      if (GROUP_BY_IDENTITY_MISSING_KEY.equals(key)) {
        distributedByResults.forEach(
            result ->
                result
                    .getViewResult()
                    .getViewMeasures()
                    .forEach(
                        measure ->
                            Optional.ofNullable(measure.getAggregationType())
                                .map(AggregationDto::getType)
                                .ifPresent(
                                    aggregationType -> {
                                      if (AggregationType.SUM.equals(aggregationType)
                                          && (measure.getValue() != null
                                              && measure.getValue() == 0)) {
                                        measure.setValue(null);
                                      }
                                    })));
      }

      // ensure missing identity bucket is excluded if its empty
      final boolean resultIsEmpty =
          distributedByResults.isEmpty()
              || distributedByResults.stream()
                  .map(DistributedByResult::getViewResult)
                  .map(CompositeCommandResult.ViewResult::getViewMeasures)
                  .flatMap(Collection::stream)
                  .allMatch(
                      viewMeasure ->
                          viewMeasure.getValue() == null || viewMeasure.getValue() == 0.0);
      if (resultIsEmpty) {
        continue;
      }

      groupedData.add(
          GroupByResult.createGroupByResult(
              key,
              getHelper().resolveIdentityName(key, this::getIdentityType),
              distributedByResults));
    }
    return groupedData;
  }
}
