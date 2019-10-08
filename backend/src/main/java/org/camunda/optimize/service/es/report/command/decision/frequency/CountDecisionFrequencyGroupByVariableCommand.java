/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.decision.frequency;

import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByVariableValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.report.command.decision.DecisionReportCommand;
import org.camunda.optimize.service.es.report.command.util.MapResultSortingUtility;
import org.camunda.optimize.service.es.report.result.decision.SingleDecisionMapReportResult;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.ReverseNested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static org.camunda.optimize.service.es.report.command.process.util.GroupByDateVariableIntervalSelection.createDateVariableAggregation;
import static org.camunda.optimize.service.es.report.command.util.IntervalAggregationService.RANGE_AGGREGATION;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableClauseIdField;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableStringValueField;
import static org.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueFieldForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.reverseNested;

public abstract class CountDecisionFrequencyGroupByVariableCommand
  extends DecisionReportCommand<SingleDecisionMapReportResult> {

  private static final String NESTED_AGGREGATION = "nested";
  private static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  private static final String VARIABLE_VALUE_AGGREGATION = "variableValues";
  private static final String FILTERED_PROCESS_INSTANCE_COUNT_AGGREGATION = "filteredProcInstCount";


  private final String variablePath;

  public CountDecisionFrequencyGroupByVariableCommand(final String variablePath) {
    this.variablePath = variablePath;
  }

  @Override
  protected SingleDecisionMapReportResult evaluate() {
    final DecisionReportDataDto reportData = getReportData();
    logger.debug(
      "Evaluating count decision instance frequency grouped by {} report " +
        "for decision definition with key [{}] and versions [{}]",
      variablePath,
      reportData.getDecisionDefinitionKey(),
      reportData.getDecisionDefinitionVersions()
    );

    final BoolQueryBuilder query = setupBaseQuery(reportData);

    DecisionGroupByVariableValueDto groupBy = getVariableGroupByDto();

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(createAggregation(groupBy.getId(), groupBy.getType()))
      .size(0);
    SearchRequest searchRequest = new SearchRequest(DECISION_INSTANCE_INDEX_NAME)
      .types(DECISION_INSTANCE_INDEX_NAME)
      .source(searchSourceBuilder);

    SearchResponse response;
    try {
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason =
        String.format(
          "Could not evaluate count decision instance frequency grouped by {} report " +
            "for decision definition with key [%s] and versions [%s]",
          reportData.getDecisionDefinitionKey(),
          reportData.getDecisionDefinitionVersions()
        );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    final DecisionReportMapResultDto mapResultDto = mapToReportResult(response);
    return new SingleDecisionMapReportResult(mapResultDto, reportDefinition);
  }

  @SuppressWarnings("unchecked")
  private DecisionGroupByVariableValueDto getVariableGroupByDto() {
    final DecisionReportDataDto reportData = getReportData();
    return ((DecisionGroupByDto<DecisionGroupByVariableValueDto>) reportData.getGroupBy()).getValue();
  }

  @Override
  protected void sortResultData(final SingleDecisionMapReportResult evaluationResult) {
    DecisionReportDataDto reportData = getReportData();
    final Optional<SortingDto> sorting = reportData.getConfiguration().getSorting();
    if (sorting.isPresent()) {
      MapResultSortingUtility.sortResultData(
        sorting.get(),
        evaluationResult,
        ((DecisionGroupByVariableValueDto) (reportData.getGroupBy().getValue())).getType()
      );
    } else if (VariableType.DATE.equals(getVariableGroupByDto().getType())) {
      MapResultSortingUtility.sortResultData(new SortingDto(SortingDto.SORT_BY_KEY, SortOrder.DESC), evaluationResult);
    }
  }

  private AggregationBuilder createAggregation(final String variableClauseId, VariableType variableType) {
    return AggregationBuilders
      .nested(NESTED_AGGREGATION, variablePath)
      .subAggregation(
        filter(
          FILTERED_VARIABLES_AGGREGATION,
          boolQuery()
            .must(termQuery(getVariableClauseIdField(variablePath), variableClauseId))
            .must(existsQuery(getVariableStringValueField(variablePath)))
        )
          .subAggregation(
            createVariableSubAggregation(variableClauseId, variableType)
          )
          .subAggregation(
            reverseNested(FILTERED_PROCESS_INSTANCE_COUNT_AGGREGATION)
          )
      );
  }

  private AggregationBuilder createVariableSubAggregation(final String variableClauseId,
                                                          final VariableType variableType) {
    AggregationBuilder variableAggregation = AggregationBuilders
      .terms(VARIABLE_VALUE_AGGREGATION)
      .size(configurationService.getEsAggregationBucketLimit())
      .field(getVariableStringValueField(variablePath));

    if (VariableType.DATE.equals(variableType)) {
      variableAggregation = createDateVariableAggregation(
        VARIABLE_VALUE_AGGREGATION,
        variableClauseId,
        getVariableClauseIdField(variablePath),
        getVariableValueFieldForType(variablePath, VariableType.DATE),
        DECISION_INSTANCE_INDEX_NAME,
        variablePath,
        intervalAggregationService,
        esClient,
        setupBaseQuery(getReportData())
      );
    }
    return variableAggregation;
  }


  private DecisionReportMapResultDto mapToReportResult(final SearchResponse response) {
    final DecisionReportMapResultDto resultDto = new DecisionReportMapResultDto();

    final Nested nested = response.getAggregations().get(NESTED_AGGREGATION);
    final Filter filteredVariables = nested.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
    MultiBucketsAggregation variableTerms = filteredVariables.getAggregations().get(VARIABLE_VALUE_AGGREGATION);

    if (variableTerms == null) {
      variableTerms = filteredVariables.getAggregations().get(RANGE_AGGREGATION);
    }

    final List<MapResultEntryDto<Long>> resultData = new ArrayList<>();
    for (MultiBucketsAggregation.Bucket b : variableTerms.getBuckets()) {
      resultData.add(new MapResultEntryDto<>(b.getKeyAsString(), b.getDocCount()));
    }

    final ReverseNested filteredProcessInstAggr = filteredVariables.getAggregations()
      .get(FILTERED_PROCESS_INSTANCE_COUNT_AGGREGATION);
    final long filteredProcInstCount = filteredProcessInstAggr.getDocCount();

    if (response.getHits().getTotalHits() > filteredProcInstCount) {
      resultData.add(new MapResultEntryDto<>(
        MISSING_VARIABLE_KEY,
        response.getHits().getTotalHits() - filteredProcInstCount
      ));
    }

    resultDto.setData(resultData);
    resultDto.setIsComplete(isResultComplete(variableTerms));
    resultDto.setDecisionInstanceCount(response.getHits().getTotalHits());

    return resultDto;
  }


  private boolean isResultComplete(MultiBucketsAggregation variableTerms) {
    return !(variableTerms instanceof Terms) || ((Terms) variableTerms).getSumOfOtherDocCounts() == 0L;
  }

}
