/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.AgentInstanceDbQuery;
import io.camunda.db.rdbms.read.mapper.AgentInstanceEntityMapper;
import io.camunda.db.rdbms.sql.AgentInstanceMapper;
import io.camunda.db.rdbms.sql.columns.AgentInstanceSearchColumn;
import io.camunda.search.clients.reader.AgentInstanceReader;
import io.camunda.search.entities.AgentInstanceEntity;
import io.camunda.search.query.AgentInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentInstanceDbReader extends AbstractEntityReader<AgentInstanceEntity>
    implements AgentInstanceReader {

  private static final Logger LOG = LoggerFactory.getLogger(AgentInstanceDbReader.class);

  private final AgentInstanceMapper agentInstanceMapper;

  public AgentInstanceDbReader(
      final AgentInstanceMapper agentInstanceMapper, final RdbmsReaderConfig readerConfig) {
    super(AgentInstanceSearchColumn.values(), readerConfig);
    this.agentInstanceMapper = agentInstanceMapper;
  }

  @Override
  public AgentInstanceEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    return findOne(key);
  }

  public AgentInstanceEntity findOne(final long key) {
    return search(
            AgentInstanceQuery.of(
                b -> b.filter(f -> f.agentInstanceKeys(key)).page(p -> p.from(0).size(1))))
        .items()
        .stream()
        .findFirst()
        .orElse(null);
  }

  @Override
  public SearchQueryResult<AgentInstanceEntity> search(
      final AgentInstanceQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort = convertSort(query.sort(), AgentInstanceSearchColumn.AGENT_INSTANCE_KEY);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }
    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.PROCESS_DEFINITION.name(), List.of());
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        AgentInstanceDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for agent instances with filter {}", dbQuery);
    return executePagedQuery(
        () -> agentInstanceMapper.count(dbQuery),
        () ->
            agentInstanceMapper.search(dbQuery).stream()
                .map(AgentInstanceEntityMapper::toEntity)
                .toList(),
        dbPage,
        dbSort);
  }

  public SearchQueryResult<AgentInstanceEntity> search(final AgentInstanceQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }
}
