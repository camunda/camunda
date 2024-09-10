/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.distributedby.process;

import static io.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static io.camunda.optimize.service.db.es.report.command.util.FilterLimitedAggregationUtilES.FILTER_LIMITED_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.FILTERED_INSTANCE_COUNT_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.FILTERED_VARIABLES_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.MISSING_VARIABLES_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.NESTED_VARIABLE_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.VARIABLES_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.VARIABLES_INSTANCE_COUNT_AGGREGATION;
import static io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES.VARIABLE_HISTOGRAM_AGGREGATION;
import static io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy.PROCESS_DISTRIBUTED_BY_VARIABLE;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasNames;
import static io.camunda.optimize.service.util.ProcessVariableHelper.createFilterForUndefinedOrNullQueryBuilder;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableTypeField;
import static io.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldForType;
import static java.util.stream.Collectors.toSet;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.reverseNested;

import io.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.VariableDistributedByDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.VariableDistributedByValueDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.es.report.context.VariableAggregationContextES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterFacadeES;
import io.camunda.optimize.service.db.es.report.service.DateAggregationServiceES;
import io.camunda.optimize.service.db.es.report.service.VariableAggregationServiceES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessDistributedBy;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.nested.ReverseNestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class ProcessDistributedByVariableInterpreterES
    extends AbstractProcessDistributedByInterpreterES {
  private static final String PARENT_FILTER_AGGREGATION = "matchAllFilter";

  @Getter private final ProcessViewInterpreterFacadeES viewInterpreter;
  private final DateAggregationServiceES dateAggregationService;
  private final VariableAggregationServiceES variableAggregationService;

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
  public List<AggregationBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final QueryBuilder baseQueryBuilder) {
    final ReverseNestedAggregationBuilder reverseNestedInstanceAggregation =
        reverseNested(VARIABLES_INSTANCE_COUNT_AGGREGATION);
    viewInterpreter
        .createAggregations(context)
        .forEach(reverseNestedInstanceAggregation::subAggregation);
    final VariableAggregationContextES varAggContext =
        VariableAggregationContextES.builder()
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
            .baseQueryForMinMaxStats(baseQueryBuilder)
            .subAggregations(List.of(reverseNestedInstanceAggregation))
            .combinedRangeMinMaxStats(context.getCombinedRangeMinMaxStats().orElse(null))
            .filterContext(context.getFilterContext())
            .build();

    final Optional<AggregationBuilder> variableSubAggregation =
        variableAggregationService.createVariableSubAggregation(varAggContext);

    if (variableSubAggregation.isEmpty()) {
      // if the report contains no instances and is distributed by date variable, this agg will not
      // be present
      // as it is based on instance data
      return viewInterpreter.createAggregations(context);
    }

    // Add a parent match all filter agg to be able to retrieve all undefined/null variables as a
    // sibling aggregation
    // to the nested existing variable filter
    return List.of(
        filter(PARENT_FILTER_AGGREGATION, matchAllQuery())
            .subAggregation(createUndefinedOrNullVariableAggregation(context))
            .subAggregation(
                nested(NESTED_VARIABLE_AGGREGATION, VARIABLES)
                    .subAggregation(
                        filter(
                                FILTERED_VARIABLES_AGGREGATION,
                                boolQuery()
                                    .must(
                                        termQuery(
                                            getNestedVariableNameField(), getVariableName(context)))
                                    .must(
                                        termQuery(
                                            getNestedVariableTypeField(),
                                            getVariableType(context).getId()))
                                    .must(
                                        existsQuery(
                                            getNestedVariableValueFieldLabel(VariableType.STRING))))
                            .subAggregation(variableSubAggregation.get())
                            .subAggregation(reverseNested(FILTERED_INSTANCE_COUNT_AGGREGATION)))));
  }

  @Override
  public List<DistributedByResult> retrieveResult(
      SearchResponse response,
      Aggregations aggregations,
      ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final ParsedFilter parentFilterAgg = aggregations.get(PARENT_FILTER_AGGREGATION);
    if (parentFilterAgg == null) {
      // could not create aggregations, e.g. because baseline is invalid
      return Collections.emptyList();
    }

    final Nested nested = parentFilterAgg.getAggregations().get(NESTED_VARIABLE_AGGREGATION);
    final Filter filteredVariables = nested.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
    Filter filteredParentAgg = filteredVariables.getAggregations().get(FILTER_LIMITED_AGGREGATION);
    if (filteredParentAgg == null) {
      filteredParentAgg = filteredVariables;
    }
    MultiBucketsAggregation variableTerms =
        filteredParentAgg.getAggregations().get(VARIABLES_AGGREGATION);
    if (variableTerms == null) {
      variableTerms = filteredParentAgg.getAggregations().get(VARIABLE_HISTOGRAM_AGGREGATION);
    }

    Map<String, Aggregations> bucketAggregations =
        variableAggregationService.retrieveResultBucketMap(
            filteredParentAgg, variableTerms, getVariableType(context), context.getTimezone());

    List<CompositeCommandResult.DistributedByResult> distributedByResults = new ArrayList<>();

    for (Map.Entry<String, Aggregations> keyToAggregationEntry : bucketAggregations.entrySet()) {
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
      final Aggregations aggregations) {
    final ParsedFilter parentFilterAgg = aggregations.get(PARENT_FILTER_AGGREGATION);
    if (parentFilterAgg == null) {
      // there are no distributedBy keys
      context.setAllDistributedByKeysAndLabels(new HashMap<>());
      return;
    }

    Set<String> allDistributedByKeys = new HashSet<>();
    final VariableType type = getVariableType(context);
    if (!VariableType.getNumericTypes().contains(type)) {
      // missing distrBy keys evaluation only required if it's not a range (number var) aggregation
      final ParsedNested nestedAgg =
          parentFilterAgg.getAggregations().get(NESTED_VARIABLE_AGGREGATION);
      final ParsedFilter filteredVarAgg =
          nestedAgg.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
      if (VariableType.DATE.equals(type)) {
        final ParsedFilter filterLimitedAgg =
            filteredVarAgg.getAggregations().get(FILTER_LIMITED_AGGREGATION);
        allDistributedByKeys =
            dateAggregationService
                .mapDateAggregationsToKeyAggregationMap(
                    filterLimitedAgg == null
                        ? filteredVarAgg.getAggregations()
                        : filterLimitedAgg.getAggregations(),
                    context.getTimezone(),
                    VARIABLES_AGGREGATION)
                .keySet();
      } else {
        final ParsedStringTerms varNamesAgg =
            filteredVarAgg.getAggregations().get(VARIABLES_AGGREGATION);
        allDistributedByKeys =
            varNamesAgg.getBuckets().stream()
                .map(MultiBucketsAggregation.Bucket::getKeyAsString)
                .collect(toSet());
      }
    }

    final Filter missingVarAgg =
        parentFilterAgg.getAggregations().get(MISSING_VARIABLES_AGGREGATION);
    if (missingVarAgg.getDocCount() > 0) {
      // instances with missing value for the variable exist, so add an extra bucket for those
      allDistributedByKeys.add(MISSING_VARIABLE_KEY);
    }

    context.setAllDistributedByKeys(allDistributedByKeys);
  }

  private AggregationBuilder createUndefinedOrNullVariableAggregation(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final FilterAggregationBuilder filterAggregationBuilder =
        filter(
            MISSING_VARIABLES_AGGREGATION,
            createFilterForUndefinedOrNullQueryBuilder(
                getVariableName(context), getVariableType(context)));
    viewInterpreter.createAggregations(context).forEach(filterAggregationBuilder::subAggregation);
    return filterAggregationBuilder;
  }

  private void addEmptyMissingDistributedByResults(
      List<CompositeCommandResult.DistributedByResult> distributedByResults,
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
      final List<CompositeCommandResult.DistributedByResult> distributedByResults,
      final SearchResponse response,
      final Aggregations aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final ParsedFilter parentFilterAgg = aggregations.get(PARENT_FILTER_AGGREGATION);
    final Filter missingVarAgg =
        parentFilterAgg.getAggregations().get(MISSING_VARIABLES_AGGREGATION);

    if (missingVarAgg.getDocCount() > 0) {
      final CompositeCommandResult.ViewResult viewResult =
          viewInterpreter.retrieveResult(response, missingVarAgg.getAggregations(), context);
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
}
