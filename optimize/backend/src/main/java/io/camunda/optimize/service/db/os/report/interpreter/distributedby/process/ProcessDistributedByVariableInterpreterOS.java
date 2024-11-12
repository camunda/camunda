/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.distributedby.process;

import static io.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.filterAggregation;
import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.withSubaggregations;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.and;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.exists;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.matchAll;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.os.report.interpreter.util.FilterLimitedAggregationUtilOS.FILTER_LIMITED_AGGREGATION;
import static io.camunda.optimize.service.db.os.report.service.VariableAggregationServiceOS.FILTERED_INSTANCE_COUNT_AGGREGATION;
import static io.camunda.optimize.service.db.os.report.service.VariableAggregationServiceOS.FILTERED_VARIABLES_AGGREGATION;
import static io.camunda.optimize.service.db.os.report.service.VariableAggregationServiceOS.MISSING_VARIABLES_AGGREGATION;
import static io.camunda.optimize.service.db.os.report.service.VariableAggregationServiceOS.NESTED_VARIABLE_AGGREGATION;
import static io.camunda.optimize.service.db.os.report.service.VariableAggregationServiceOS.VARIABLES_AGGREGATION;
import static io.camunda.optimize.service.db.os.report.service.VariableAggregationServiceOS.VARIABLE_HISTOGRAM_AGGREGATION;
import static io.camunda.optimize.service.db.os.util.ProcessVariableHelperOS.createFilterForUndefinedOrNullQuery;
import static io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy.PROCESS_DISTRIBUTED_BY_VARIABLE;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableNameField;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableTypeField;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableValueFieldForType;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasNames;
import static java.util.stream.Collectors.toSet;

import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.VariableDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.VariableDistributedByValueDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.os.report.context.VariableAggregationContextOS;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterFacadeOS;
import io.camunda.optimize.service.db.os.report.service.DateAggregationServiceOS;
import io.camunda.optimize.service.db.os.report.service.VariableAggregationServiceOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.FilterAggregate;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregate;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregation.Builder;
import org.opensearch.client.opensearch._types.aggregations.ReverseNestedAggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessDistributedByVariableInterpreterOS
    extends AbstractProcessDistributedByInterpreterOS {

  private static final String PARENT_FILTER_AGGREGATION = "matchAllFilter";

  private final ProcessViewInterpreterFacadeOS viewInterpreter;
  private final DateAggregationServiceOS dateAggregationService;
  private final VariableAggregationServiceOS variableAggregationService;

  public ProcessDistributedByVariableInterpreterOS(
      final ProcessViewInterpreterFacadeOS viewInterpreter,
      final DateAggregationServiceOS dateAggregationService,
      final VariableAggregationServiceOS variableAggregationService) {
    this.viewInterpreter = viewInterpreter;
    this.dateAggregationService = dateAggregationService;
    this.variableAggregationService = variableAggregationService;
  }

  @Override
  public Set<ProcessDistributedBy> getSupportedDistributedBys() {
    return Set.of(PROCESS_DISTRIBUTED_BY_VARIABLE);
  }

  @Override
  public boolean isKeyOfNumericType(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return VariableType.getNumericTypes().contains(getVariableType(context));
  }

  @Override
  public Map<String, Aggregation> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery) {
    final Map<String, Aggregation> subAggregations =
        Map.of(
            VariableAggregationServiceOS.VARIABLES_INSTANCE_COUNT_AGGREGATION,
            withSubaggregations(
                new ReverseNestedAggregation.Builder().build(),
                viewInterpreter.createAggregations(context)));
    final VariableAggregationContextOS varAggContext =
        VariableAggregationContextOS.builder()
            .variableName(getVariableName(context))
            .variableType(getVariableType(context))
            .variablePath(VARIABLES)
            .nestedVariableNameField(getNestedVariableNameField())
            .nestedVariableValueFieldLabel(
                getNestedVariableValueFieldLabel(getVariableType(context)))
            .indexNames(getProcessInstanceIndexAliasNames(context.getReportData()))
            .timezone(context.getTimezone())
            .customBucketDto(
                context.getReportData().getConfiguration().getDistributeByCustomBucket())
            .dateUnit(getDistributeByDateUnit(context))
            .baseQueryForMinMaxStats(baseQuery)
            .subAggregations(subAggregations)
            .combinedRangeMinMaxStats(context.getCombinedRangeMinMaxStats().orElse(null))
            .filterContext(context.getFilterContext())
            .build();

    final Optional<Pair<String, Aggregation>> variableSubAggregation =
        variableAggregationService.createVariableSubAggregation(varAggContext);

    if (variableSubAggregation.isEmpty()) {
      // if the report contains no instances and is distributed by date variable, this agg will not
      // be present as it is based on instance data
      return viewInterpreter.createAggregations(context);
    }

    // Add a parent match all filter agg to be able to retrieve all undefined/null variables as a
    // sibling aggregation
    // to the nested existing variable filter
    final Pair<String, Aggregation> undefinedOrNullVariableAggregation =
        createUndefinedOrNullVariableAggregation(context);
    final Aggregation filterVariablesAggregation =
        withSubaggregations(
            filterAggregation(
                and(
                    term(getNestedVariableNameField(), getVariableName(context)),
                    term(getNestedVariableTypeField(), getVariableType(context).getId()),
                    exists(getNestedVariableValueFieldLabel(VariableType.STRING)))),
            Map.of(
                variableSubAggregation.get().getKey(),
                variableSubAggregation.get().getValue(),
                FILTERED_INSTANCE_COUNT_AGGREGATION,
                new ReverseNestedAggregation.Builder().build()._toAggregation()));
    final Aggregation nestedVariableAggregation =
        withSubaggregations(
            new Builder().path(VARIABLES).build(),
            Map.of(FILTERED_VARIABLES_AGGREGATION, filterVariablesAggregation));

    return Map.of(
        PARENT_FILTER_AGGREGATION,
        withSubaggregations(
            filterAggregation(matchAll()),
            Map.of(
                undefinedOrNullVariableAggregation.getKey(),
                undefinedOrNullVariableAggregation.getValue(),
                NESTED_VARIABLE_AGGREGATION,
                nestedVariableAggregation)));
  }

  @Override
  public List<DistributedByResult> retrieveResult(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    if (!aggregations.containsKey(PARENT_FILTER_AGGREGATION)) {
      // could not create aggregations, e.g. because baseline is invalid
      return Collections.emptyList();
    }
    final FilterAggregate parentFilterAgg = aggregations.get(PARENT_FILTER_AGGREGATION).filter();
    final NestedAggregate nested =
        parentFilterAgg.aggregations().get(NESTED_VARIABLE_AGGREGATION).nested();
    final FilterAggregate filteredVariables =
        nested.aggregations().get(FILTERED_VARIABLES_AGGREGATION).filter();
    final FilterAggregate filteredParentAgg =
        filteredVariables.aggregations().containsKey(FILTER_LIMITED_AGGREGATION)
            ? filteredVariables.aggregations().get(FILTER_LIMITED_AGGREGATION).filter()
            : filteredVariables;
    final Map<String, Map<String, Aggregate>> bucketMap =
        filteredParentAgg.aggregations().containsKey(VARIABLES_AGGREGATION)
            ? variableAggregationService.resultBucketMap(
                filteredParentAgg.aggregations().get(VARIABLES_AGGREGATION))
            : variableAggregationService.resultBucketMap(
                filteredParentAgg.aggregations().get(VARIABLE_HISTOGRAM_AGGREGATION));
    final Map<String, Map<String, Aggregate>> bucketAggregations =
        variableAggregationService.retrieveResultBucketMap(
            filteredParentAgg, bucketMap, getVariableType(context), context.getTimezone());

    final List<DistributedByResult> distributedByResults = new ArrayList<>();

    for (final Map.Entry<String, Map<String, Aggregate>> keyToAggregationEntry :
        bucketAggregations.entrySet()) {
      final CompositeCommandResult.ViewResult viewResult =
          viewInterpreter.retrieveResult(
              response,
              variableAggregationService.retrieveSubAggregationFromBucketMapEntry(
                  keyToAggregationEntry),
              context);
      distributedByResults.add(
          createDistributedByResult(keyToAggregationEntry.getKey(), null, viewResult));
    }

    addMissingVariableBuckets(distributedByResults, response, aggregations, context);
    addEmptyMissingDistributedByResults(distributedByResults, context);

    return distributedByResults;
  }

  @Override
  public void enrichContextWithAllExpectedDistributedByKeys(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Map<String, Aggregate> aggregations) {
    final Aggregate parentFilterAgg = aggregations.get(PARENT_FILTER_AGGREGATION);
    if (parentFilterAgg == null) {
      // there are no distributedBy keys
      context.setAllDistributedByKeysAndLabels(new HashMap<>());
      return;
    }

    Set<String> allDistributedByKeys = new HashSet<>();
    final VariableType type = getVariableType(context);
    if (!VariableType.getNumericTypes().contains(type)) {
      // missing distrBy keys evaluation only required if it's not a range (number var) aggregation
      final NestedAggregate nestedAgg =
          parentFilterAgg.filter().aggregations().get(NESTED_VARIABLE_AGGREGATION).nested();
      final FilterAggregate filteredVarAgg =
          nestedAgg.aggregations().get(FILTERED_VARIABLES_AGGREGATION).filter();
      if (VariableType.DATE.equals(type)) {
        final Aggregate filterLimitedAgg =
            filteredVarAgg.aggregations().get(FILTER_LIMITED_AGGREGATION);
        allDistributedByKeys =
            dateAggregationService
                .mapDateAggregationsToKeyAggregationMap(
                    filterLimitedAgg == null
                        ? filteredVarAgg.aggregations()
                        : filterLimitedAgg.filter().aggregations(),
                    context.getTimezone(),
                    VARIABLES_AGGREGATION)
                .keySet();
      } else {
        final StringTermsAggregate varNamesAgg =
            filteredVarAgg.aggregations().get(VARIABLES_AGGREGATION).sterms();
        allDistributedByKeys =
            varNamesAgg.buckets().array().stream().map(StringTermsBucket::key).collect(toSet());
      }
    }

    final FilterAggregate missingVarAgg =
        parentFilterAgg.filter().aggregations().get(MISSING_VARIABLES_AGGREGATION).filter();
    if (missingVarAgg.docCount() > 0) {
      // instances with missing value for the variable exist, so add an extra bucket for those
      allDistributedByKeys.add(MISSING_VARIABLE_KEY);
    }

    context.setAllDistributedByKeys(allDistributedByKeys);
  }

  private Pair<String, Aggregation> createUndefinedOrNullVariableAggregation(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return Pair.of(
        MISSING_VARIABLES_AGGREGATION,
        withSubaggregations(
            filterAggregation(
                createFilterForUndefinedOrNullQuery(
                    getVariableName(context), getVariableType(context))),
            viewInterpreter.createAggregations(context)));
  }

  private void addEmptyMissingDistributedByResults(
      final List<DistributedByResult> distributedByResults,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    context.getAllDistributedByKeysAndLabels().entrySet().stream()
        .filter(
            entry ->
                distributedByResults.stream()
                    .noneMatch(
                        distributedByResult -> distributedByResult.getKey().equals(entry.getKey())))
        .map(
            entry ->
                createDistributedByResult(
                    entry.getKey(), entry.getValue(), viewInterpreter.createEmptyResult(context)))
        .forEach(distributedByResults::add);
  }

  private void addMissingVariableBuckets(
      final List<DistributedByResult> distributedByResults,
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final FilterAggregate parentFilterAgg = aggregations.get(PARENT_FILTER_AGGREGATION).filter();
    final FilterAggregate missingVarAgg =
        parentFilterAgg.aggregations().get(MISSING_VARIABLES_AGGREGATION).filter();

    if (missingVarAgg.docCount() > 0) {
      final CompositeCommandResult.ViewResult viewResult =
          viewInterpreter.retrieveResult(response, missingVarAgg.aggregations(), context);
      distributedByResults.add(createDistributedByResult(MISSING_VARIABLE_KEY, null, viewResult));
    }
  }

  private String getVariableName(final ExecutionContext<ProcessReportDataDto, ?> context) {
    return getVariableDistributedByValueDto(context).getName();
  }

  private VariableType getVariableType(final ExecutionContext<ProcessReportDataDto, ?> context) {
    return getVariableDistributedByValueDto(context).getType();
  }

  private String getNestedVariableValueFieldLabel(final VariableType type) {
    return getNestedVariableValueFieldForType(type);
  }

  private VariableDistributedByValueDto getVariableDistributedByValueDto(
      final ExecutionContext<ProcessReportDataDto, ?> context) {
    return ((VariableDistributedByDto) context.getReportData().getDistributedBy()).getValue();
  }

  private AggregateByDateUnit getDistributeByDateUnit(
      final ExecutionContext<ProcessReportDataDto, ?> context) {
    return context.getReportData().getConfiguration().getDistributeByDateVariableUnit();
  }

  public ProcessViewInterpreterFacadeOS getViewInterpreter() {
    return this.viewInterpreter;
  }
}
