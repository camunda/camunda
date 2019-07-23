/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.VariableGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.report.command.aggregations.AggregationStrategy;
import org.camunda.optimize.service.es.report.command.process.ProcessReportCommand;
import org.camunda.optimize.service.es.report.command.util.MapResultSortingUtility;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapDurationReportResult;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
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

import static org.camunda.optimize.service.es.report.command.process.util.GroupByDateVariableIntervalSelection.createDateVariableAggregation;
import static org.camunda.optimize.service.es.report.command.util.IntervalAggregationService.RANGE_AGGREGATION;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameFieldLabelForType;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldLabelForType;
import static org.camunda.optimize.service.util.ProcessVariableHelper.variableTypeToFieldLabel;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

public abstract class AbstractProcessInstanceDurationByVariableCommand
  extends ProcessReportCommand<SingleProcessMapDurationReportResult> {

  public static final String NESTED_AGGREGATION = "nested";
  private static final String VARIABLES_AGGREGATION = "variables";
  public static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  private static final String REVERSE_NESTED_AGGREGATION = "reverseNested";

  protected AggregationStrategy aggregationStrategy;

  @Override
  protected SingleProcessMapDurationReportResult evaluate() {
    final ProcessReportDataDto processReportData = getReportData();
    logger.debug(
      "Evaluating average process instance duration grouped by variable report " +
        "for process definition key [{}] and versions [{}]",
      processReportData.getProcessDefinitionKey(),
      processReportData.getProcessDefinitionVersions()
    );

    BoolQueryBuilder query = setupBaseQuery(processReportData);

    VariableGroupByValueDto groupByVariable = getVariableGroupByDto();

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .fetchSource(false)
      .aggregation(createAggregation(groupByVariable.getName(), groupByVariable.getType()))
      .size(0);
    SearchRequest searchRequest = new SearchRequest(PROC_INSTANCE_TYPE)
      .types(PROC_INSTANCE_TYPE)
      .source(searchSourceBuilder);

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      final ProcessDurationReportMapResultDto resultDto = mapToReportResult(response);
      return new SingleProcessMapDurationReportResult(resultDto, reportDefinition);
    } catch (IOException e) {
      String reason =
        String.format(
          "Could not evaluate average process instance duration grouped by variable report " +
            "for process definition key [%s] and versions [%s]",
          processReportData.getProcessDefinitionKey(),
          processReportData.getProcessDefinitionVersions()
        );
      logger.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

  }

  private VariableGroupByValueDto getVariableGroupByDto() {
    final ProcessReportDataDto processReportData = getReportData();
    return ((VariableGroupByDto) processReportData.getGroupBy()).getValue();
  }


  @Override
  protected void sortResultData(final SingleProcessMapDurationReportResult evaluationResult) {
    final Optional<SortingDto> sortingOpt = ((ProcessReportDataDto) getReportData()).getParameters().getSorting();
    if (sortingOpt.isPresent()) {
      MapResultSortingUtility.sortResultData(sortingOpt.get(), evaluationResult);

    } else if (VariableType.DATE.equals(getVariableGroupByDto().getType())) {
      MapResultSortingUtility.sortResultData(
        new SortingDto(SortingDto.SORT_BY_KEY, SortOrder.DESC),
        evaluationResult
      );
    }
  }

  private AggregationBuilder createAggregation(String variableName, VariableType variableType) {
    String path = variableTypeToFieldLabel(variableType);
    String nestedVariableNameFieldLabel = getNestedVariableNameFieldLabelForType(variableType);
    String nestedVariableValueFieldLabel = getNestedVariableValueFieldLabelForType(variableType);

    AggregationBuilder aggregationBuilder = AggregationBuilders
      .terms(VARIABLES_AGGREGATION)
      .size(configurationService.getEsAggregationBucketLimit())
      .field(nestedVariableValueFieldLabel);

    if (VariableType.DATE.equals(variableType)) {
      aggregationBuilder = createDateVariableAggregation(
        variableName,
        nestedVariableNameFieldLabel,
        nestedVariableValueFieldLabel,
        intervalAggregationService,
        esClient,
        setupBaseQuery(getReportData())
      );
    }

    AggregationBuilder operationsAggregation = addOperationsAggregation(AggregationBuilders.reverseNested(
      REVERSE_NESTED_AGGREGATION));

    return nested(NESTED_AGGREGATION, path).subAggregation(
      filter(
        FILTERED_VARIABLES_AGGREGATION,
        boolQuery().must(termQuery(nestedVariableNameFieldLabel, variableName))
      ).subAggregation(aggregationBuilder.subAggregation(operationsAggregation)));
  }

  private AggregationBuilder addOperationsAggregation(AggregationBuilder aggregationBuilder) {
    return aggregationBuilder.subAggregation(createOperationsAggregation());
  }

  private ProcessDurationReportMapResultDto mapToReportResult(final SearchResponse response) {
    final ProcessDurationReportMapResultDto resultDto = new ProcessDurationReportMapResultDto();

    final Nested nested = response.getAggregations().get(NESTED_AGGREGATION);
    final Filter filteredVariables = nested.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);

    MultiBucketsAggregation variableTerms = filteredVariables.getAggregations().get(VARIABLES_AGGREGATION);
    if (variableTerms == null) {
      variableTerms = filteredVariables.getAggregations().get(RANGE_AGGREGATION);
    }

    final List<MapResultEntryDto<Long>> resultData = new ArrayList<>();
    for (MultiBucketsAggregation.Bucket b : variableTerms.getBuckets()) {
      ReverseNested reverseNested = b.getAggregations().get(REVERSE_NESTED_AGGREGATION);
      Long operationsResult = processAggregationOperation(reverseNested.getAggregations());
      resultData.add(new MapResultEntryDto<>(b.getKeyAsString(), operationsResult));
    }

    resultDto.setData(resultData);
    resultDto.setIsComplete(isResultComplete(variableTerms));
    resultDto.setProcessInstanceCount(response.getHits().getTotalHits());

    return resultDto;
  }

  private boolean isResultComplete(MultiBucketsAggregation variableTerms) {
    return !(variableTerms instanceof Terms) || ((Terms) variableTerms).getSumOfOtherDocCounts() == 0L;
  }


  protected abstract Long processAggregationOperation(Aggregations aggs);

  protected abstract AggregationBuilder createOperationsAggregation();

}
