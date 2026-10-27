/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.AgentDefinitionDbQuery;
import io.camunda.db.rdbms.read.mapper.AgentDefinitionEntityMapper;
import io.camunda.db.rdbms.sql.AgentDefinitionMapper;
import io.camunda.db.rdbms.sql.columns.AgentDefinitionSearchColumn;
import io.camunda.search.clients.reader.AgentDefinitionReader;
import io.camunda.search.entities.AgentDefinitionEntity;
import io.camunda.search.query.AgentDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.core.authz.ResourceAccessChecks;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentDefinitionDbReader extends AbstractEntityReader<AgentDefinitionEntity>
    implements AgentDefinitionReader {

  private static final Logger LOG = LoggerFactory.getLogger(AgentDefinitionDbReader.class);

  private final AgentDefinitionMapper agentDefinitionMapper;

  public AgentDefinitionDbReader(
      final AgentDefinitionMapper agentDefinitionMapper, final RdbmsReaderConfig readerConfig) {
    super(AgentDefinitionSearchColumn.values(), readerConfig);
    this.agentDefinitionMapper = agentDefinitionMapper;
  }

  @Override
  public @Nullable AgentDefinitionEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    return search(
            AgentDefinitionQuery.of(
                b -> b.filter(f -> f.agentDefinitionKeys(key)).page(p -> p.from(0).size(1))))
        .items()
        .stream()
        .findFirst()
        .orElse(null);
  }

  @Override
  public SearchQueryResult<AgentDefinitionEntity> search(
      final AgentDefinitionQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort = convertSort(query.sort(), AgentDefinitionSearchColumn.AGENT_DEFINITION_KEY);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }
    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.PROCESS_DEFINITION.name(), List.of());
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        AgentDefinitionDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for agent definitions with filter {}", dbQuery);
    return executePagedQuery(
        () -> agentDefinitionMapper.count(dbQuery),
        () ->
            agentDefinitionMapper.search(dbQuery).stream()
                .map(AgentDefinitionEntityMapper::toEntity)
                .toList(),
        dbPage,
        dbSort);
  }

  public SearchQueryResult<AgentDefinitionEntity> search(final AgentDefinitionQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }
}
