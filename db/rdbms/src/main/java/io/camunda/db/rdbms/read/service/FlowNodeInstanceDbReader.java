/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.FlowNodeInstanceDbQuery;
import io.camunda.db.rdbms.read.mapper.FlowNodeInstanceEntityMapper;
import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper;
import io.camunda.db.rdbms.sql.columns.FlowNodeInstanceSearchColumn;
import io.camunda.search.clients.reader.FlowNodeInstanceReader;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowNodeInstanceDbReader extends AbstractEntityReader<FlowNodeInstanceEntity>
    implements FlowNodeInstanceReader {

  private static final Logger LOG = LoggerFactory.getLogger(FlowNodeInstanceDbReader.class);

  private final FlowNodeInstanceMapper flowNodeInstanceMapper;

  public FlowNodeInstanceDbReader(
      final FlowNodeInstanceMapper flowNodeInstanceMapper, final RdbmsReaderConfig readerConfig) {
    super(FlowNodeInstanceSearchColumn.values(), readerConfig);
    this.flowNodeInstanceMapper = flowNodeInstanceMapper;
  }

  @Override
  public FlowNodeInstanceEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    return findOne(key).orElse(null);
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> search(
      final FlowNodeInstanceQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort =
        convertSort(query.sort(), FlowNodeInstanceSearchColumn.FLOW_NODE_INSTANCE_KEY);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.PROCESS_DEFINITION.name(), List.of());
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        FlowNodeInstanceDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for process instance with filter {}", dbQuery);
    final var totalHits = flowNodeInstanceMapper.count(dbQuery);

    if (shouldReturnEmptyPage(dbPage, totalHits)) {
      return buildSearchQueryResult(totalHits, List.of(), dbSort);
    }

    final var hits =
        flowNodeInstanceMapper.search(dbQuery).stream()
            .map(FlowNodeInstanceEntityMapper::toEntity)
            .toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public Optional<FlowNodeInstanceEntity> findOne(final long key) {
    final var result =
        search(FlowNodeInstanceQuery.of(b -> b.filter(f -> f.flowNodeInstanceKeys(key))));
    return Optional.ofNullable(result.items()).flatMap(it -> it.stream().findFirst());
  }

  public SearchQueryResult<FlowNodeInstanceEntity> search(final FlowNodeInstanceQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }
}
