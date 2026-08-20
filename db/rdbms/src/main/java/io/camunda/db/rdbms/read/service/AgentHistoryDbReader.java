/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.AgentHistoryDbQuery;
import io.camunda.db.rdbms.read.mapper.AgentHistoryEntityMapper;
import io.camunda.db.rdbms.sql.AgentHistoryMapper;
import io.camunda.db.rdbms.sql.columns.AgentHistorySearchColumn;
import io.camunda.search.clients.reader.AgentHistoryReader;
import io.camunda.search.entities.AgentInstanceHistoryEntity;
import io.camunda.search.query.AgentInstanceHistoryQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.core.authz.ResourceAccessChecks;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentHistoryDbReader extends AbstractEntityReader<AgentInstanceHistoryEntity>
    implements AgentHistoryReader {

  private static final Logger LOG = LoggerFactory.getLogger(AgentHistoryDbReader.class);

  private final AgentHistoryMapper agentHistoryMapper;

  public AgentHistoryDbReader(
      final AgentHistoryMapper agentHistoryMapper, final RdbmsReaderConfig readerConfig) {
    super(AgentHistorySearchColumn.values(), readerConfig);
    this.agentHistoryMapper = agentHistoryMapper;
  }

  @Override
  public SearchQueryResult<AgentInstanceHistoryEntity> search(
      final AgentInstanceHistoryQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort = convertSort(query.sort(), AgentHistorySearchColumn.AGENT_HISTORY_KEY);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.PROCESS_DEFINITION.name(), List.of());
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        AgentHistoryDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for agent history items with filter {}", dbQuery);
    return executePagedQuery(
        () -> agentHistoryMapper.count(dbQuery),
        () ->
            agentHistoryMapper.search(dbQuery).stream()
                .map(AgentHistoryEntityMapper::toEntity)
                .toList(),
        dbPage,
        dbSort);
  }
}
