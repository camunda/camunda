/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.ProcessDefinitionMessageSubscriptionStatisticsDbQuery;
import io.camunda.db.rdbms.sql.MessageSubscriptionMapper;
import io.camunda.db.rdbms.sql.columns.ProcessDefinitionMessageSubscriptionStatisticsColumn;
import io.camunda.search.clients.reader.ProcessDefinitionMessageSubscriptionStatisticsReader;
import io.camunda.search.entities.ProcessDefinitionMessageSubscriptionStatisticsEntity;
import io.camunda.search.query.ProcessDefinitionMessageSubscriptionStatisticsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.ProcessDefinitionMessageSubscriptionStatisticsSort;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessDefinitionMessageSubscriptionStatisticsDbReader
    extends AbstractEntityReader<ProcessDefinitionMessageSubscriptionStatisticsEntity>
    implements ProcessDefinitionMessageSubscriptionStatisticsReader {

  // Fixed sort: processDefinitionKey asc, tenantId asc
  // we don't allow sorting by other fields for this statistics type
  private static final ProcessDefinitionMessageSubscriptionStatisticsSort FIXED_SORT =
      ProcessDefinitionMessageSubscriptionStatisticsSort.of(
          b -> b.processDefinitionKey().asc().tenantId().asc());
  private static final Logger LOG =
      LoggerFactory.getLogger(ProcessDefinitionMessageSubscriptionStatisticsDbReader.class);
  private final MessageSubscriptionMapper mapper;

  public ProcessDefinitionMessageSubscriptionStatisticsDbReader(
      final MessageSubscriptionMapper mapper, final RdbmsReaderConfig readerConfig) {
    super(ProcessDefinitionMessageSubscriptionStatisticsColumn.values(), readerConfig);
    this.mapper = mapper;
  }

  @Override
  public SearchQueryResult<ProcessDefinitionMessageSubscriptionStatisticsEntity> aggregate(
      final ProcessDefinitionMessageSubscriptionStatisticsQuery query,
      final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort = convertSort(FIXED_SORT);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return SearchQueryResult.empty();
    }

    final var dbPage = convertPaging(dbSort, query.page());
    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.PROCESS_DEFINITION.name(), List.of());
    final var dbQuery =
        ProcessDefinitionMessageSubscriptionStatisticsDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .page(dbPage));

    LOG.trace(
        "[RDBMS DB] Aggregating message subscription process definition statistics with query {}",
        dbQuery);

    final var results = mapper.getProcessDefinitionStatistics(dbQuery);

    return buildSearchQueryResult(results.size(), results, convertSort(FIXED_SORT));
  }
}
