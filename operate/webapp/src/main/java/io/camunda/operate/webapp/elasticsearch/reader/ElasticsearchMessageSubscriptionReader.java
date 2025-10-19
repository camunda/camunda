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
import io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionEntity;
import java.io.IOException;
import java.util.Optional;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticsearchCondition.class)
public class ElasticsearchMessageSubscriptionReader
    implements io.camunda.operate.webapp.reader.MessageSubscriptionReader {

  final MessageSubscriptionTemplate messageSubscriptionTemplate;

  private final TenantAwareElasticsearchClient tenantAwareClient;

  private final ObjectMapper objectMapper;

  public ElasticsearchMessageSubscriptionReader(
      final MessageSubscriptionTemplate messageSubscriptionTemplate,
      final TenantAwareElasticsearchClient tenantAwareClient,
      @Qualifier("operateObjectMapper") final ObjectMapper objectMapper) {
    this.messageSubscriptionTemplate = messageSubscriptionTemplate;
    this.tenantAwareClient = tenantAwareClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<MessageSubscriptionEntity> getMessageSubscriptionEntityByFlowNodeInstanceId(
      final String flowNodeInstanceId) {
    final QueryBuilder query =
        constantScoreQuery(
            termQuery(MessageSubscriptionTemplate.FLOW_NODE_INSTANCE_KEY, flowNodeInstanceId));
    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(messageSubscriptionTemplate)
            .source(new SearchSourceBuilder().query(query).sort(MessageSubscriptionTemplate.ID));
    try {
      final SearchResponse response = tenantAwareClient.search(request);
      final var hits = response.getHits();
      final var totalHits =
          (hits != null && hits.getTotalHits() != null) ? hits.getTotalHits().value : 0L;
      if (totalHits >= 1) {
        // take last message subscription
        final MessageSubscriptionEntity messageSubscriptionEntity =
            fromSearchHit(
                hits.getHits()[(int) (totalHits - 1)].getSourceAsString(),
                objectMapper,
                MessageSubscriptionEntity.class);
        return Optional.of(messageSubscriptionEntity);
      } else {
        return Optional.empty();
      }
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining metadata for flow node instance: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }
}
