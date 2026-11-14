/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_DEFINITION_INDEX_NAME;

import co.elastic.clients.elasticsearch._types.FieldValue;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.reader.AgentElementReader;
import io.camunda.optimize.service.db.schema.index.AgentDefinitionIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class AgentElementReaderES implements AgentElementReader {

  private static final String FLOW_NODE_IDS = "flowNodeIds";
  private static final int MAX_AGENT_ELEMENTS = 10_000;

  private final OptimizeElasticsearchClient esClient;

  public AgentElementReaderES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @Override
  public List<String> getAgentFlowNodeIds(final Collection<String> processDefinitionKeys) {
    if (processDefinitionKeys.isEmpty()) {
      return List.of();
    }
    try {
      final var response =
          esClient.search(
              OptimizeSearchRequestBuilderES.of(
                  b ->
                      b.optimizeIndex(esClient, AGENT_DEFINITION_INDEX_NAME)
                          .size(0)
                          .query(
                              q ->
                                  q.terms(
                                      t ->
                                          t.field(AgentDefinitionIndex.PROCESS_DEFINITION_KEY)
                                              .terms(
                                                  v ->
                                                      v.value(
                                                          processDefinitionKeys.stream()
                                                              .map(FieldValue::of)
                                                              .toList()))))
                          .aggregations(
                              FLOW_NODE_IDS,
                              a ->
                                  a.terms(
                                      t ->
                                          t.field(AgentDefinitionIndex.FLOW_NODE_ID)
                                              .size(MAX_AGENT_ELEMENTS)))),
              Object.class);
      return response.aggregations().get(FLOW_NODE_IDS).sterms().buckets().array().stream()
          .map(bucket -> bucket.key().stringValue())
          .toList();
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not read the agent elements of processes", e);
    }
  }
}
