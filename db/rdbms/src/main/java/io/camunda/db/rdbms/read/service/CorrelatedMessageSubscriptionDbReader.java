/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.CorrelatedMessageSubscriptionDbQuery;
import io.camunda.db.rdbms.read.mapper.CorrelatedMessageSubscriptionEntityMapper;
import io.camunda.db.rdbms.sql.CorrelatedMessageSubscriptionMapper;
import io.camunda.db.rdbms.sql.columns.CorrelatedMessageSubscriptionSearchColumn;
import io.camunda.search.clients.reader.CorrelatedMessageSubscriptionReader;
import io.camunda.search.entities.CorrelatedMessageSubscriptionEntity;
import io.camunda.search.query.CorrelatedMessageSubscriptionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CorrelatedMessageSubscriptionDbReader
    extends AbstractEntityReader<CorrelatedMessageSubscriptionEntity>
    implements CorrelatedMessageSubscriptionReader {

  private static final Logger LOG =
      LoggerFactory.getLogger(CorrelatedMessageSubscriptionDbReader.class);

  private final CorrelatedMessageSubscriptionMapper mapper;

  public CorrelatedMessageSubscriptionDbReader(
      final CorrelatedMessageSubscriptionMapper mapper, final RdbmsReaderConfig readerConfig) {
    super(CorrelatedMessageSubscriptionSearchColumn.values(), readerConfig);
    this.mapper = mapper;
  }

  public SearchQueryResult<CorrelatedMessageSubscriptionEntity> search(
      final CorrelatedMessageSubscriptionQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }

  @Override
  public SearchQueryResult<CorrelatedMessageSubscriptionEntity> search(
      final CorrelatedMessageSubscriptionQuery query,
      final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort =
        convertSort(
            query.sort(),
            CorrelatedMessageSubscriptionSearchColumn.MESSAGE_KEY,
            CorrelatedMessageSubscriptionSearchColumn.SUBSCRIPTION_KEY);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.PROCESS_DEFINITION.name(), List.of());
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        CorrelatedMessageSubscriptionDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for correlated message subscriptions with filter {}", dbQuery);
    final var totalHits = mapper.count(dbQuery);

    if (shouldReturnEmptyPage(dbPage, totalHits)) {
      return buildSearchQueryResult(totalHits, List.of(), dbSort);
    }

    final var hits =
        mapper.search(dbQuery).stream()
            .map(CorrelatedMessageSubscriptionEntityMapper::toEntity)
            .toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public Optional<CorrelatedMessageSubscriptionEntity> findOne(
      final long messageKey, final long subscriptionKey) {
    final var result =
        search(
            CorrelatedMessageSubscriptionQuery.of(
                b -> b.filter(f -> f.messageKeys(messageKey).subscriptionKeys(subscriptionKey))));
    return Optional.ofNullable(result.items()).flatMap(it -> it.stream().findFirst());
  }
}
