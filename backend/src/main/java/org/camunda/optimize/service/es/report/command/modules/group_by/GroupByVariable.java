/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.VariableGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.report.command.util.IntervalAggregationService;
import org.camunda.optimize.service.es.report.command.util.MapResultSortingUtility;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.ReverseNested;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static org.camunda.optimize.service.es.report.command.process.util.GroupByDateVariableIntervalSelection.createDateVariableAggregation;
import static org.camunda.optimize.service.es.report.command.util.IntervalAggregationService.RANGE_AGGREGATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableTypeField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.reverseNested;

@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GroupByVariable extends GroupByPart<ReportMapResultDto> {

  private static final String NESTED_AGGREGATION = "nested";
  private static final String VARIABLES_AGGREGATION = "variables";
  private static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  private static final String FILTERED_PROCESS_INSTANCE_COUNT_AGGREGATION = "filteredProcInstCount";

  private final ConfigurationService configurationService;
  private final IntervalAggregationService intervalAggregationService;
  private final OptimizeElasticsearchClient esClient;

  private VariableGroupByValueDto getVariableGroupByDto(final ProcessReportDataDto definitionData) {
    return ((VariableGroupByDto) definitionData.getGroupBy()).getValue();
  }

  @Override
  public AggregationBuilder createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                              final ProcessReportDataDto definitionData) {
    final VariableGroupByValueDto variableGroupByDto = getVariableGroupByDto(definitionData);

    AggregationBuilder variableSubAggregation =
      createVariableSubAggregation(variableGroupByDto, searchSourceBuilder.query());

    return nested(NESTED_AGGREGATION, VARIABLES)
      .subAggregation(
        filter(
          FILTERED_VARIABLES_AGGREGATION,
          boolQuery()
            .must(termQuery(getNestedVariableNameField(), variableGroupByDto.getName()))
            .must(termQuery(getNestedVariableTypeField(), variableGroupByDto.getType().getId()))
            .must(existsQuery(getNestedVariableValueField()))
        )
          .subAggregation(variableSubAggregation)
          .subAggregation(reverseNested(FILTERED_PROCESS_INSTANCE_COUNT_AGGREGATION))
      );
  }

  private AggregationBuilder createVariableSubAggregation(final VariableGroupByValueDto variableGroupByDto,
                                                          final QueryBuilder baseQuery) {
    AggregationBuilder aggregationBuilder = AggregationBuilders
      .terms(VARIABLES_AGGREGATION)
      .size(configurationService.getEsAggregationBucketLimit())
      .field(getNestedVariableValueFieldForType(variableGroupByDto.getType()));

    if (variableGroupByDto.getType().equals(VariableType.DATE)) {
      aggregationBuilder = createDateVariableAggregation(
        VARIABLES_AGGREGATION,
        variableGroupByDto.getName(),
        getNestedVariableNameField(),
        getNestedVariableValueFieldForType(VariableType.DATE),
        PROCESS_INSTANCE_INDEX_NAME,
        VARIABLES,
        intervalAggregationService,
        esClient,
        baseQuery
      );
    }

    aggregationBuilder.subAggregation(viewPart.createAggregation());
    return aggregationBuilder;
  }

  @Override
  public ReportMapResultDto retrieveQueryResult(SearchResponse response) {
    final Nested nested = response.getAggregations().get(NESTED_AGGREGATION);
    final Filter filteredVariables = nested.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
    MultiBucketsAggregation variableTerms = filteredVariables.getAggregations().get(VARIABLES_AGGREGATION);
    if (variableTerms == null) {
      variableTerms = filteredVariables.getAggregations().get(RANGE_AGGREGATION);
    }

    final List<MapResultEntryDto<Long>> resultData = new ArrayList<>();
    for (MultiBucketsAggregation.Bucket b : variableTerms.getBuckets()) {
      resultData.add(new MapResultEntryDto<>(b.getKeyAsString(), viewPart.retrieveQueryResult(b.getAggregations())));
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

    final ReportMapResultDto resultDto = new ReportMapResultDto();
    resultDto.setData(resultData);
    resultDto.setIsComplete(isResultComplete(variableTerms));
    resultDto.setInstanceCount(response.getHits().getTotalHits());

    return resultDto;
  }

  @Override
  public void sortResultData(final ProcessReportDataDto reportData, final ReportMapResultDto resultDto) {
    final Optional<SortingDto> sortingOpt = reportData.getConfiguration().getSorting();
    if (sortingOpt.isPresent()) {
      MapResultSortingUtility.sortResultData(
        sortingOpt.get(),
        resultDto,
        ((VariableGroupByValueDto) (reportData.getGroupBy().getValue())).getType()
      );

    } else if (VariableType.DATE.equals(getVariableGroupByDto(reportData).getType())) {
      MapResultSortingUtility.sortResultData(
        new SortingDto(SortingDto.SORT_BY_KEY, SortOrder.DESC),
        resultDto,
        ((VariableGroupByValueDto) (reportData.getGroupBy().getValue())).getType()
      );
    }
  }

  private boolean isResultComplete(MultiBucketsAggregation variableTerms) {
    return !(variableTerms instanceof Terms) || ((Terms) variableTerms).getSumOfOtherDocCounts() == 0L;
  }
}
