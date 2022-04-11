/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.es.report.command.service.VariableAggregationService;
import org.camunda.optimize.service.es.report.command.util.VariableAggregationContext;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.nested.ReverseNested;
import org.elasticsearch.search.aggregations.bucket.nested.ReverseNestedAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.camunda.optimize.dto.optimize.ReportConstants.MISSING_VARIABLE_KEY;
import static org.camunda.optimize.service.es.filter.util.ModelElementFilterQueryUtil.createModelElementAggregationFilter;
import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import static org.camunda.optimize.service.es.report.command.service.VariableAggregationService.FILTERED_INSTANCE_COUNT_AGGREGATION;
import static org.camunda.optimize.service.es.report.command.service.VariableAggregationService.FILTERED_VARIABLES_AGGREGATION;
import static org.camunda.optimize.service.es.report.command.service.VariableAggregationService.MISSING_VARIABLES_AGGREGATION;
import static org.camunda.optimize.service.es.report.command.service.VariableAggregationService.NESTED_FLOWNODE_AGGREGATION;
import static org.camunda.optimize.service.es.report.command.service.VariableAggregationService.NESTED_VARIABLE_AGGREGATION;
import static org.camunda.optimize.service.es.report.command.service.VariableAggregationService.VARIABLES_AGGREGATION;
import static org.camunda.optimize.service.es.report.command.service.VariableAggregationService.VARIABLES_INSTANCE_COUNT_AGGREGATION;
import static org.camunda.optimize.service.es.report.command.service.VariableAggregationService.VARIABLE_HISTOGRAM_AGGREGATION;
import static org.camunda.optimize.service.es.report.command.util.FilterLimitedAggregationUtil.FILTER_LIMITED_AGGREGATION;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;
import static org.elasticsearch.search.aggregations.AggregationBuilders.reverseNested;

@RequiredArgsConstructor
public abstract class AbstractGroupByVariable<Data extends SingleReportDataDto> extends GroupByPart<Data> {

  public static final String FILTERED_FLOW_NODE_AGGREGATION = "filteredFlowNodeAggregation";

  private final VariableAggregationService variableAggregationService;
  private final DefinitionService definitionService;

  protected abstract String getVariableName(final ExecutionContext<Data> context);

  protected abstract VariableType getVariableType(final ExecutionContext<Data> context);

  protected abstract String getNestedVariableNameFieldLabel();

  protected abstract String getNestedVariableTypeField();

  protected abstract String getNestedVariableValueFieldLabel(final VariableType type);

  protected abstract String getVariablePath();

  protected abstract BoolQueryBuilder getVariableUndefinedOrNullQuery(final ExecutionContext<Data> context);

  @Override
  public Optional<MinMaxStatDto> getMinMaxStats(final ExecutionContext<Data> context,
                                                final BoolQueryBuilder baseQuery) {
    if (isGroupedByNumberVariable(getVariableType(context)) || VariableType.DATE.equals(getVariableType(context))) {
      return Optional.of(
        variableAggregationService.getVariableMinMaxStats(
          getVariableType(context),
          getVariableName(context),
          getVariablePath(),
          getNestedVariableNameFieldLabel(),
          getNestedVariableValueFieldLabel(getVariableType(context)),
          getIndexNames(context),
          baseQuery
        )
      );
    }
    return Optional.empty();
  }

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<Data> context) {
    // base query used for distrBy date reports
    context.setDistributedByMinMaxBaseQuery(searchSourceBuilder.query());

    final ReverseNestedAggregationBuilder reverseNestedAggregationBuilder =
      reverseNested(VARIABLES_INSTANCE_COUNT_AGGREGATION);
    createDistributedBySubAggregations(context).forEach(reverseNestedAggregationBuilder::subAggregation);
    final VariableAggregationContext varAggContext = VariableAggregationContext.builder()
      .variableName(getVariableName(context))
      .variableType(getVariableType(context))
      .variablePath(getVariablePath())
      .nestedVariableNameField(getNestedVariableNameFieldLabel())
      .nestedVariableValueFieldLabel(getNestedVariableValueFieldLabel(getVariableType(context)))
      .indexNames(getIndexNames(context))
      .timezone(context.getTimezone())
      .customBucketDto(context.getReportData().getConfiguration().getCustomBucket())
      .dateUnit(getGroupByDateUnit(context))
      .baseQueryForMinMaxStats(searchSourceBuilder.query())
      .subAggregations(Collections.singletonList(reverseNestedAggregationBuilder))
      .combinedRangeMinMaxStats(context.getCombinedRangeMinMaxStats().orElse(null))
      .filterContext(context.getFilterContext())
      .build();

    final Optional<AggregationBuilder> variableSubAggregation =
      variableAggregationService.createVariableSubAggregation(varAggContext);

    final Optional<QueryBuilder> variableFilterQueryBuilder =
      variableAggregationService.createVariableFilterQuery(varAggContext);

    if (!variableSubAggregation.isPresent()) {
      // if the report contains no instances and is grouped by date variable, this agg will not be present
      // as it is based on instance data
      return Collections.emptyList();
    }

    final NestedAggregationBuilder variableAggregation = nested(NESTED_VARIABLE_AGGREGATION, getVariablePath())
      .subAggregation(
        filter(
          FILTERED_VARIABLES_AGGREGATION,
          boolQuery()
            .must(termQuery(getNestedVariableNameFieldLabel(), getVariableName(context)))
            .must(termQuery(getNestedVariableTypeField(), getVariableType(context).getId()))
            .must(existsQuery(getNestedVariableValueFieldLabel(VariableType.STRING)))
            .must(variableFilterQueryBuilder.orElseGet(QueryBuilders::matchAllQuery))
        )
          .subAggregation(variableSubAggregation.get())
          .subAggregation(reverseNested(FILTERED_INSTANCE_COUNT_AGGREGATION))
      );
    final AggregationBuilder undefinedOrNullVariableAggregation =
      createUndefinedOrNullVariableAggregation(context);
    return Arrays.asList(variableAggregation, undefinedOrNullVariableAggregation);
  }

  private List<AggregationBuilder> createDistributedBySubAggregations(final ExecutionContext<Data> context) {
    if (distributedByPart.isFlownodeReport()) {
      // Nest the distributed by part to ensure the aggregation is on flownode level
      final FilterAggregationBuilder filterAggregationBuilder = filter(
        FILTERED_FLOW_NODE_AGGREGATION,
        createModelElementAggregationFilter(
          (ProcessReportDataDto) context.getReportData(), context.getFilterContext(), definitionService
        )
      );
      distributedByPart.createAggregations(context).forEach(filterAggregationBuilder::subAggregation);
      return Collections.singletonList(
        nested(NESTED_FLOWNODE_AGGREGATION, FLOW_NODE_INSTANCES)
          .subAggregation(filterAggregationBuilder)
      );
    }
    return distributedByPart.createAggregations(context);
  }

  private AggregationBuilder createUndefinedOrNullVariableAggregation(final ExecutionContext<Data> context) {
    final FilterAggregationBuilder filterAggregationBuilder = filter(
      MISSING_VARIABLES_AGGREGATION,
      getVariableUndefinedOrNullQuery(context)
    );
    createDistributedBySubAggregations(context).forEach(filterAggregationBuilder::subAggregation);
    return filterAggregationBuilder;
  }

  @Override
  public void addQueryResult(final CompositeCommandResult compositeCommandResult,
                             final SearchResponse response,
                             final ExecutionContext<Data> context) {
    if (response.getAggregations() == null) {
      // aggregations are null if there are no instances in the report and it is grouped by date variable
      return;
    }

    final Nested nested = response.getAggregations().get(NESTED_VARIABLE_AGGREGATION);
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

    // enrich context with complete set of distributed by keys
    distributedByPart.enrichContextWithAllExpectedDistributedByKeys(
      context,
      filteredParentAgg.getAggregations()
    );

    final List<GroupByResult> groupedData = new ArrayList<>();
    for (Map.Entry<String, Aggregations> keyToAggregationEntry : bucketAggregations.entrySet()) {
      final List<DistributedByResult> distribution =
        distributedByPart.retrieveResult(
          response,
          variableAggregationService.retrieveSubAggregationFromBucketMapEntry(keyToAggregationEntry),
          context
        );
      groupedData.add(GroupByResult.createGroupByResult(keyToAggregationEntry.getKey(), distribution));
    }

    addMissingVariableBuckets(groupedData, response, context);

    compositeCommandResult.setGroups(groupedData);
    if (VariableType.DATE.equals(getVariableType(context))) {
      compositeCommandResult.setGroupBySorting(new ReportSortingDto(ReportSortingDto.SORT_BY_KEY, SortOrder.ASC));
    }
    compositeCommandResult.setGroupByKeyOfNumericType(getSortByKeyIsOfNumericType(context));
    compositeCommandResult.setDistributedByKeyOfNumericType(distributedByPart.isKeyOfNumericType(context));
  }

  private void addMissingVariableBuckets(final List<GroupByResult> groupedData,
                                         final SearchResponse response,
                                         final ExecutionContext<Data> context) {
    final Nested nested = response.getAggregations().get(NESTED_VARIABLE_AGGREGATION);
    final Filter filteredVariables = nested.getAggregations().get(FILTERED_VARIABLES_AGGREGATION);

    final ReverseNested filteredInstAggr = filteredVariables.getAggregations()
      .get(FILTERED_INSTANCE_COUNT_AGGREGATION);
    if (response.getHits().getTotalHits().value > filteredInstAggr.getDocCount()) {
      final List<DistributedByResult> missingVarsOperationResult =
        distributedByPart.retrieveResult(
          response,
          retrieveAggregationsForMissingVariables(response),
          context
        );
      groupedData.add(GroupByResult.createGroupByResult(MISSING_VARIABLE_KEY, missingVarsOperationResult));
    }
  }

  private Aggregations retrieveAggregationsForMissingVariables(final SearchResponse response) {
    final Filter aggregation = response.getAggregations().get(MISSING_VARIABLES_AGGREGATION);
    final ParsedNested nestedFlowNodeAgg = aggregation.getAggregations().get(NESTED_FLOWNODE_AGGREGATION);
    if (nestedFlowNodeAgg == null) {
      return aggregation.getAggregations(); // this is an instance report
    } else {
      Aggregations flowNodeAggs = nestedFlowNodeAgg.getAggregations(); // this is a flownode report
      final ParsedFilter filteredAgg = flowNodeAggs.get(FILTERED_FLOW_NODE_AGGREGATION);
      return filteredAgg.getAggregations();
    }
  }

  private boolean getSortByKeyIsOfNumericType(final ExecutionContext<Data> context) {
    return VariableType.getNumericTypes().contains(getVariableType(context));
  }

  private AggregateByDateUnit getGroupByDateUnit(final ExecutionContext<Data> context) {
    return context.getReportData().getConfiguration().getGroupByDateVariableUnit();
  }

  private boolean isGroupedByNumberVariable(final VariableType varType) {
    return VariableType.getNumericTypes().contains(varType);
  }
}
