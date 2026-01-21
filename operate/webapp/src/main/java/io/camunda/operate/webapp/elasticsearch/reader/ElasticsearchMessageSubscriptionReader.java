/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.constantScoreQuery;
import static io.camunda.operate.util.ElasticsearchUtil.termsQuery;
import static io.camunda.operate.util.ElasticsearchUtil.whereToSearch;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionEntity;
import java.io.IOException;
import java.util.Optional;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticsearchCondition.class)
public class ElasticsearchMessageSubscriptionReader extends AbstractReader
    implements io.camunda.operate.webapp.reader.MessageSubscriptionReader {

  final MessageSubscriptionTemplate messageSubscriptionTemplate;

  public ElasticsearchMessageSubscriptionReader(
      final MessageSubscriptionTemplate messageSubscriptionTemplate) {
    this.messageSubscriptionTemplate = messageSubscriptionTemplate;
  }

  @Override
  public Optional<MessageSubscriptionEntity> getMessageSubscriptionEntityByFlowNodeInstanceId(
      final String flowNodeInstanceId) {
    final var query =
        constantScoreQuery(
            termsQuery(MessageSubscriptionTemplate.FLOW_NODE_INSTANCE_KEY, flowNodeInstanceId));
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var searchRequest =
        new SearchRequest.Builder()
            .index(whereToSearch(messageSubscriptionTemplate, ALL))
            .query(tenantAwareQuery)
            .sort(ElasticsearchUtil.sortOrder(MessageSubscriptionTemplate.ID, SortOrder.Asc))
            .build();

    try {
      final var response = esClient.search(searchRequest, MessageSubscriptionEntity.class);
      final var totalHits = response.hits().total().value();
      if (totalHits >= 1) {
        // take last message subscription
        final var messageSubscriptionEntity =
            response.hits().hits().get((int) (totalHits - 1)).source();
        return Optional.ofNullable(messageSubscriptionEntity);
      } else {
        return Optional.empty();
      }
    } catch (final IOException e) {
      final var message =
          String.format(
              "Exception occurred, while obtaining metadata for flow node instance: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }
}
