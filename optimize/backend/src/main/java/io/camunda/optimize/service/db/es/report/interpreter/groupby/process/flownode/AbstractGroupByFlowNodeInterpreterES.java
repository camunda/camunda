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

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation.Builder.ContainerBuilder;
import co.elastic.clients.elasticsearch._types.aggregations.FilterAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.NestedAggregate;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.interpreter.groupby.process.AbstractProcessGroupByInterpreterES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class AbstractGroupByFlowNodeInterpreterES
    extends AbstractProcessGroupByInterpreterES {
  private static final String FLOW_NODES_AGGREGATION = "flowNodes";
  private static final String FILTERED_FLOW_NODES_AGGREGATION = "filteredFlowNodes";

  protected abstract DefinitionService getDefinitionService();

  protected Map<String, ContainerBuilder> createFilteredFlowNodeAggregation(
      final ExecutionContext<ProcessReportDataDto, ?> context,
      final Map<String, Aggregation.Builder.ContainerBuilder> subAggregations) {
    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder()
            .nested(n -> n.path(FLOW_NODE_INSTANCES))
            .aggregations(
                FILTERED_FLOW_NODES_AGGREGATION,
                Aggregation.of(
                    a ->
                        a.filter(
                                f ->
                                    f.bool(
                                        createModelElementAggregationFilter(
                                                context.getReportData(),
                                                context.getFilterContext(),
                                                getDefinitionService())
                                            .build()))
                            .aggregations(
                                subAggregations.entrySet().stream()
                                    .collect(
                                        Collectors.toMap(
                                            Map.Entry::getKey, e -> e.getValue().build())))));
    return Map.of(FLOW_NODES_AGGREGATION, builder);
  }

  protected Optional<FilterAggregate> getFilteredFlowNodesAggregation(
      final ResponseBody<?> response) {
    return getFlowNodesAggregation(response)
        .map(NestedAggregate::aggregations)
        .map(aggs -> aggs.get(FILTERED_FLOW_NODES_AGGREGATION).filter());
  }

  protected Optional<NestedAggregate> getFlowNodesAggregation(final ResponseBody<?> response) {
    return Optional.ofNullable(response.aggregations())
        .filter(aggs -> !aggs.isEmpty())
        .map(aggs -> aggs.get(FLOW_NODES_AGGREGATION).nested());
  }
}
