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
import io.camunda.operate.webapp.reader.MessageSubscriptionReader;
import io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionEntity;
import java.util.Optional;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchMessageSubscriptionReader implements MessageSubscriptionReader {

  private final MessageSubscriptionTemplate messageSubscriptionTemplate;

  private final RichOpenSearchClient richOpenSearchClient;

  public OpensearchMessageSubscriptionReader(
      final MessageSubscriptionTemplate messageSubscriptionTemplate,
      final RichOpenSearchClient richOpenSearchClient) {
    this.messageSubscriptionTemplate = messageSubscriptionTemplate;
    this.richOpenSearchClient = richOpenSearchClient;
  }

  @Override
  public Optional<MessageSubscriptionEntity> getMessageSubscriptionEntityByFlowNodeInstanceId(
      final String flowNodeInstanceId) {
    final var request =
        searchRequestBuilder(messageSubscriptionTemplate.getAlias())
            .query(
                withTenantCheck(
                    term(MessageSubscriptionTemplate.FLOW_NODE_INSTANCE_KEY, flowNodeInstanceId)))
            .sort(sortOptions(MessageSubscriptionTemplate.ID, SortOrder.Asc));
    final var response =
        richOpenSearchClient.doc().search(request, MessageSubscriptionEntity.class);
    final var total = response.hits().total();
    final var totalHits = (total != null) ? total.value() : 0L;
    if (totalHits >= 1) {
      return response.hits().hits().stream().findFirst().map(Hit::source);
    }
    return Optional.empty();
  }
}
