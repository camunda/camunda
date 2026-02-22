/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.DecisionDefinitionDbQuery;
import io.camunda.db.rdbms.sql.DecisionDefinitionMapper;
import io.camunda.db.rdbms.sql.columns.DecisionDefinitionSearchColumn;
import io.camunda.search.clients.reader.DecisionDefinitionReader;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionDefinitionDbReader extends AbstractEntityReader<DecisionDefinitionEntity>
    implements DecisionDefinitionReader {

  private static final Logger LOG = LoggerFactory.getLogger(DecisionDefinitionDbReader.class);

  private final DecisionDefinitionMapper decisionDefinitionMapper;

  public DecisionDefinitionDbReader(
      final DecisionDefinitionMapper decisionDefinitionMapper,
      final RdbmsReaderConfig readerConfig) {
    super(DecisionDefinitionSearchColumn.values(), readerConfig);
    this.decisionDefinitionMapper = decisionDefinitionMapper;
  }

  @Override
  public DecisionDefinitionEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    return findOne(key).orElse(null);
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> search(
      final DecisionDefinitionQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort =
        convertSort(query.sort(), DecisionDefinitionSearchColumn.DECISION_DEFINITION_KEY);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.DECISION_DEFINITION.name(), List.of());
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        DecisionDefinitionDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for decision definition with filter {}", dbQuery);
    final var totalHits = decisionDefinitionMapper.count(dbQuery);

    if (shouldReturnEmptyPage(dbPage, totalHits)) {
      return buildSearchQueryResult(totalHits, List.of(), dbSort);
    }

    final var hits = decisionDefinitionMapper.search(dbQuery);
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public Optional<DecisionDefinitionEntity> findOne(final long decisionDefinitionKey) {
    final var result =
        search(
            DecisionDefinitionQuery.of(
                b -> b.filter(f -> f.decisionDefinitionKeys(decisionDefinitionKey))));
    return Optional.ofNullable(result.items()).flatMap(it -> it.stream().findFirst());
  }

  public SearchQueryResult<DecisionDefinitionEntity> search(final DecisionDefinitionQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }
}
