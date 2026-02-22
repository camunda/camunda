/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.DecisionRequirementsDbQuery;
import io.camunda.db.rdbms.sql.DecisionRequirementsMapper;
import io.camunda.db.rdbms.sql.columns.DecisionRequirementsSearchColumn;
import io.camunda.search.clients.reader.DecisionRequirementsReader;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionRequirementsDbReader extends AbstractEntityReader<DecisionRequirementsEntity>
    implements DecisionRequirementsReader {

  private static final Logger LOG = LoggerFactory.getLogger(DecisionRequirementsDbReader.class);

  private final DecisionRequirementsMapper decisionRequirementsMapper;

  public DecisionRequirementsDbReader(
      final DecisionRequirementsMapper decisionRequirementsMapper,
      final RdbmsReaderConfig readerConfig) {
    super(DecisionRequirementsSearchColumn.values(), readerConfig);
    this.decisionRequirementsMapper = decisionRequirementsMapper;
  }

  @Override
  public DecisionRequirementsEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    return getByKey(key, resourceAccessChecks, false);
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> search(
      final DecisionRequirementsQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort =
        convertSort(query.sort(), DecisionRequirementsSearchColumn.DECISION_REQUIREMENTS_KEY);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(
                AuthorizationResourceType.DECISION_REQUIREMENTS_DEFINITION.name(), List.of());
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        DecisionRequirementsDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(dbPage)
                    .resultConfig(query.resultConfig()));

    LOG.trace("[RDBMS DB] Search for decision requirements with filter {}", dbQuery);
    final var totalHits = decisionRequirementsMapper.count(dbQuery);

    if (shouldReturnEmptyPage(dbPage, totalHits)) {
      return buildSearchQueryResult(totalHits, List.of(), dbSort);
    }

    final var hits = decisionRequirementsMapper.search(dbQuery);
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public Optional<DecisionRequirementsEntity> findOne(final long decisionRequirementsKey) {
    final var result =
        search(
            DecisionRequirementsQuery.of(
                b ->
                    b.filter(f -> f.decisionRequirementsKeys(decisionRequirementsKey))
                        .resultConfig(c -> c.includeXml(true))));
    return Optional.ofNullable(result.items()).flatMap(it -> it.stream().findFirst());
  }

  public SearchQueryResult<DecisionRequirementsEntity> search(
      final DecisionRequirementsQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }

  @Override
  public DecisionRequirementsEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks, final boolean includeXml) {
    return findOne(key).orElse(null);
  }
}
