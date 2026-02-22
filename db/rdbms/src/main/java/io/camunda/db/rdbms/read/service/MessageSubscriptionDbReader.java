/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.MessageSubscriptionDbQuery;
import io.camunda.db.rdbms.read.mapper.MessageSubscriptionEntityMapper;
import io.camunda.db.rdbms.sql.MessageSubscriptionMapper;
import io.camunda.db.rdbms.sql.columns.MessageSubscriptionColumn;
import io.camunda.search.clients.reader.MessageSubscriptionReader;
import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageSubscriptionDbReader extends AbstractEntityReader<MessageSubscriptionEntity>
    implements MessageSubscriptionReader {

  private static final Logger LOG = LoggerFactory.getLogger(MessageSubscriptionDbReader.class);

  private final MessageSubscriptionMapper mapper;

  public MessageSubscriptionDbReader(
      final MessageSubscriptionMapper mapper, final RdbmsReaderConfig readerConfig) {
    super(MessageSubscriptionColumn.values(), readerConfig);
    this.mapper = mapper;
  }

  public SearchQueryResult<MessageSubscriptionEntity> search(final MessageSubscriptionQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }

  @Override
  public SearchQueryResult<MessageSubscriptionEntity> search(
      final MessageSubscriptionQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort =
        convertSort(query.sort(), MessageSubscriptionColumn.MESSAGE_SUBSCRIPTION_KEY);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.PROCESS_DEFINITION.name(), List.of());
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        MessageSubscriptionDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for message subscriptions with filter {}", dbQuery);
    final var totalHits = mapper.count(dbQuery);

    if (shouldReturnEmptyPage(dbPage, totalHits)) {
      return buildSearchQueryResult(totalHits, List.of(), dbSort);
    }

    final var hits =
        mapper.search(dbQuery).stream().map(MessageSubscriptionEntityMapper::toEntity).toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public Optional<MessageSubscriptionEntity> findOne(final long key) {
    final var result =
        search(MessageSubscriptionQuery.of(b -> b.filter(f -> f.messageSubscriptionKeys(key))));
    return Optional.ofNullable(result.items()).flatMap(it -> it.stream().findFirst());
  }
}
