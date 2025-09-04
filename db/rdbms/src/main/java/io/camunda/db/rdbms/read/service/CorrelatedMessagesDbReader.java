/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.CorrelatedMessagesDbQuery;
import io.camunda.db.rdbms.read.mapper.CorrelatedMessagesEntityMapper;
import io.camunda.db.rdbms.sql.CorrelatedMessageMapper;
import io.camunda.db.rdbms.sql.columns.CorrelatedMessageSearchColumn;
import io.camunda.search.clients.reader.CorrelatedMessagesReader;
import io.camunda.search.entities.CorrelatedMessageEntity;
import io.camunda.search.query.CorrelatedMessagesQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CorrelatedMessagesDbReader extends AbstractEntityReader<CorrelatedMessageEntity>
    implements CorrelatedMessagesReader {

  private static final Logger LOG = LoggerFactory.getLogger(CorrelatedMessagesDbReader.class);

  private final CorrelatedMessageMapper mapper;

  public CorrelatedMessagesDbReader(final CorrelatedMessageMapper mapper) {
    super(CorrelatedMessageSearchColumn.values());
    this.mapper = mapper;
  }

  public SearchQueryResult<CorrelatedMessageEntity> search(final CorrelatedMessagesQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }

  @Override
  public SearchQueryResult<CorrelatedMessageEntity> search(
      final CorrelatedMessagesQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort =
        convertSort(query.sort(), CorrelatedMessageSearchColumn.MESSAGE_KEY);
    final var dbQuery =
        CorrelatedMessagesDbQuery.of(
            b -> b.filter(query.filter()).sort(dbSort).page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for correlated messages with filter {}", dbQuery);
    final var totalHits = mapper.count(dbQuery);
    final var hits =
        mapper.search(dbQuery).stream().map(CorrelatedMessagesEntityMapper::toEntity).toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public Optional<CorrelatedMessageEntity> findOne(final long key) {
    final var result =
        search(CorrelatedMessagesQuery.of(b -> b.filter(f -> f.messageKey(key))));
    return Optional.ofNullable(result.items()).flatMap(it -> it.stream().findFirst());
  }
}