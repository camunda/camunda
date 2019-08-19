/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable;

import org.apache.lucene.search.join.ScoreMode;
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

import static org.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static org.camunda.optimize.service.es.report.command.process.util.GroupByDateVariableIntervalSelection.createDateVariableAggregation;
import static org.camunda.optimize.service.es.report.command.util.IntervalAggregationService.RANGE_AGGREGATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableTypeField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.reverseNested;

public abstract class AbstractProcessInstanceDurationByVariableCommand
  extends ProcessReportCommand<SingleProcessMapDurationReportResult> {

  public static final String NESTED_AGGREGATION = "nested";
  private static final String VARIABLES_AGGREGATION = "variables";
  public static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  private static final String REVERSE_NESTED_AGGREGATION = "reverseNested";
  private static final String MISSING_VARIABLES_AGGREGATION = "missingVariables";
  private static final String FILTERED_PROCESS_INSTANCE_COUNT_AGGREGATION = "filteredProcInstCount";

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
      .aggregation(createVariableAggregation(groupByVariable.getName(), groupByVariable.getType()))
      .aggregation(createMissingVariableAggregation(groupByVariable.getName(), groupByVariable.getType()))
      .size(0);
    SearchRequest searchRequest = new SearchRequest(PROCESS_INSTANCE_INDEX_NAME)
      .types(PROCESS_INSTANCE_INDEX_NAME)
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


  private AggregationBuilder createVariableAggregation(String variableName, VariableType variableType) {
    AggregationBuilder variableSubAggregation =
      createVariableSubAggregation(variableName, variableType);

    return nested(NESTED_AGGREGATION, VARIABLES)
      .subAggregation(
        filter(
          FILTERED_VARIABLES_AGGREGATION,
          boolQuery().must(termQuery(getNestedVariableNameField(), variableName))
          .must(termQuery(getNestedVariableTypeField(), variableType.getId()))
        )
          .subAggregation(variableSubAggregation)
          .subAggregation(reverseNested(FILTERED_PROCESS_INSTANCE_COUNT_AGGREGATION))
      );
  }

  private AggregationBuilder createVariableSubAggregation(final String variableName, final VariableType variableType) {
    AggregationBuilder variableSubAggregation = AggregationBuilders
      .terms(VARIABLES_AGGREGATION)
      .size(configurationService.getEsAggregationBucketLimit())
      .field(getNestedVariableValueFieldForType(variableType));

    if (VariableType.DATE.equals(variableType)) {
      variableSubAggregation = createDateVariableAggregation(
        VARIABLES_AGGREGATION,
        variableName,
        getNestedVariableNameField(),
        getNestedVariableValueFieldForType(VariableType.DATE),
        PROCESS_INSTANCE_INDEX_NAME,
        VARIABLES,
        intervalAggregationService,
        esClient,
        setupBaseQuery(getReportData())
      );
    }
    AggregationBuilder operationsAggregation = reverseNested(REVERSE_NESTED_AGGREGATION)
      .subAggregation(createOperationsAggregation());

    variableSubAggregation.subAggregation(operationsAggregation);
    return variableSubAggregation;
  }

  private AggregationBuilder createMissingVariableAggregation(String variableName, VariableType variableType) {
    return filter(
      MISSING_VARIABLES_AGGREGATION,
      boolQuery().mustNot(nestedQuery(
        VARIABLES,
        boolQuery()
          .must(termQuery(getNestedVariableNameField(), variableName))
          .must(termQuery(getNestedVariableTypeField(), variableType.getId())),
        ScoreMode.None
      ).ignoreUnmapped(true))
    ).subAggregation(createOperationsAggregation());
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


    final ReverseNested filteredProcInstCountAggregation = filteredVariables.getAggregations()
      .get(FILTERED_PROCESS_INSTANCE_COUNT_AGGREGATION);
    final long filteredProcInstCount = filteredProcInstCountAggregation.getDocCount();

    // if there are process instances where the variable is missing then add a 'missing' bucket with aggs results
    if (response.getHits().getTotalHits() > filteredProcInstCount) {

      final Filter aggregation = response.getAggregations().get(MISSING_VARIABLES_AGGREGATION);
      final Long missingVarsOperationResult = processAggregationOperation(aggregation.getAggregations());

      resultData.add(
        new MapResultEntryDto<>(
          MISSING_VARIABLE_KEY,
          missingVarsOperationResult
        ));
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
