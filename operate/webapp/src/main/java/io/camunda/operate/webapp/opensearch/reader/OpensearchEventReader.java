/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.sortOptions;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.withTenantCheck;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.reader.EventReader;
import io.camunda.webapps.schema.descriptors.operate.template.EventTemplate;
import io.camunda.webapps.schema.entities.event.EventEntity;
import org.opensearch.client.opensearch._types.SortOrder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchEventReader implements EventReader {

  private final EventTemplate eventTemplate;

  private final RichOpenSearchClient richOpenSearchClient;

  public OpensearchEventReader(
      final EventTemplate eventTemplate, final RichOpenSearchClient richOpenSearchClient) {
    this.eventTemplate = eventTemplate;
    this.richOpenSearchClient = richOpenSearchClient;
  }

  @Override
  public EventEntity getEventEntityByFlowNodeInstanceId(final String flowNodeInstanceId) {
    final var request =
        searchRequestBuilder(eventTemplate.getAlias())
            .query(withTenantCheck(term(EventTemplate.FLOW_NODE_INSTANCE_KEY, flowNodeInstanceId)))
            .sort(sortOptions(EventTemplate.ID, SortOrder.Asc));
    final var response = richOpenSearchClient.doc().search(request, EventEntity.class);
    if (response.hits().total().value() >= 1) {
      return response.hits().hits().get(0).source();
    }
    return null;
  }
}
