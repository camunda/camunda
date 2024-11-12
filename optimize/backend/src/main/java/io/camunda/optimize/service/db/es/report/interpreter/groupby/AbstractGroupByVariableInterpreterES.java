/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby;

import static io.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static io.camunda.optimize.service.db.es.filter.util.ModelElementFilterQueryUtilES.createModelElementAggregationFilter;
import static io.camunda.optimize.service.db.es.report.interpreter.util.FilterLimitedAggregationUtilES.FILTER_LIMITED_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.FILTERED_INSTANCE_COUNT_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.FILTERED_VARIABLES_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.MISSING_VARIABLES_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.NESTED_FLOWNODE_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.NESTED_VARIABLE_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.VARIABLES_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.VARIABLES_INSTANCE_COUNT_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.VARIABLE_HISTOGRAM_AGGREGATION;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_FLOW_NODE_DURATION;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_FLOW_NODE_FREQUENCY;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.FilterAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.NestedAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.ReverseNestedAggregate;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.context.VariableAggregationContextES;
import io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractGroupByVariableInterpreterES<
        DATA extends SingleReportDataDto, PLAN extends ExecutionPlan>
    extends AbstractGroupByInterpreterES<DATA, PLAN> {

  public static final String FILTERED_FLOW_NODE_AGGREGATION = "filteredFlowNodeAggregation";

  public AbstractGroupByVariableInterpreterES() {}

  protected abstract VariableAggregationServiceES getVariableAggregationService();

  protected abstract DefinitionService getDefinitionService();

  protected abstract String getVariableName(final ExecutionContext<DATA, PLAN> context);

  protected abstract VariableType getVariableType(final ExecutionContext<DATA, PLAN> context);

  protected abstract String getNestedVariableNameFieldLabel();

  protected abstract String getNestedVariableTypeField();

  protected abstract String getNestedVariableValueFieldLabel(final VariableType type);

  protected abstract String getVariablePath();

  protected abstract BoolQuery.Builder getVariableUndefinedOrNullQuery(
      final ExecutionContext<DATA, PLAN> context);

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(
      final ExecutionContext<DATA, PLAN> context, final Query baseQuery) {
    if (isGroupedByNumberVariable(getVariableType(context))
        || VariableType.DATE.equals(getVariableType(context))) {
      return Optional.of(
          getVariableAggregationService()
              .getVariableMinMaxStats(
                  getVariableType(context),
                  getVariableName(context),
                  getVariablePath(),
                  getNestedVariableNameFieldLabel(),
                  getNestedVariableValueFieldLabel(getVariableType(context)),
                  getIndexNames(context),
                  baseQuery));
    }
    return Optional.empty();
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregation(
      final BoolQuery boolQuery, final ExecutionContext<DATA, PLAN> context) {
    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder().reverseNested(r -> r);
    createDistributedBySubAggregations(context, boolQuery).forEach(builder::aggregations);
    final VariableAggregationContextES varAggContext =
        VariableAggregationContextES.builder()
            .variableName(getVariableName(context))
            .variableType(getVariableType(context))
            .variablePath(getVariablePath())
            .nestedVariableNameField(getNestedVariableNameFieldLabel())
            .nestedVariableValueFieldLabel(
                getNestedVariableValueFieldLabel(getVariableType(context)))
            .indexNames(getIndexNames(context))
            .timezone(context.getTimezone())
            .customBucketDto(context.getReportData().getConfiguration().getCustomBucket())
            .dateUnit(getGroupByDateUnit(context))
            .baseQueryForMinMaxStats(boolQuery)
            .subAggregations(Map.of(VARIABLES_INSTANCE_COUNT_AGGREGATION, builder))
            .combinedRangeMinMaxStats(context.getCombinedRangeMinMaxStats().orElse(null))
            .filterContext(context.getFilterContext())
            .build();

    final Optional<Map<String, Aggregation.Builder.ContainerBuilder>> variableSubAggregation =
        getVariableAggregationService().createVariableSubAggregation(varAggContext);

    final Optional<Query> variableFilterQuery =
        getVariableAggregationService().createVariableFilterQuery(varAggContext);

    if (variableSubAggregation.isEmpty()) {
      // if the report contains no instances and is grouped by date variable, this agg will not be
      // present
      // as it is based on instance data
      return Map.of();
    }

    final Aggregation.Builder.ContainerBuilder variableAggregation =
        new Aggregation.Builder()
            .nested(n -> n.path(getVariablePath()))
            .aggregations(
                FILTERED_VARIABLES_AGGREGATION,
                Aggregation.of(
                    a ->
                        a.filter(
                                f ->
                                    f.bool(
                                        b ->
                                            b.must(
                                                    mm ->
                                                        mm.term(
                                                            t ->
                                                                t.field(
                                                                        getNestedVariableNameFieldLabel())
                                                                    .value(
                                                                        getVariableName(context))))
                                                .must(
                                                    mm ->
                                                        mm.term(
                                                            t ->
                                                                t.field(
                                                                        getNestedVariableTypeField())
                                                                    .value(
                                                                        getVariableType(context)
                                                                            .getId())))
                                                .must(
                                                    mm ->
                                                        mm.exists(
                                                            t ->
                                                                t.field(
                                                                    getNestedVariableValueFieldLabel(
                                                                        VariableType.STRING))))
                                                .must(
                                                    variableFilterQuery.orElseGet(
                                                        () -> Query.of(q -> q.matchAll(m -> m))))))
                            .aggregations(
                                variableSubAggregation.get().entrySet().stream()
                                    .collect(
                                        Collectors.toMap(
                                            Map.Entry::getKey, e -> e.getValue().build())))
                            .aggregations(
                                FILTERED_INSTANCE_COUNT_AGGREGATION,
                                Aggregation.of(aa -> aa.reverseNested(n -> n)))));
    final Aggregation.Builder.ContainerBuilder undefinedOrNullVariableAggregation =
        createUndefinedOrNullVariableAggregation(context, boolQuery);
    return Map.of(
        MISSING_VARIABLES_AGGREGATION, undefinedOrNullVariableAggregation,
        NESTED_VARIABLE_AGGREGATION, variableAggregation);
  }

  private Map<String, Aggregation> createDistributedBySubAggregations(
      final ExecutionContext<DATA, PLAN> context, final BoolQuery baseQuery) {
    if (isFlownodeReport(context.getPlan())) {
      // Nest the distributed by part to ensure the aggregation is on flownode level
      final Aggregation.Builder.ContainerBuilder builder =
          new Aggregation.Builder().nested(n -> n.path(FLOW_NODE_INSTANCES));
      builder.aggregations(
          FILTERED_FLOW_NODE_AGGREGATION,
          Aggregation.of(
              a -> {
                final Aggregation.Builder.ContainerBuilder filter =
                    a.filter(
                        f ->
                            f.bool(
                                createModelElementAggregationFilter(
                                        (ProcessReportDataDto) context.getReportData(),
                                        context.getFilterContext(),
                                        getDefinitionService())
                                    .build()));
                getDistributedByInterpreter()
                    .createAggregations(context, baseQuery)
                    .forEach((k, v) -> a.aggregations(k, v.build()));
                return filter;
              }));

      return Map.of(NESTED_FLOWNODE_AGGREGATION, builder.build());
    }
    return getDistributedByInterpreter().createAggregations(context, baseQuery).entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().build()));
  }

  private Aggregation.Builder.ContainerBuilder createUndefinedOrNullVariableAggregation(
      final ExecutionContext<DATA, PLAN> context, final BoolQuery baseQuery) {
    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder()
            .filter(f -> f.bool(getVariableUndefinedOrNullQuery(context).build()));
    createDistributedBySubAggregations(context, baseQuery).forEach(builder::aggregations);
    return builder;
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final ResponseBody<?> response,
      final ExecutionContext<DATA, PLAN> context) {
    if (response.aggregations() == null || response.aggregations().isEmpty()) {
      // aggregations are null if there are no instances in the report and it is grouped by date
      // variable
      return;
    }

    final NestedAggregate nested =
        response.aggregations().get(NESTED_VARIABLE_AGGREGATION).nested();
    final Aggregate filteredVariables = nested.aggregations().get(FILTERED_VARIABLES_AGGREGATION);
    Aggregate filteredParentAgg =
        filteredVariables.filter().aggregations().get(FILTER_LIMITED_AGGREGATION);
    if (filteredParentAgg == null) {
      filteredParentAgg = filteredVariables;
    }
    Aggregate variableTerms = filteredParentAgg.filter().aggregations().get(VARIABLES_AGGREGATION);
    if (variableTerms == null) {
      variableTerms = filteredParentAgg.filter().aggregations().get(VARIABLE_HISTOGRAM_AGGREGATION);
    }

    final Map<String, Map<String, Aggregate>> bucketAggregations =
        getVariableAggregationService()
            .retrieveResultBucketMap(
                filteredParentAgg.filter(),
                variableTerms,
                getVariableType(context),
                context.getTimezone());

    // enrich context with complete set of distributed by keys
    getDistributedByInterpreter()
        .enrichContextWithAllExpectedDistributedByKeys(
            context, filteredParentAgg.filter().aggregations());

    final List<CompositeCommandResult.GroupByResult> groupedData = new ArrayList<>();
    for (final Map.Entry<String, Map<String, Aggregate>> keyToAggregationEntry :
        bucketAggregations.entrySet()) {
      final List<CompositeCommandResult.DistributedByResult> distribution =
          getDistributedByInterpreter()
              .retrieveResult(
                  response,
                  getVariableAggregationService()
                      .retrieveSubAggregationFromBucketMapEntry(keyToAggregationEntry),
                  context);
      groupedData.add(
          CompositeCommandResult.GroupByResult.createGroupByResult(
              keyToAggregationEntry.getKey(), distribution));
    }

    addMissingVariableBuckets(groupedData, response, context);

    compositeCommandResult.setGroups(groupedData);
    if (VariableType.DATE.equals(getVariableType(context))) {
      compositeCommandResult.setGroupBySorting(
          new ReportSortingDto(ReportSortingDto.SORT_BY_KEY, SortOrder.ASC));
    }
    compositeCommandResult.setGroupByKeyOfNumericType(getSortByKeyIsOfNumericType(context));
    compositeCommandResult.setDistributedByKeyOfNumericType(
        getDistributedByInterpreter().isKeyOfNumericType(context));
  }

  private boolean isFlownodeReport(final PLAN plan) {
    if (plan instanceof final ProcessExecutionPlan processExecutionPlan) {
      return Set.of(PROCESS_VIEW_FLOW_NODE_DURATION, PROCESS_VIEW_FLOW_NODE_FREQUENCY)
          .contains(processExecutionPlan.getView());
    } else {
      return false;
    }
  }

  private void addMissingVariableBuckets(
      final List<CompositeCommandResult.GroupByResult> groupedData,
      final ResponseBody<?> response,
      final ExecutionContext<DATA, PLAN> context) {
    final NestedAggregate nested =
        response.aggregations().get(NESTED_VARIABLE_AGGREGATION).nested();
    final FilterAggregate filteredVariables =
        nested.aggregations().get(FILTERED_VARIABLES_AGGREGATION).filter();

    final ReverseNestedAggregate filteredInstAggr =
        filteredVariables.aggregations().get(FILTERED_INSTANCE_COUNT_AGGREGATION).reverseNested();
    if (response.hits().total().value() > filteredInstAggr.docCount()) {
      final List<CompositeCommandResult.DistributedByResult> missingVarsOperationResult =
          getDistributedByInterpreter()
              .retrieveResult(response, retrieveAggregationsForMissingVariables(response), context);
      groupedData.add(
          CompositeCommandResult.GroupByResult.createGroupByResult(
              MISSING_VARIABLE_KEY, missingVarsOperationResult));
    }
  }

  private Map<String, Aggregate> retrieveAggregationsForMissingVariables(
      final ResponseBody<?> response) {
    final FilterAggregate aggregation =
        response.aggregations().get(MISSING_VARIABLES_AGGREGATION).filter();
    final Aggregate nestedFlowNodeAgg = aggregation.aggregations().get(NESTED_FLOWNODE_AGGREGATION);
    if (nestedFlowNodeAgg == null) {
      return aggregation.aggregations(); // this is an instance report
    } else {
      final Map<String, Aggregate> flowNodeAggs =
          nestedFlowNodeAgg.nested().aggregations(); // this is a flownode report
      final FilterAggregate filteredAgg = flowNodeAggs.get(FILTERED_FLOW_NODE_AGGREGATION).filter();
      return filteredAgg.aggregations();
    }
  }

  private boolean getSortByKeyIsOfNumericType(final ExecutionContext<DATA, PLAN> context) {
    return VariableType.getNumericTypes().contains(getVariableType(context));
  }

  private AggregateByDateUnit getGroupByDateUnit(final ExecutionContext<DATA, PLAN> context) {
    return context.getReportData().getConfiguration().getGroupByDateVariableUnit();
  }

  private boolean isGroupedByNumberVariable(final VariableType varType) {
    return VariableType.getNumericTypes().contains(varType);
  }
}
