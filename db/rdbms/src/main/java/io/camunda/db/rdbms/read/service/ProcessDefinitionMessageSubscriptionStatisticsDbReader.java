/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.ProcessDefinitionMessageSubscriptionStatisticsDbQuery;
import io.camunda.db.rdbms.sql.MessageSubscriptionMapper;
import io.camunda.db.rdbms.sql.columns.MessageSubscriptionColumn;
import io.camunda.search.clients.reader.ProcessDefinitionMessageSubscriptionStatisticsReader;
import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.entities.ProcessDefinitionMessageSubscriptionStatisticsEntity;
import io.camunda.search.query.ProcessDefinitionMessageSubscriptionStatisticsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessDefinitionMessageSubscriptionStatisticsDbReader
    extends AbstractEntityReader<MessageSubscriptionEntity>
    implements ProcessDefinitionMessageSubscriptionStatisticsReader {

  private static final Logger LOG =
      LoggerFactory.getLogger(ProcessDefinitionMessageSubscriptionStatisticsDbReader.class);

  private final MessageSubscriptionMapper mapper;

  public ProcessDefinitionMessageSubscriptionStatisticsDbReader(
      final MessageSubscriptionMapper mapper) {
    super(MessageSubscriptionColumn.values());
    this.mapper = mapper;
  }

  @Override
  public SearchQueryResult<ProcessDefinitionMessageSubscriptionStatisticsEntity> aggregate(
      final ProcessDefinitionMessageSubscriptionStatisticsQuery query,
      final ResourceAccessChecks resourceAccessChecks) {

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return SearchQueryResult.empty();
    }

    final var dbQuery =
        ProcessDefinitionMessageSubscriptionStatisticsDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(resourceAccessChecks.getAuthorizedResourceIds())
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .page(query.page()));

    LOG.trace(
        "[RDBMS DB] Aggregating message subscription process definition statistics with query {}",
        dbQuery);

    final var results = mapper.getProcessDefinitionStatistics(dbQuery);

    // Calculate the endCursor for pagination
    // The cursor represents the offset for the next page
    String endCursor = null;
    if (query.page() != null && query.page().size() != null && !results.isEmpty()) {
      final int currentOffset =
          query.page().after() != null ? Integer.parseInt(query.page().after()) : 0;
      final int nextOffset = currentOffset + results.size();
      // Only set endCursor if we got a full page (indicating there might be more results)
      if (results.size() >= query.page().size()) {
        endCursor = String.valueOf(nextOffset);
      }
    }
    return new SearchQueryResult<>(results.size(), !results.isEmpty(), results, null, endCursor);
  }
}
