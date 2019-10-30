/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.decision.variable;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByVariableValueDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.group_by.decision.DecisionGroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ReverseNested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import static org.camunda.optimize.service.es.report.command.process.util.GroupByDateVariableIntervalSelection.createDateVariableAggregation;
import static org.camunda.optimize.service.es.report.command.util.IntervalAggregationService.RANGE_AGGREGATION;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableClauseIdField;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableStringValueField;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableTypeField;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableUndefinedOrNullQuery;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueFieldForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.reverseNested;

@RequiredArgsConstructor
public abstract class AbstractDecisionGroupByVariable extends DecisionGroupByPart {

  private static final String NESTED_AGGREGATION = "nested";
  private static final String VARIABLES_AGGREGATION = "variables";
  private static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  private static final String FILTERED_INSTANCE_COUNT_AGGREGATION = "filteredInstCount";
  private static final String VARIABLES_INSTANCE_COUNT_AGGREGATION = "inst_count";
  private static final String MISSING_VARIABLES_AGGREGATION = "missingVariables";

  private final ConfigurationService configurationService;
  private final IntervalAggregationService intervalAggregationService;
  private final OptimizeElasticsearchClient esClient;

  protected abstract DecisionGroupByVariableValueDto getVariableGroupByDto(final ExecutionContext<DecisionReportDataDto> context);

  protected abstract DecisionGroupByDto<DecisionGroupByVariableValueDto> getDecisionGroupByVariableType();

  protected abstract String getVariablePath();

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<DecisionReportDataDto> context) {
    final DecisionGroupByVariableValueDto variableGroupByDto = getVariableGroupByDto(context);

    AggregationBuilder variableSubAggregation =
      createVariableSubAggregation(context, searchSourceBuilder.query());

    final NestedAggregationBuilder variableAggregation = nested(NESTED_AGGREGATION, getVariablePath())
      .subAggregation(
        filter(
          FILTERED_VARIABLES_AGGREGATION,
          boolQuery()
            .must(termQuery(getVariableClauseIdField(getVariablePath()), variableGroupByDto.getId()))
            .must(termQuery(getVariableTypeField(getVariablePath()), variableGroupByDto.getType().getId()))
            .must(existsQuery(getVariableStringValueField(getVariablePath())))
        )
          .subAggregation(variableSubAggregation)
          .subAggregation(reverseNested(FILTERED_INSTANCE_COUNT_AGGREGATION))
      );
    final AggregationBuilder undefinedOrNullVariableAggregation =
      createUndefinedOrNullVariableAggregation(context);
    return Arrays.asList(variableAggregation, undefinedOrNullVariableAggregation);
  }

  private AggregationBuilder createUndefinedOrNullVariableAggregation(final ExecutionContext<DecisionReportDataDto> context) {
    final DecisionGroupByVariableValueDto variableGroupByDto = getVariableGroupByDto(context);
    return filter(
      MISSING_VARIABLES_AGGREGATION,
      getVariableUndefinedOrNullQuery(variableGroupByDto.getId(), getVariablePath(), variableGroupByDto.getType())
    )
      .subAggregation(distributedByPart.createAggregation(context));
  }

  private AggregationBuilder createVariableSubAggregation(final ExecutionContext<DecisionReportDataDto> context,
                                                          final QueryBuilder baseQuery) {
    final DecisionGroupByVariableValueDto variableGroupByDto = getVariableGroupByDto(context);
    AggregationBuilder aggregationBuilder = AggregationBuilders
      .terms(VARIABLES_AGGREGATION)
      .size(configurationService.getEsAggregationBucketLimit())
      .field(getVariableStringValueField(getVariablePath()));

    if (VariableType.DATE.equals(variableGroupByDto.getType())) {
      aggregationBuilder = createDateVariableAggregation(
        VARIABLES_AGGREGATION,
        variableGroupByDto.getId(),
        getVariableClauseIdField(getVariablePath()),
        getVariableValueFieldForType(getVariablePath(), VariableType.DATE),
        DECISION_INSTANCE_INDEX_NAME,
        getVariablePath(),
        intervalAggregationService,
        esClient,
        baseQuery
      );
    }

    AggregationBuilder operationsAggregation = reverseNested(VARIABLES_INSTANCE_COUNT_AGGREGATION)
      .subAggregation(distributedByPart.createAggregation(context));


    aggregationBuilder.subAggregation(operationsAggregation);
    return aggregationBuilder;
  }

  @Override
  public CompositeCommandResult retrieveQueryResult(SearchResponse response, final DecisionReportDataDto reportData) {
    final Nested nested = response.getAggregations().get(NESTED_AGGREGATION);
    final Filter filteredVariables = nested.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
    MultiBucketsAggregation variableTerms = filteredVariables.getAggregations().get(VARIABLES_AGGREGATION);
    if (variableTerms == null) {
      variableTerms = filteredVariables.getAggregations().get(RANGE_AGGREGATION);
    }

    final List<GroupByResult> groupedData = new ArrayList<>();
    for (MultiBucketsAggregation.Bucket b : variableTerms.getBuckets()) {
      ReverseNested reverseNested = b.getAggregations().get(VARIABLES_INSTANCE_COUNT_AGGREGATION);
      final List<DistributedByResult> distribution =
        distributedByPart.retrieveResult(reverseNested.getAggregations(), reportData);
      groupedData.add(GroupByResult.createGroupByResult(b.getKeyAsString(), distribution));
    }

    final ReverseNested filteredInstAggr = filteredVariables.getAggregations()
      .get(FILTERED_INSTANCE_COUNT_AGGREGATION);
    final long filteredProcInstCount = filteredInstAggr.getDocCount();

    if (response.getHits().getTotalHits() > filteredProcInstCount) {

      final Filter aggregation = response.getAggregations().get(MISSING_VARIABLES_AGGREGATION);
      final List<DistributedByResult> missingVarsOperationResult =
        distributedByPart.retrieveResult(aggregation.getAggregations(), reportData);
      groupedData.add(GroupByResult.createGroupByResult(MISSING_VARIABLE_KEY, missingVarsOperationResult));
    }

    CompositeCommandResult compositeCommandResult = new CompositeCommandResult();
    compositeCommandResult.setGroups(groupedData);
    compositeCommandResult.setIsComplete(isResultComplete(variableTerms));

    return compositeCommandResult;
  }

  @Override
  public boolean getSortByKeyIsOfNumericType(final ExecutionContext<DecisionReportDataDto> context) {
    return VariableType.getNumericTypes().contains(getVariableGroupByDto(context).getType());
  }

  @Override
  public Optional<SortingDto> getSorting(final ExecutionContext<DecisionReportDataDto> context) {
    if (VariableType.DATE.equals(getVariableGroupByDto(context).getType())) {
      return Optional.of(new SortingDto(SortingDto.SORT_BY_KEY, SortOrder.DESC));
    } else {
      return super.getSorting(context);
    }
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final DecisionReportDataDto reportData) {
    reportData.setGroupBy(getDecisionGroupByVariableType());
  }


  private boolean isResultComplete(MultiBucketsAggregation variableTerms) {
    return !(variableTerms instanceof Terms) || ((Terms) variableTerms).getSumOfOtherDocCounts() == 0L;
  }
}
