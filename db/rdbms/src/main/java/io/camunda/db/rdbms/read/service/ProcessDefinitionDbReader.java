/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.ProcessDefinitionDbQuery;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.db.rdbms.sql.columns.ProcessDefinitionSearchColumn;
import io.camunda.search.clients.reader.ProcessDefinitionReader;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessDefinitionDbReader extends AbstractEntityReader<ProcessDefinitionEntity>
    implements ProcessDefinitionReader {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessDefinitionDbReader.class);

  private final ProcessDefinitionMapper processDefinitionMapper;

  public ProcessDefinitionDbReader(
      final ProcessDefinitionMapper processDefinitionMapper, final RdbmsReaderConfig readerConfig) {
    super(ProcessDefinitionSearchColumn.values(), readerConfig);
    this.processDefinitionMapper = processDefinitionMapper;
  }

  @Override
  public ProcessDefinitionEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    return findOne(key).orElse(null);
  }

  @Override
  public SearchQueryResult<ProcessDefinitionEntity> search(
      final ProcessDefinitionQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort =
        convertSort(query.sort(), ProcessDefinitionSearchColumn.PROCESS_DEFINITION_KEY);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.PROCESS_DEFINITION.name(), List.of());
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        ProcessDefinitionDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for process instance with filter {}", dbQuery);
    final var totalHits = processDefinitionMapper.count(dbQuery);

    if (shouldReturnEmptyPage(dbPage, totalHits)) {
      return buildSearchQueryResult(totalHits, List.of(), dbSort);
    }

    final var hits = processDefinitionMapper.search(dbQuery);
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public Optional<ProcessDefinitionEntity> findOne(final long processDefinitionKey) {
    final var result =
        search(
            ProcessDefinitionQuery.of(
                b -> b.filter(f -> f.processDefinitionKeys(processDefinitionKey))));
    if (result.items() == null || result.items().isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(result.items().getFirst());
    }
  }

  public SearchQueryResult<ProcessDefinitionEntity> search(final ProcessDefinitionQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }
}
