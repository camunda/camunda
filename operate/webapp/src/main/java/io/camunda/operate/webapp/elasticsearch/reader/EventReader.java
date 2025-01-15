/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.util.ElasticsearchUtil.fromSearchHit;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.webapps.schema.descriptors.template.EventTemplate;
import io.camunda.webapps.schema.entities.event.EventEntity;
import java.io.IOException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticsearchCondition.class)
public class EventReader implements io.camunda.operate.webapp.reader.EventReader {

  final EventTemplate eventTemplate;

  private final TenantAwareElasticsearchClient tenantAwareClient;

  private final ObjectMapper objectMapper;

  public EventReader(
      final EventTemplate eventTemplate,
      final TenantAwareElasticsearchClient tenantAwareClient,
      @Qualifier("operateObjectMapper") final ObjectMapper objectMapper) {
    this.eventTemplate = eventTemplate;
    this.tenantAwareClient = tenantAwareClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public EventEntity getEventEntityByFlowNodeInstanceId(final String flowNodeInstanceId) {
    final EventEntity eventEntity;
    // request corresponding event and build cumulative metadata
    final QueryBuilder query =
        constantScoreQuery(termQuery(EventTemplate.FLOW_NODE_INSTANCE_KEY, flowNodeInstanceId));
    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(eventTemplate)
            .source(new SearchSourceBuilder().query(query).sort(EventTemplate.ID));
    try {
      final SearchResponse response = tenantAwareClient.search(request);
      if (response.getHits().getTotalHits().value >= 1) {
        // take last event
        eventEntity =
            fromSearchHit(
                response
                    .getHits()
                    .getHits()[(int) (response.getHits().getTotalHits().value - 1)]
                    .getSourceAsString(),
                objectMapper,
                EventEntity.class);
      } else {
        throw new NotFoundException(
            String.format(
                "Could not find flow node instance event with id '%s'.", flowNodeInstanceId));
      }
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining metadata for flow node instance: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
    return eventEntity;
  }
}
