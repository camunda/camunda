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
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryResult;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthorizationReader extends AbstractEntityReader<AuthorizationEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(AuthorizationReader.class);

  private final AuthorizationMapper authorizationMapper;

  public AuthorizationReader(final AuthorizationMapper authorizationMapper) {
    super(AuthorizationSearchColumn::findByProperty);
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

  public SearchQueryResult<AuthorizationEntity> search(final AuthorizationQuery query) {
    final var dbSort =
        convertSort(
            query.sort(),
            AuthorizationSearchColumn.OWNER_KEY,
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

  private AuthorizationEntity map(final AuthorizationDbModel model) {
    return new AuthorizationEntity(
        model.ownerKey(),
        model.ownerType(),
        model.resourceType(),
        null,
        null,
        null);
  }
}
