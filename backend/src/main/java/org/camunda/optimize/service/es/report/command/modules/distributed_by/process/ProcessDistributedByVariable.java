/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by.process;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.VariableDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.VariableDistributedByValueDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.service.DateAggregationService;
import org.camunda.optimize.service.es.report.command.service.VariableAggregationService;
import org.camunda.optimize.service.es.report.command.util.VariableAggregationContext;
import org.elasticsearch.action.search.SearchResponse;
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
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;
import static org.camunda.optimize.service.es.report.command.service.VariableAggregationService.FILTERED_INSTANCE_COUNT_AGGREGATION;
import static org.camunda.optimize.service.es.report.command.service.VariableAggregationService.FILTERED_VARIABLES_AGGREGATION;
import static org.camunda.optimize.service.es.report.command.service.VariableAggregationService.MISSING_VARIABLES_AGGREGATION;
import static org.camunda.optimize.service.es.report.command.service.VariableAggregationService.NESTED_VARIABLE_AGGREGATION;
import static org.camunda.optimize.service.es.report.command.service.VariableAggregationService.VARIABLES_AGGREGATION;
import static org.camunda.optimize.service.es.report.command.service.VariableAggregationService.VARIABLES_INSTANCE_COUNT_AGGREGATION;
import static org.camunda.optimize.service.es.report.command.service.VariableAggregationService.VARIABLE_HISTOGRAM_AGGREGATION;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.FILTER_LIMITED_AGGREGATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasNames;
import static org.camunda.optimize.service.util.ProcessVariableHelper.createFilterForUndefinedOrNullQueryBuilder;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableTypeField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldForType;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.reverseNested;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class ProcessDistributedByVariable extends ProcessDistributedByPart {

  private static final String PARENT_FILTER_AGGREGATION = "matchAllFilter";

  private final DateAggregationService dateAggregationService;
  private final VariableAggregationService variableAggregationService;

  @Override
  public boolean isKeyOfNumericType(final ExecutionContext<ProcessReportDataDto> context) {
    return VariableType.getNumericTypes().contains(getVariableType(context));
  }

  @Override
  public List<AggregationBuilder> createAggregations(final ExecutionContext<ProcessReportDataDto> context) {
    final ReverseNestedAggregationBuilder reverseNestedInstanceAggregation =
      reverseNested(VARIABLES_INSTANCE_COUNT_AGGREGATION);
    viewPart.createAggregations(context).forEach(reverseNestedInstanceAggregation::subAggregation);
    final VariableAggregationContext varAggContext = VariableAggregationContext.builder()
      .variableName(getVariableName(context))
      .variableType(getVariableType(context))
      .variablePath(VARIABLES)
      .nestedVariableNameField(getNestedVariableNameField())
      .nestedVariableValueFieldLabel(getNestedVariableValueFieldLabel(getVariableType(context)))
      .indexNames(getProcessInstanceIndexAliasNames(context.getReportData()))
      .timezone(context.getTimezone())
      .customBucketDto(context.getReportData().getConfiguration().getDistributeByCustomBucket())
      .dateUnit(getDistributeByDateUnit(context))
      .baseQueryForMinMaxStats(context.getDistributedByMinMaxBaseQuery())
      .subAggregations(Collections.singletonList(reverseNestedInstanceAggregation))
      .combinedRangeMinMaxStats(context.getCombinedRangeMinMaxStats().orElse(null))
      .filterContext(context.getFilterContext())
      .build();

    final Optional<AggregationBuilder> variableSubAggregation =
      variableAggregationService.createVariableSubAggregation(varAggContext);

    if (variableSubAggregation.isEmpty()) {
      // if the report contains no instances and is distributed by date variable, this agg will not be present
      // as it is based on instance data
      return viewPart.createAggregations(context);
    }

    // Add a parent match all filter agg to be able to retrieve all undefined/null variables as a sibling aggregation
    // to the nested existing variable filter
    return Collections.singletonList(filter(PARENT_FILTER_AGGREGATION, matchAllQuery())
      .subAggregation(createUndefinedOrNullVariableAggregation(context))
      .subAggregation(
        nested(NESTED_VARIABLE_AGGREGATION, VARIABLES)
          .subAggregation(
            filter(
              FILTERED_VARIABLES_AGGREGATION,
              boolQuery()
                .must(termQuery(getNestedVariableNameField(), getVariableName(context)))
                .must(termQuery(getNestedVariableTypeField(), getVariableType(context).getId()))
                .must(existsQuery(getNestedVariableValueFieldLabel(VariableType.STRING)))
            )
              .subAggregation(variableSubAggregation.get())
              .subAggregation(reverseNested(FILTERED_INSTANCE_COUNT_AGGREGATION))
          ))
    );
  }

  private AggregationBuilder createUndefinedOrNullVariableAggregation(final ExecutionContext<ProcessReportDataDto> context) {
    final FilterAggregationBuilder filterAggregationBuilder = filter(
      MISSING_VARIABLES_AGGREGATION,
      createFilterForUndefinedOrNullQueryBuilder(getVariableName(context), getVariableType(context))
    );
    viewPart.createAggregations(context).forEach(filterAggregationBuilder::subAggregation);
    return filterAggregationBuilder;
  }

  @Override
  public List<CompositeCommandResult.DistributedByResult> retrieveResult(final SearchResponse response,
                                                                         final Aggregations aggregations,
                                                                         final ExecutionContext<ProcessReportDataDto> context) {
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
    MultiBucketsAggregation variableTerms = filteredParentAgg.getAggregations().get(VARIABLES_AGGREGATION);
    if (variableTerms == null) {
      variableTerms = filteredParentAgg.getAggregations().get(VARIABLE_HISTOGRAM_AGGREGATION);
    }

    Map<String, Aggregations> bucketAggregations =
      variableAggregationService.retrieveResultBucketMap(
        filteredParentAgg,
        variableTerms,
        getVariableType(context),
        context.getTimezone()
      );

    List<CompositeCommandResult.DistributedByResult> distributedByResults = new ArrayList<>();

    for (Map.Entry<String, Aggregations> keyToAggregationEntry : bucketAggregations.entrySet()) {
      final CompositeCommandResult.ViewResult viewResult = viewPart.retrieveResult(
        response,
        variableAggregationService.retrieveSubAggregationFromBucketMapEntry(keyToAggregationEntry),
        context
      );
      distributedByResults.add(createDistributedByResult(keyToAggregationEntry.getKey(), null, viewResult));
    }

    addMissingVariableBuckets(distributedByResults, response, aggregations, context);
    addEmptyMissingDistributedByResults(distributedByResults, context);

    return distributedByResults;
  }

  @Override
  protected void addAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setDistributedBy(new VariableDistributedByDto());
  }

  @Override
  public void enrichContextWithAllExpectedDistributedByKeys(
    final ExecutionContext<ProcessReportDataDto> context,
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
      final ParsedNested nestedAgg = parentFilterAgg.getAggregations().get(NESTED_VARIABLE_AGGREGATION);
      final ParsedFilter filteredVarAgg = nestedAgg.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
      if (VariableType.DATE.equals(type)) {
        final ParsedFilter filterLimitedAgg = filteredVarAgg.getAggregations().get(FILTER_LIMITED_AGGREGATION);
        allDistributedByKeys = dateAggregationService.mapDateAggregationsToKeyAggregationMap(
          filterLimitedAgg == null ? filteredVarAgg.getAggregations() : filterLimitedAgg.getAggregations(),
          context.getTimezone(),
          VARIABLES_AGGREGATION
        ).keySet();
      } else {
        final ParsedStringTerms varNamesAgg = filteredVarAgg.getAggregations().get(VARIABLES_AGGREGATION);
        allDistributedByKeys = varNamesAgg.getBuckets()
          .stream()
          .map(MultiBucketsAggregation.Bucket::getKeyAsString)
          .collect(toSet());
      }
    }

    final Filter missingVarAgg = parentFilterAgg.getAggregations().get(MISSING_VARIABLES_AGGREGATION);
    if (missingVarAgg.getDocCount() > 0) {
      // instances with missing value for the variable exist, so add an extra bucket for those
      allDistributedByKeys.add(MISSING_VARIABLE_KEY);
    }

    context.setAllDistributedByKeys(allDistributedByKeys);
  }

  private void addEmptyMissingDistributedByResults(
    List<CompositeCommandResult.DistributedByResult> distributedByResults,
    final ExecutionContext<ProcessReportDataDto> context) {
    context.getAllDistributedByKeysAndLabels()
      .entrySet()
      .stream()
      .filter(entry -> distributedByResults.stream()
        .noneMatch(distributedByResult -> distributedByResult.getKey().equals(entry.getKey())))
      .map(entry -> createDistributedByResult(entry.getKey(), entry.getValue(), viewPart.createEmptyResult(context)))
      .forEach(distributedByResults::add);
  }

  private void addMissingVariableBuckets(final List<CompositeCommandResult.DistributedByResult> distributedByResults,
                                         final SearchResponse response,
                                         final Aggregations aggregations,
                                         final ExecutionContext<ProcessReportDataDto> context) {
    final ParsedFilter parentFilterAgg = aggregations.get(PARENT_FILTER_AGGREGATION);
    final Filter missingVarAgg = parentFilterAgg.getAggregations().get(MISSING_VARIABLES_AGGREGATION);

    if (missingVarAgg.getDocCount() > 0) {
      final CompositeCommandResult.ViewResult viewResult = viewPart.retrieveResult(
        response,
        missingVarAgg.getAggregations(),
        context
      );
      distributedByResults.add(createDistributedByResult(MISSING_VARIABLE_KEY, null, viewResult));
    }
  }

  private String getVariableName(final ExecutionContext<ProcessReportDataDto> context) {
    return getVariableDistributedByValueDto(context).getName();
  }

  private VariableType getVariableType(final ExecutionContext<ProcessReportDataDto> context) {
    return getVariableDistributedByValueDto(context).getType();
  }

  private String getNestedVariableValueFieldLabel(final VariableType type) {
    return getNestedVariableValueFieldForType(type);
  }

  private VariableDistributedByValueDto getVariableDistributedByValueDto(final ExecutionContext<ProcessReportDataDto> context) {
    return ((VariableDistributedByDto) context.getReportData().getDistributedBy()).getValue();
  }

  private AggregateByDateUnit getDistributeByDateUnit(final ExecutionContext<ProcessReportDataDto> context) {
    return context.getReportData().getConfiguration().getDistributeByDateVariableUnit();
  }
}
