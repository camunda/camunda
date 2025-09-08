/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.sql.CorrelatedMessageMapper;
import io.camunda.db.rdbms.sql.columns.CorrelatedMessageSearchColumn;
import io.camunda.search.clients.reader.CorrelatedMessageReader;
import io.camunda.search.entities.CorrelatedMessageEntity;
import io.camunda.search.query.CorrelatedMessageQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.Collections;
import java.util.Optional;

public class CorrelatedMessageDbReader extends AbstractEntityReader<CorrelatedMessageEntity>
    implements CorrelatedMessageReader {

  public CorrelatedMessageDbReader(final CorrelatedMessageMapper mapper) {
    super(CorrelatedMessageSearchColumn.values());
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
    // TODO add RDBMS search capabilities
    return buildSearchQueryResult(0, Collections.emptyList(), dbSort);
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
