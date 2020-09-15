/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by.process;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.VariableDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.VariableDistributedByValueDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.MinMaxStatsService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.service.NumberVariableAggregationService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.nested.ReverseNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_DURATION_PROPERTY;
import static org.camunda.optimize.dto.optimize.ReportConstants.VIEW_FREQUENCY_PROPERTY;
import static org.camunda.optimize.service.es.report.command.modules.group_by.AbstractGroupByVariable.RANGE_AGGREGATION;
import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;
import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult.createResultWithEmptyValue;
import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult.createResultWithZeroValue;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.FILTER_LIMITED_AGGREGATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.util.ProcessVariableHelper.createFilterForUndefinedOrNullQueryBuilder;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableTypeField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
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
  private static final String NESTED_AGGREGATION = "nested";
  private static final String VARIABLES_AGGREGATION = "variables";
  private static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  private static final String FILTERED_INSTANCE_COUNT_AGGREGATION = "filteredInstCount";
  private static final String VARIABLES_INSTANCE_COUNT_AGGREGATION = "inst_count";
  private static final String MISSING_VARIABLES_AGGREGATION = "missingVariables";
  private static final String PARENT_FILTER_AGGREGATION = "matchAllFilter";

  private final ConfigurationService configurationService;
  private final MinMaxStatsService minMaxStatsService;
  private final NumberVariableAggregationService numberVariableAggregationService;

  @Override
  public Optional<Boolean> isKeyOfNumericType(final ExecutionContext<ProcessReportDataDto> context) {
    return Optional.of(VariableType.getNumericTypes().contains(getVariableType(context)));
  }

  @Override
  public AggregationBuilder createAggregation(final ExecutionContext<ProcessReportDataDto> context) {
    Optional<AggregationBuilder> variableSubAggregation = createVariableSubAggregation(context);

    if (!variableSubAggregation.isPresent()) {
      // if the report contains no instances and is distributed by date variable, this agg will not be present
      // as it is based on instance data
      return viewPart.createAggregation(context);
    }

    // Add a parent match all filter agg to be able to retrieve all undefined/null variables as a sibling aggregation
    // to the nested existing variable filter
    return filter(PARENT_FILTER_AGGREGATION, matchAllQuery())
      .subAggregation(createUndefinedOrNullVariableAggregation(context))
      .subAggregation(
        nested(NESTED_AGGREGATION, VARIABLES)
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
          ));
  }

  private Optional<AggregationBuilder> createVariableSubAggregation(final ExecutionContext<ProcessReportDataDto> context) {
    Optional<AggregationBuilder> aggregationBuilder = Optional.empty();
    final VariableType type = getVariableType(context);
    switch (getVariableType(context)) {
      case STRING:
      case BOOLEAN:
        aggregationBuilder = Optional.of(AggregationBuilders
                                           .terms(VARIABLES_AGGREGATION)
                                           .size(configurationService.getEsAggregationBucketLimit())
                                           .field(getNestedVariableValueFieldLabel(VariableType.STRING)));
        break;
      case DATE:
        // TODO implement with OPT-4254
        break;
      default:
        if (VariableType.getNumericTypes().contains(type)) {
          aggregationBuilder =
            numberVariableAggregationService.createNumberVariableAggregation(
              context,
              type,
              getNestedVariableValueFieldLabel(type),
              getMinMaxStats(context),
              context.getReportData().getConfiguration().getDistributeByCustomBucket()
            );
        }
    }

    final AggregationBuilder nestedViewAgg = reverseNested(VARIABLES_INSTANCE_COUNT_AGGREGATION)
      .subAggregation(viewPart.createAggregation(context));

    return aggregationBuilder.map(builder -> builder.subAggregation(nestedViewAgg));
  }

  @Override
  public List<CompositeCommandResult.DistributedByResult> retrieveResult(final SearchResponse response,
                                                                         final Aggregations aggregations,
                                                                         final ExecutionContext<ProcessReportDataDto> context) {
    final ParsedFilter parentFilterAgg = aggregations.get(PARENT_FILTER_AGGREGATION);
    if (parentFilterAgg == null) {
      // TODO implement with OPT-4254
      return Collections.emptyList();
    }

    final Nested nested = parentFilterAgg.getAggregations().get(NESTED_AGGREGATION);
    final Filter filteredVariables = nested.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
    Filter filteredParentAgg = filteredVariables.getAggregations().get(FILTER_LIMITED_AGGREGATION);
    if (filteredParentAgg == null) {
      filteredParentAgg = filteredVariables;
    }
    MultiBucketsAggregation variableTerms = filteredParentAgg.getAggregations().get(VARIABLES_AGGREGATION);
    if (variableTerms == null) {
      variableTerms = filteredParentAgg.getAggregations().get(RANGE_AGGREGATION);
    }

    List<CompositeCommandResult.DistributedByResult> distributedByResults = new ArrayList<>();

    for (MultiBucketsAggregation.Bucket bucket : variableTerms.getBuckets()) {
      ReverseNested reverseNested = bucket.getAggregations().get(VARIABLES_INSTANCE_COUNT_AGGREGATION);
      final CompositeCommandResult.ViewResult viewResult = viewPart.retrieveResult(
        response,
        reverseNested == null ? bucket.getAggregations() : reverseNested.getAggregations(),
        context
      );
      distributedByResults.add(createDistributedByResult(bucket.getKeyAsString(), null, viewResult));
    }

    addMissingVariableBuckets(distributedByResults, response, aggregations, context);
    addEmptyMissingDistributedByResults(distributedByResults, context);

    return distributedByResults;
  }

  @Override
  protected void addAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.getConfiguration().setDistributedBy(new VariableDistributedByDto());
  }

  @Override
  public void enrichContextWithAllExpectedDistributedByKeys(
    final ExecutionContext<ProcessReportDataDto> context,
    final Aggregations aggregations) {
    final ParsedFilter parentFilterAgg = aggregations.get(PARENT_FILTER_AGGREGATION);
    if (parentFilterAgg == null) {
      // there are no distributedBy keys
      context.setAllDistributedByKeys(new HashSet<>());
      return;
    }

    Set<String> allDistributedByKeys = new HashSet<>();
    if (!VariableType.getNumericTypes().contains(getVariableType(context))) {
      // missing distrBy keys evaluation only required if it's not a range (number var) aggregation
      final ParsedNested nestedAgg = parentFilterAgg.getAggregations().get(NESTED_AGGREGATION);
      final ParsedFilter filteredVarAgg = nestedAgg.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
      final ParsedStringTerms varNamesAgg = filteredVarAgg.getAggregations().get(VARIABLES_AGGREGATION);
      allDistributedByKeys = varNamesAgg.getBuckets()
        .stream()
        .map(MultiBucketsAggregation.Bucket::getKeyAsString)
        .collect(toSet());
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
    context.getAllDistributedByKeys().stream()
      .filter(key -> distributedByResults.stream()
        .noneMatch(distributedByResult -> distributedByResult.getKey().equals(key)))
      .map(key -> createEmptyDistributedBy(key, context))
      .forEach(distributedByResults::add);
  }

  private CompositeCommandResult.DistributedByResult createEmptyDistributedBy(
    final String distributedByKey,
    final ExecutionContext<ProcessReportDataDto> context) {
    switch (context.getReportData().getView().getProperty().toString()) {
      case VIEW_DURATION_PROPERTY:
        return createResultWithEmptyValue(distributedByKey);
      case VIEW_FREQUENCY_PROPERTY:
      default:
        return createResultWithZeroValue(distributedByKey);
    }
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

  private AggregationBuilder createUndefinedOrNullVariableAggregation(final ExecutionContext<ProcessReportDataDto> context) {
    return filter(
      MISSING_VARIABLES_AGGREGATION,
      createFilterForUndefinedOrNullQueryBuilder(getVariableName(context), getVariableType(context))
    )
      .subAggregation(viewPart.createAggregation(context));
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
    return ((VariableDistributedByDto) context.getReportData().getConfiguration().getDistributedBy()).getValue();
  }

  private MinMaxStatDto getMinMaxStats(final ExecutionContext<ProcessReportDataDto> context) {
    final BoolQueryBuilder filterQuery = boolQuery().must(
      termQuery(getNestedVariableNameField(), getVariableName(context))
    );
    if (VariableType.getNumericTypes().contains(getVariableType(context))) {
      return minMaxStatsService.getSingleFieldMinMaxStats(
        context.getDistributedByMinMaxBaseQuery(),
        PROCESS_INSTANCE_INDEX_NAME,
        getNestedVariableValueFieldLabel(getVariableType(context)),
        VARIABLES,
        filterQuery
      );
    }
    return new MinMaxStatDto(Double.NaN, Double.NaN); // not yet implemented for other variable types
  }
}
