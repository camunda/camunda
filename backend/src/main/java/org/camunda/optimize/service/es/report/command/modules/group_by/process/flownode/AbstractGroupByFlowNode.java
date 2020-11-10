/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process.flownode;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByPart;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;

import java.util.Optional;

import static org.camunda.optimize.service.es.report.command.util.AggregationFilterUtil.addExecutionStateFilter;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_CANCELED;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_END_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.ACTIVITY_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.EVENTS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

public abstract class AbstractGroupByFlowNode extends GroupByPart<ProcessReportDataDto> {
  private static final String FLOW_NODES_AGGREGATION = "flowNodes";
  private static final String FILTERED_FLOW_NODES_AGGREGATION = "filteredFlowNodes";
  private static final String MI_BODY = "multiInstanceBody";

  protected AggregationBuilder createExecutionStateFilteredFlowNodeAggregation(
    final FlowNodeExecutionState flowNodeExecutionState,
    final AggregationBuilder subAggregation) {

    return nested(FLOW_NODES_AGGREGATION, EVENTS)
      .subAggregation(
        filter(
          FILTERED_FLOW_NODES_AGGREGATION,
          addExecutionStateFilter(
            boolQuery()
              .mustNot(
                termQuery(EVENTS + "." + ACTIVITY_TYPE, MI_BODY)
              ),
            flowNodeExecutionState,
            getExecutionStateFilterFieldForType(flowNodeExecutionState)
          )
        ).subAggregation(subAggregation)
      );
  }

  protected Optional<Filter> getExecutionStateFilteredFlowNodesAggregation(final SearchResponse response) {
    return getFlowNodesAggregation(response)
      .map(SingleBucketAggregation::getAggregations)
      .map(aggs -> aggs.get(FILTERED_FLOW_NODES_AGGREGATION));
  }

  protected Optional<Nested> getFlowNodesAggregation(final SearchResponse response) {
    return Optional.ofNullable(response.getAggregations())
      .map(aggs -> aggs.get(FLOW_NODES_AGGREGATION));
  }

  private static String getExecutionStateFilterFieldForType(final FlowNodeExecutionState flowNodeExecutionState) {
    if (FlowNodeExecutionState.CANCELED.equals(flowNodeExecutionState)) {
      return EVENTS + "." + ACTIVITY_CANCELED;
    }
    return EVENTS + "." + ACTIVITY_END_DATE;
  }

}
