/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.VariableGroupByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.VariableGroupByValueDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
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
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import static org.camunda.optimize.service.es.report.command.process.util.GroupByDateVariableIntervalSelection.createDateVariableAggregation;
import static org.camunda.optimize.service.es.report.command.util.IntervalAggregationService.RANGE_AGGREGATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableNameField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableTypeField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueField;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getNestedVariableValueFieldForType;
import static org.camunda.optimize.service.util.ProcessVariableHelper.getVariableUndefinedOrNullQuery;
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
public class ProcessGroupByVariable extends ProcessGroupByPart {

  private static final String NESTED_AGGREGATION = "nested";
  private static final String VARIABLES_AGGREGATION = "variables";
  private static final String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  private static final String FILTERED_PROCESS_INSTANCE_COUNT_AGGREGATION = "filteredProcInstCount";
  private static final String VARIABLES_PROCESS_INSTANCE_COUNT_AGGREGATION = "proc_inst_count";
  private static final String MISSING_VARIABLES_AGGREGATION = "missingVariables";

  private final ConfigurationService configurationService;
  private final IntervalAggregationService intervalAggregationService;
  private final OptimizeElasticsearchClient esClient;

  private VariableGroupByValueDto getVariableGroupByDto(final ExecutionContext<ProcessReportDataDto> context) {
    return ((VariableGroupByDto) context.getReportData().getGroupBy()).getValue();
  }

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    final VariableGroupByValueDto variableGroupByDto = getVariableGroupByDto(context);

    AggregationBuilder variableSubAggregation =
      createVariableSubAggregation(context, searchSourceBuilder.query());

    final NestedAggregationBuilder variableAggregation = nested(NESTED_AGGREGATION, VARIABLES)
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
    final AggregationBuilder undefinedOrNullVariableAggregation =
      createUndefinedOrNullVariableAggregation(context);
    return Arrays.asList(variableAggregation, undefinedOrNullVariableAggregation);
  }

  private AggregationBuilder createUndefinedOrNullVariableAggregation(final ExecutionContext<ProcessReportDataDto> context) {
    final VariableGroupByValueDto variableGroupByDto = getVariableGroupByDto(context);
    return filter(
      MISSING_VARIABLES_AGGREGATION,
      getVariableUndefinedOrNullQuery(variableGroupByDto.getName(), variableGroupByDto.getType())
    )
      .subAggregation(distributedByPart.createAggregation(context));
  }

  private AggregationBuilder createVariableSubAggregation(final ExecutionContext<ProcessReportDataDto> context,
                                                          final QueryBuilder baseQuery) {
    final VariableGroupByValueDto variableGroupByDto = getVariableGroupByDto(context);
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

    // the same process instance could have several same variable names -> do not count each but only the proc inst once
    AggregationBuilder operationsAggregation = reverseNested(VARIABLES_PROCESS_INSTANCE_COUNT_AGGREGATION)
      .subAggregation(distributedByPart.createAggregation(context));


    aggregationBuilder.subAggregation(operationsAggregation);
    return aggregationBuilder;
  }

  @Override
  public CompositeCommandResult retrieveQueryResult(SearchResponse response,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    final Nested nested = response.getAggregations().get(NESTED_AGGREGATION);
    final Filter filteredVariables = nested.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);
    MultiBucketsAggregation variableTerms = filteredVariables.getAggregations().get(VARIABLES_AGGREGATION);
    if (variableTerms == null) {
      variableTerms = filteredVariables.getAggregations().get(RANGE_AGGREGATION);
    }

    final List<GroupByResult> groupedData = new ArrayList<>();
    for (MultiBucketsAggregation.Bucket b : variableTerms.getBuckets()) {
      ReverseNested reverseNested = b.getAggregations().get(VARIABLES_PROCESS_INSTANCE_COUNT_AGGREGATION);
      final List<DistributedByResult> distribution =
        distributedByPart.retrieveResult(response, reverseNested.getAggregations(), context);
      groupedData.add(GroupByResult.createGroupByResult(b.getKeyAsString(), distribution));
    }

    final ReverseNested filteredProcessInstAggr = filteredVariables.getAggregations()
      .get(FILTERED_PROCESS_INSTANCE_COUNT_AGGREGATION);
    final long filteredProcInstCount = filteredProcessInstAggr.getDocCount();

    if (response.getHits().getTotalHits() > filteredProcInstCount) {

      final Filter aggregation = response.getAggregations().get(MISSING_VARIABLES_AGGREGATION);
      final List<DistributedByResult> missingVarsOperationResult =
        distributedByPart.retrieveResult(response, aggregation.getAggregations(), context);
      groupedData.add(GroupByResult.createGroupByResult(MISSING_VARIABLE_KEY, missingVarsOperationResult));
    }

    CompositeCommandResult compositeCommandResult = new CompositeCommandResult();
    compositeCommandResult.setGroups(groupedData);
    compositeCommandResult.setIsComplete(isResultComplete(variableTerms));

    return compositeCommandResult;
  }

  @Override
  public boolean getSortByKeyIsOfNumericType(final ExecutionContext<ProcessReportDataDto> context) {
    return VariableType.getNumericTypes().contains(getVariableGroupByDto(context).getType());
  }

  @Override
  public Optional<SortingDto> getSorting(final ExecutionContext<ProcessReportDataDto> context) {
    if(VariableType.DATE.equals(getVariableGroupByDto(context).getType())) {
      return Optional.of(new SortingDto(SortingDto.SORT_BY_KEY, SortOrder.DESC));
    } else {
      return super.getSorting(context);
    }
  }

  @Override
  protected void addGroupByAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto reportData) {
    reportData.setGroupBy(new VariableGroupByDto());
  }

  private boolean isResultComplete(MultiBucketsAggregation variableTerms) {
    return !(variableTerms instanceof Terms) || ((Terms) variableTerms).getSumOfOtherDocCounts() == 0L;
  }
}
