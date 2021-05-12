/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process.flownode;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.ProcessGroupByPart;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;

import java.util.Optional;

import static org.camunda.optimize.service.es.filter.util.modelelement.FlowNodeFilterQueryUtil.createFlowNodeAggregationFilter;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

public abstract class AbstractGroupByFlowNode extends ProcessGroupByPart {

  private static final String FLOW_NODES_AGGREGATION = "flowNodes";
  private static final String FILTERED_FLOW_NODES_AGGREGATION = "filteredFlowNodes";

  protected AggregationBuilder createFilteredFlowNodeAggregation(
    final ExecutionContext<ProcessReportDataDto> context,
    final AggregationBuilder subAggregation) {
    return nested(FLOW_NODES_AGGREGATION, FLOW_NODE_INSTANCES)
      .subAggregation(
        filter(
          FILTERED_FLOW_NODES_AGGREGATION,
          createFlowNodeAggregationFilter(context.getReportData())
        ).subAggregation(subAggregation)
      );
  }

  protected Optional<Filter> getFilteredFlowNodesAggregation(final SearchResponse response) {
    return getFlowNodesAggregation(response)
      .map(SingleBucketAggregation::getAggregations)
      .map(aggs -> aggs.get(FILTERED_FLOW_NODES_AGGREGATION));
  }

  protected Optional<Nested> getFlowNodesAggregation(final SearchResponse response) {
    return Optional.ofNullable(response.getAggregations())
      .map(aggs -> aggs.get(FLOW_NODES_AGGREGATION));
  }

}
