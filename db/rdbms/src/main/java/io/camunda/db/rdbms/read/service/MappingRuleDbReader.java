/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.MappingRuleDbQuery;
import io.camunda.db.rdbms.sql.MappingRuleMapper;
import io.camunda.db.rdbms.sql.columns.MappingRuleSearchColumn;
import io.camunda.search.clients.reader.MappingRuleReader;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.filter.MappingRuleFilter;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MappingRuleDbReader extends AbstractEntityReader<MappingRuleEntity>
    implements MappingRuleReader {

  private static final Logger LOG = LoggerFactory.getLogger(MappingRuleDbReader.class);

  private final MappingRuleMapper mappingRuleMapper;

  public MappingRuleDbReader(
      final MappingRuleMapper mappingRuleMapper, final RdbmsReaderConfig readerConfig) {
    super(MappingRuleSearchColumn.values(), readerConfig);
    this.mappingRuleMapper = mappingRuleMapper;
  }

  @Override
  public MappingRuleEntity getById(
      final String id, final ResourceAccessChecks resourceAccessChecks) {
    return findOne(id).orElse(null);
  }

  @Override
  public SearchQueryResult<MappingRuleEntity> search(
      final MappingRuleQuery query, final ResourceAccessChecks resourceAccessChecks) {

    if (shouldReturnEmptyResult(query.filter(), resourceAccessChecks)) {
      return new SearchQueryResult.Builder<MappingRuleEntity>().total(0).items(List.of()).build();
    }

    final var dbSort = convertSort(query.sort(), MappingRuleSearchColumn.MAPPING_RULE_ID);

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.MAPPING_RULE.name(), List.of());
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        MappingRuleDbQuery.of(
            b ->
                b.filter(query.filter())
                    .sort(dbSort)
                    .authorizedResourceIds(authorizedResourceIds)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for mapping rule with filter {}", dbQuery);
    final var totalHits = mappingRuleMapper.count(dbQuery);

    if (shouldReturnEmptyPage(dbPage, totalHits)) {
      return buildSearchQueryResult(totalHits, List.of(), dbSort);
    }

    final var hits = mappingRuleMapper.search(dbQuery);
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public Optional<MappingRuleEntity> findOne(final String mappingRuleId) {
    LOG.trace("[RDBMS DB] Search for mapping rule with mapping rule ID {}", mappingRuleId);
    final SearchQueryResult<MappingRuleEntity> queryResult =
        search(MappingRuleQuery.of(b -> b.filter(f -> f.mappingRuleId(mappingRuleId))));
    return Optional.ofNullable(queryResult.items()).flatMap(hits -> hits.stream().findFirst());
  }

  public SearchQueryResult<MappingRuleEntity> search(final MappingRuleQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }

  /**
   * Checks if the search result should be empty based on resource and tenant authorization.
   * Returns {@code true} if authorization is enabled but no authorized resource IDs are present.
   *
   * @param resourceAccessChecks the resource access checks containing authorization and tenant checks
   * @return {@code true} if the search result should be empty, {@code false
   */
  private boolean shouldReturnEmptyResult(
      final MappingRuleFilter filter, final ResourceAccessChecks resourceAccessChecks) {
    return (filter.mappingRuleIds() != null && filter.mappingRuleIds().isEmpty())
        || shouldReturnEmptyResult(resourceAccessChecks);
  }
}
