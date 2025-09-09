/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.CorrelatedMessageDbQuery;
import io.camunda.db.rdbms.read.mapper.CorrelatedMessageEntityMapper;
import io.camunda.db.rdbms.sql.CorrelatedMessageMapper;
import io.camunda.db.rdbms.sql.columns.CorrelatedMessageSearchColumn;
import io.camunda.search.clients.reader.CorrelatedMessageReader;
import io.camunda.search.entities.CorrelatedMessageEntity;
import io.camunda.search.query.CorrelatedMessageQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CorrelatedMessageDbReader extends AbstractEntityReader<CorrelatedMessageEntity>
    implements CorrelatedMessageReader {

  private static final Logger LOG = LoggerFactory.getLogger(CorrelatedMessageDbReader.class);

  private final CorrelatedMessageMapper mapper;

  public CorrelatedMessageDbReader(final CorrelatedMessageMapper mapper) {
    super(CorrelatedMessageSearchColumn.values());
    this.mapper = mapper;
  }

  public SearchQueryResult<CorrelatedMessageEntity> search(final CorrelatedMessageQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }

  @Override
  public SearchQueryResult<CorrelatedMessageEntity> search(
      final CorrelatedMessageQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort =
        convertSort(
            query.sort(),
            CorrelatedMessageSearchColumn.MESSAGE_KEY,
            CorrelatedMessageSearchColumn.SUBSCRIPTION_KEY);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var dbQuery =
        CorrelatedMessageDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(resourceAccessChecks.getAuthorizedResourceIds())
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for correlated messages with filter {}", dbQuery);
    final var totalHits = mapper.count(dbQuery);
    final var hits =
        mapper.search(dbQuery).stream().map(CorrelatedMessageEntityMapper::toEntity).toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public Optional<CorrelatedMessageEntity> findOne(
      final long messageKey, final long subscriptionKey) {
    final var result =
        search(
            CorrelatedMessageQuery.of(
                b -> b.filter(f -> f.messageKeys(messageKey).subscriptionKeys(subscriptionKey))));
    return Optional.ofNullable(result.items()).flatMap(it -> it.stream().findFirst());
  }
}
