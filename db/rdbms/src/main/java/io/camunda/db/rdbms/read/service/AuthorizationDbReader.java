/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.AuthorizationDbQuery;
import io.camunda.db.rdbms.read.mapper.AuthorizationEntityMapper;
import io.camunda.db.rdbms.sql.AuthorizationMapper;
import io.camunda.db.rdbms.sql.columns.AuthorizationSearchColumn;
import io.camunda.db.rdbms.write.domain.AuthorizationDbModel;
import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthorizationDbReader extends AbstractEntityReader<AuthorizationEntity>
    implements AuthorizationReader {

  private static final Logger LOG = LoggerFactory.getLogger(AuthorizationDbReader.class);

  private final AuthorizationMapper authorizationMapper;

  public AuthorizationDbReader(
      final AuthorizationMapper authorizationMapper, final RdbmsReaderConfig readerConfig) {
    super(AuthorizationSearchColumn.values(), readerConfig);
    this.authorizationMapper = authorizationMapper;
  }

  public Optional<AuthorizationEntity> findOne(
      final String ownerId, final String ownerType, final String resourceType) {
    final var result =
        search(
            AuthorizationQuery.of(
                b ->
                    b.filter(
                        f -> f.ownerIds(ownerId).ownerType(ownerType).resourceType(resourceType))));
    return Optional.ofNullable(result.items()).flatMap(items -> items.stream().findFirst());
  }

  @Override
  public AuthorizationEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    return search(
            AuthorizationQuery.of(q -> q.filter(f -> f.authorizationKey(key)).singleResult()),
            ResourceAccessChecks.disabled())
        .items()
        .stream()
        .findFirst()
        .orElse(null);
  }

  @Override
  public SearchQueryResult<AuthorizationEntity> search(
      final AuthorizationQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort =
        convertSort(
            query.sort(),
            AuthorizationSearchColumn.OWNER_ID,
            AuthorizationSearchColumn.OWNER_TYPE,
            AuthorizationSearchColumn.RESOURCE_TYPE);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.AUTHORIZATION.name(), List.of());
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        AuthorizationDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .sort(dbSort)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for authorizations with filter {}", dbQuery);
    final var totalHits = authorizationMapper.count(dbQuery);

    if (shouldReturnEmptyPage(dbPage, totalHits)) {
      return buildSearchQueryResult(totalHits, List.of(), dbSort);
    }

    final var hits = authorizationMapper.search(dbQuery).stream().map(this::map).toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public SearchQueryResult<AuthorizationEntity> search(final AuthorizationQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }

  private AuthorizationEntity map(final AuthorizationDbModel model) {
    return AuthorizationEntityMapper.toEntity(model);
  }
}
