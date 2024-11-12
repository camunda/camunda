/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby;

import static io.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.exists;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.matchAll;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.os.report.filter.util.ModelElementFilterQueryUtilOS.createModelElementAggregationFilter;
import static io.camunda.optimize.service.db.os.report.interpreter.util.FilterLimitedAggregationUtilOS.FILTER_LIMITED_AGGREGATION;
import static io.camunda.optimize.service.db.os.report.service.VariableAggregationServiceOS.FILTERED_INSTANCE_COUNT_AGGREGATION;
import static io.camunda.optimize.service.db.os.report.service.VariableAggregationServiceOS.FILTERED_VARIABLES_AGGREGATION;
import static io.camunda.optimize.service.db.os.report.service.VariableAggregationServiceOS.MISSING_VARIABLES_AGGREGATION;
import static io.camunda.optimize.service.db.os.report.service.VariableAggregationServiceOS.NESTED_FLOWNODE_AGGREGATION;
import static io.camunda.optimize.service.db.os.report.service.VariableAggregationServiceOS.NESTED_VARIABLE_AGGREGATION;
import static io.camunda.optimize.service.db.os.report.service.VariableAggregationServiceOS.VARIABLES_AGGREGATION;
import static io.camunda.optimize.service.db.os.report.service.VariableAggregationServiceOS.VARIABLES_INSTANCE_COUNT_AGGREGATION;
import static io.camunda.optimize.service.db.os.report.service.VariableAggregationServiceOS.VARIABLE_HISTOGRAM_AGGREGATION;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_FLOW_NODE_DURATION;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_FLOW_NODE_FREQUENCY;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.os.report.context.VariableAggregationContextOS;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.service.VariableAggregationServiceOS;
import io.camunda.optimize.service.db.os.util.AggregateHelperOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.MinMaxStatDto;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.FilterAggregate;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregate;
import org.opensearch.client.opensearch._types.aggregations.ReverseNestedAggregate;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;

public abstract class AbstractGroupByVariableInterpreterOS<
        DATA extends SingleReportDataDto, PLAN extends ExecutionPlan>
    extends AbstractGroupByInterpreterOS<DATA, PLAN> {

  public static final String FILTERED_FLOW_NODE_AGGREGATION = "filteredFlowNodeAggregation";

  public AbstractGroupByVariableInterpreterOS() {}

  protected abstract VariableAggregationServiceOS getVariableAggregationService();

  protected abstract DefinitionService getDefinitionService();

  protected abstract String getVariableName(final ExecutionContext<DATA, PLAN> context);

  protected abstract VariableType getVariableType(final ExecutionContext<DATA, PLAN> context);

  protected abstract String getNestedVariableNameFieldLabel();

  protected abstract String getNestedVariableTypeField();

  protected abstract String getNestedVariableValueFieldLabel(final VariableType type);

  protected abstract String getVariablePath();

  protected abstract Query getVariableUndefinedOrNullQuery(
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
  public Map<String, Aggregation> createAggregation(
      final Query query, final ExecutionContext<DATA, PLAN> context) {
    final Aggregation reverseNestedAggregation =
        new Aggregation.Builder()
            .reverseNested(b -> b)
            .aggregations(createDistributedBySubAggregations(context, query))
            .build();

    final VariableAggregationContextOS varAggContext =
        VariableAggregationContextOS.builder()
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
            .baseQueryForMinMaxStats(query)
            .subAggregations(Map.of(VARIABLES_INSTANCE_COUNT_AGGREGATION, reverseNestedAggregation))
            .combinedRangeMinMaxStats(context.getCombinedRangeMinMaxStats().orElse(null))
            .filterContext(context.getFilterContext())
            .build();

    final Optional<Pair<String, Aggregation>> variableSubAggregation =
        getVariableAggregationService().createVariableSubAggregation(varAggContext);

    final Optional<Query> variableFilterQuery =
        getVariableAggregationService().createVariableFilterQuery(varAggContext);

    if (variableSubAggregation.isEmpty()) {
      // if the report contains no instances and is grouped by date variable, this agg will not be
      // present as it is based on instance data
      return Map.of();
    }

    final Aggregation emptyReverseNestedAggregation =
        new Aggregation.Builder().reverseNested(b -> b).build();

    final Aggregation filteredVariablesAggregation =
        new Aggregation.Builder()
            .filter(
                f ->
                    f.bool(
                        BoolQuery.of(
                            q ->
                                q.must(
                                        term(
                                            getNestedVariableNameFieldLabel(),
                                            getVariableName(context)))
                                    .must(
                                        term(
                                            getNestedVariableTypeField(),
                                            getVariableType(context).getId()))
                                    .must(
                                        exists(
                                            getNestedVariableValueFieldLabel(VariableType.STRING)))
                                    .must(variableFilterQuery.orElse(matchAll())))))
            .aggregations(
                Map.of(
                    variableSubAggregation.get().getKey(),
                    variableSubAggregation.get().getValue(),
                    FILTERED_INSTANCE_COUNT_AGGREGATION,
                    emptyReverseNestedAggregation))
            .build();

    final Aggregation variableAggregation =
        new Aggregation.Builder()
            .nested(b -> b.path(getVariablePath()))
            .aggregations(Map.of(FILTERED_VARIABLES_AGGREGATION, filteredVariablesAggregation))
            .build();

    final Pair<String, Aggregation> undefinedOrNullVariableAggregation =
        createUndefinedOrNullVariableAggregation(context, query);

    return Map.of(
        NESTED_VARIABLE_AGGREGATION,
        variableAggregation,
        undefinedOrNullVariableAggregation.getKey(),
        undefinedOrNullVariableAggregation.getValue());
  }

  private boolean isFlownodeReport(final PLAN plan) {
    if (plan instanceof final ProcessExecutionPlan processExecutionPlan) {
      return Set.of(PROCESS_VIEW_FLOW_NODE_DURATION, PROCESS_VIEW_FLOW_NODE_FREQUENCY)
          .contains(processExecutionPlan.getView());
    } else {
      return false;
    }
  }

  private Map<String, Aggregation> createDistributedBySubAggregations(
      final ExecutionContext<DATA, PLAN> context, final Query baseQuery) {
    if (isFlownodeReport(context.getPlan())) {
      // Nest the distributed by part to ensure the aggregation is on flownode level
      final Aggregation filterAggregation =
          new Aggregation.Builder()
              .filter(
                  createModelElementAggregationFilter(
                          (ProcessReportDataDto) context.getReportData(),
                          context.getFilterContext(),
                          getDefinitionService())
                      .build()
                      .toQuery())
              .aggregations(getDistributedByInterpreter().createAggregations(context, baseQuery))
              .build();

      final Aggregation nestedFlownodeAggregation =
          new Aggregation.Builder()
              .nested(b -> b.path(FLOW_NODE_INSTANCES))
              .aggregations(Map.of(FILTERED_FLOW_NODE_AGGREGATION, filterAggregation))
              .build();

      return Map.of(NESTED_FLOWNODE_AGGREGATION, nestedFlownodeAggregation);
    }
    return getDistributedByInterpreter().createAggregations(context, baseQuery);
  }

  private Pair<String, Aggregation> createUndefinedOrNullVariableAggregation(
      final ExecutionContext<DATA, PLAN> context, final Query baseQuery) {
    return Pair.of(
        MISSING_VARIABLES_AGGREGATION,
        new Aggregation.Builder()
            .filter(getVariableUndefinedOrNullQuery(context))
            .aggregations(createDistributedBySubAggregations(context, baseQuery))
            .build());
  }

  @Override
  public void addQueryResult(
      final CompositeCommandResult compositeCommandResult,
      final SearchResponse<RawResult> response,
      final ExecutionContext<DATA, PLAN> context) {
    if (response.aggregations() == null || response.aggregations().isEmpty()) {
      // aggregations are null if there are no instances in the report and it is grouped by date
      // variable
      return;
    }
    final Map<String, Aggregate> fixedAggregations =
        AggregateHelperOS.withNullValues(response.hits().total().value(), response.aggregations());
    final NestedAggregate nested = fixedAggregations.get(NESTED_VARIABLE_AGGREGATION).nested();
    final FilterAggregate filteredVariables =
        nested.aggregations().get(FILTERED_VARIABLES_AGGREGATION).filter();
    final Aggregate filterLimitedAggregation =
        filteredVariables.aggregations().get(FILTER_LIMITED_AGGREGATION);
    final FilterAggregate filteredParentAgg =
        filterLimitedAggregation != null ? filterLimitedAggregation.filter() : filteredVariables;
    final Aggregate variablesAggregation =
        filteredParentAgg.aggregations().get(VARIABLES_AGGREGATION);
    final Aggregate histogramAggregation =
        filteredParentAgg.aggregations().get(VARIABLE_HISTOGRAM_AGGREGATION);
    final Map<String, Map<String, Aggregate>> bucketMap =
        variablesAggregation != null
            ? getVariableAggregationService().resultBucketMap(variablesAggregation)
            : getVariableAggregationService().resultBucketMap(histogramAggregation);

    final Map<String, Map<String, Aggregate>> bucketAggregations =
        getVariableAggregationService()
            .retrieveResultBucketMap(
                filteredParentAgg, bucketMap, getVariableType(context), context.getTimezone());

    // enrich context with complete set of distributed by keys
    getDistributedByInterpreter()
        .enrichContextWithAllExpectedDistributedByKeys(context, filteredParentAgg.aggregations());

    final List<GroupByResult> groupedData = new ArrayList<>();
    for (final Map.Entry<String, Map<String, Aggregate>> keyToAggregationEntry :
        bucketAggregations.entrySet()) {
      final List<DistributedByResult> distribution =
          getDistributedByInterpreter()
              .retrieveResult(
                  response,
                  getVariableAggregationService()
                      .retrieveSubAggregationFromBucketMapEntry(keyToAggregationEntry),
                  context);
      groupedData.add(
          GroupByResult.createGroupByResult(keyToAggregationEntry.getKey(), distribution));
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

  private void addMissingVariableBuckets(
      final List<GroupByResult> groupedData,
      final SearchResponse<RawResult> response,
      final ExecutionContext<DATA, PLAN> context) {
    final NestedAggregate nested =
        response.aggregations().get(NESTED_VARIABLE_AGGREGATION).nested();
    final FilterAggregate filteredVariables =
        nested.aggregations().get(FILTERED_VARIABLES_AGGREGATION).filter();

    final ReverseNestedAggregate filteredInstAggr =
        filteredVariables.aggregations().get(FILTERED_INSTANCE_COUNT_AGGREGATION).reverseNested();
    if (response.hits().total().value() > filteredInstAggr.docCount()) {
      final List<DistributedByResult> missingVarsOperationResult =
          getDistributedByInterpreter()
              .retrieveResult(response, retrieveAggregationsForMissingVariables(response), context);
      groupedData.add(
          GroupByResult.createGroupByResult(MISSING_VARIABLE_KEY, missingVarsOperationResult));
    }
  }

  private Map<String, Aggregate> retrieveAggregationsForMissingVariables(
      final SearchResponse<RawResult> response) {
    final FilterAggregate aggregation =
        response.aggregations().get(MISSING_VARIABLES_AGGREGATION).filter();
    final Aggregate nestedFlowNodeAgg = aggregation.aggregations().get(NESTED_FLOWNODE_AGGREGATION);
    if (nestedFlowNodeAgg == null) {
      return aggregation.aggregations(); // this is an instance report
    } else {
      return nestedFlowNodeAgg
          .nested()
          .aggregations()
          .get(FILTERED_FLOW_NODE_AGGREGATION)
          .filter()
          .aggregations(); // this is a flownode report
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
