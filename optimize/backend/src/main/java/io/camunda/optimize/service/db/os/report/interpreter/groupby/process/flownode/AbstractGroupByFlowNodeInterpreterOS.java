/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process.flownode;

import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.os.report.filter.util.ModelElementFilterQueryUtilOS;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.groupby.process.AbstractProcessGroupByInterpreterOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import java.util.Map;
import java.util.Optional;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.FilterAggregate;
import org.opensearch.client.opensearch._types.aggregations.NestedAggregate;
import org.opensearch.client.opensearch.core.SearchResponse;

public abstract class AbstractGroupByFlowNodeInterpreterOS
    extends AbstractProcessGroupByInterpreterOS {
  private static final String FLOW_NODES_AGGREGATION = "flowNodes";
  private static final String FILTERED_FLOW_NODES_AGGREGATION = "filteredFlowNodes";

  protected abstract DefinitionService getDefinitionService();

  protected Map<String, Aggregation> createFilteredFlowNodeAggregation(
      final ExecutionContext<ProcessReportDataDto, ?> context,
      final Map<String, Aggregation> subAggregations) {
    final Aggregation filteredFlowNodesAggregation =
        new Aggregation.Builder()
            .filter(
                ModelElementFilterQueryUtilOS.createModelElementAggregationFilter(
                        context.getReportData(), context.getFilterContext(), getDefinitionService())
                    .build()
                    .toQuery())
            .aggregations(subAggregations)
            .build();

    final Aggregation aggregation =
        new Aggregation.Builder()
            .nested(n -> n.path(FLOW_NODE_INSTANCES))
            .aggregations(FILTERED_FLOW_NODES_AGGREGATION, filteredFlowNodesAggregation)
            .build();

    return Map.of(FLOW_NODES_AGGREGATION, aggregation);
  }

  protected Optional<FilterAggregate> getFilteredFlowNodesAggregation(
      final SearchResponse<RawResult> response) {
    return getFlowNodesAggregation(response)
        .map(NestedAggregate::aggregations)
        .map(aggs -> aggs.get(FILTERED_FLOW_NODES_AGGREGATION).filter());
  }

  protected Optional<NestedAggregate> getFlowNodesAggregation(
      final SearchResponse<RawResult> response) {
    return Optional.ofNullable(response.aggregations())
        .filter(aggs -> !aggs.isEmpty())
        .flatMap(aggs -> Optional.ofNullable(aggs.get(FLOW_NODES_AGGREGATION).nested()));
  }
}
