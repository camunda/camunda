/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.AuthorizationDbQuery;
import io.camunda.db.rdbms.sql.AuthorizationMapper;
import io.camunda.db.rdbms.sql.columns.AuthorizationSearchColumn;
import io.camunda.db.rdbms.write.domain.AuthorizationDbModel;
import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.HashSet;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthorizationDbReader extends AbstractEntityReader<AuthorizationEntity>
    implements AuthorizationReader {

  private static final Logger LOG = LoggerFactory.getLogger(AuthorizationDbReader.class);

  private final AuthorizationMapper authorizationMapper;

  public AuthorizationDbReader(final AuthorizationMapper authorizationMapper) {
    super(AuthorizationSearchColumn.values());
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
            resourceAccessChecks)
        .items()
        .getFirst();
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
    final var dbQuery =
        AuthorizationDbQuery.of(
            b -> b.filter(query.filter()).sort(dbSort).page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for authorizations with filter {}", dbQuery);
    final var totalHits = authorizationMapper.count(dbQuery);
    final var hits = authorizationMapper.search(dbQuery).stream().map(this::map).toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public SearchQueryResult<AuthorizationEntity> search(final AuthorizationQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }

  private AuthorizationEntity map(final AuthorizationDbModel model) {
    return new AuthorizationEntity(
        model.authorizationKey(),
        model.ownerId(),
        model.ownerType(),
        model.resourceType(),
        model.resourceMatcher(),
        model.resourceId(),
        new HashSet<>(model.permissionTypes()));
  }
}
