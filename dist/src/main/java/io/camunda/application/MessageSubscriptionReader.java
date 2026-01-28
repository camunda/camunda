/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.db.rdbms.write.domain.MessageSubscriptionDbModel;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionState;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionEntity;
import java.util.List;

/**
 * Reader that reads message subscriptions from Elasticsearch and converts them to RDBMS models.
 *
 * <p>This is part of the ES to RDBMS migration tooling (Tier 3).
 */
public final class MessageSubscriptionReader {

  private static final String INDEX_NAME = "operate-event-8.3.0_alias";

  private MessageSubscriptionReader() {}

  /**
   * Reads all message subscriptions from Elasticsearch.
   *
   * @param esClient the Elasticsearch client
   * @return list of MessageSubscriptionEntity objects from ES
   */
  public static List<MessageSubscriptionEntity> readAllMessageSubscriptionsFromEs(
      final ElasticsearchClient esClient) {
    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(INDEX_NAME)
            .query(ElasticsearchUtil.matchAllQuery())
            .size(ElasticsearchUtil.QUERY_MAX_SIZE);

    return ElasticsearchUtil.scrollAllStream(
            esClient, searchRequestBuilder, MessageSubscriptionEntity.class)
        .flatMap(response -> response.hits().hits().stream())
        .map(Hit::source)
        .toList();
  }

  /**
   * Converts a MessageSubscriptionEntity (ES model) to a MessageSubscriptionDbModel (RDBMS model).
   *
   * @param entity the MessageSubscriptionEntity from Elasticsearch
   * @return the corresponding MessageSubscriptionDbModel for RDBMS
   */
  public static MessageSubscriptionDbModel toRdbmsModel(final MessageSubscriptionEntity entity) {
    return new MessageSubscriptionDbModel.Builder()
        .messageSubscriptionKey(entity.getKey())
        .processDefinitionId(entity.getBpmnProcessId())
        .processDefinitionKey(entity.getProcessDefinitionKey())
        .processInstanceKey(entity.getProcessInstanceKey())
        .rootProcessInstanceKey(
            entity.getRootProcessInstanceKey() != null
                ? entity.getRootProcessInstanceKey()
                : entity.getProcessInstanceKey())
        .flowNodeId(entity.getFlowNodeId())
        .flowNodeInstanceKey(entity.getFlowNodeInstanceKey())
        .messageSubscriptionState(mapState(entity.getEventType()))
        .dateTime(entity.getDateTime())
        .messageName(entity.getMetadata() != null ? entity.getMetadata().getMessageName() : null)
        .correlationKey(
            entity.getMetadata() != null ? entity.getMetadata().getCorrelationKey() : null)
        .tenantId(entity.getTenantId())
        .partitionId(entity.getPartitionId())
        .build();
  }

  private static MessageSubscriptionState mapState(
      final io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionState
          esState) {
    if (esState == null) {
      return null;
    }
    return switch (esState) {
      case CREATED -> MessageSubscriptionState.CREATED;
      case CORRELATED -> MessageSubscriptionState.CORRELATED;
      case DELETED -> MessageSubscriptionState.DELETED;
      case MIGRATED -> MessageSubscriptionState.MIGRATED;
      default -> null;
    };
  }

  /**
   * Reads all message subscriptions from Elasticsearch and converts them to RDBMS models.
   *
   * @param esClient the Elasticsearch client
   * @return list of MessageSubscriptionDbModel objects ready for RDBMS insertion
   */
  public static List<MessageSubscriptionDbModel> readMessageSubscriptions(
      final ElasticsearchClient esClient) {
    return readAllMessageSubscriptionsFromEs(esClient).stream()
        .map(MessageSubscriptionReader::toRdbmsModel)
        .toList();
  }
}
