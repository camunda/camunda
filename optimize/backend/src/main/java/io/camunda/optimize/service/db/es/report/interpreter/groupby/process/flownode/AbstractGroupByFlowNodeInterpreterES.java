/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.flownode;

import static io.camunda.optimize.service.db.es.filter.util.ModelElementFilterQueryUtilES.createModelElementAggregationFilter;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.AbstractProcessGroupByInterpreterES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import java.util.Optional;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.SingleBucketAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;

public abstract class AbstractGroupByFlowNodeInterpreterES
    extends AbstractProcessGroupByInterpreterES {
  private static final String FLOW_NODES_AGGREGATION = "flowNodes";
  private static final String FILTERED_FLOW_NODES_AGGREGATION = "filteredFlowNodes";

  protected abstract DefinitionService getDefinitionService();

  protected AggregationBuilder createFilteredFlowNodeAggregation(
      final ExecutionContext<ProcessReportDataDto, ?> context,
      final AggregationBuilder subAggregation) {
    return nested(FLOW_NODES_AGGREGATION, FLOW_NODE_INSTANCES)
        .subAggregation(
            filter(
                    FILTERED_FLOW_NODES_AGGREGATION,
                    createModelElementAggregationFilter(
                        context.getReportData(),
                        context.getFilterContext(),
                        getDefinitionService()))
                .subAggregation(subAggregation));
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
