/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.ResourceDbQuery;
import io.camunda.db.rdbms.sql.ResourceMapper;
import io.camunda.db.rdbms.sql.columns.ResourceSearchColumn;
import io.camunda.search.clients.reader.ResourceReader;
import io.camunda.search.entities.ResourceEntity;
import io.camunda.search.query.ResourceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceDbReader extends AbstractEntityReader<ResourceEntity> implements ResourceReader {

  private static final Logger LOG = LoggerFactory.getLogger(ResourceDbReader.class);

  private final ResourceMapper resourceMapper;

  public ResourceDbReader(
      final ResourceMapper resourceMapper, final RdbmsReaderConfig readerConfig) {
    super(ResourceSearchColumn.values(), readerConfig);
    this.resourceMapper = resourceMapper;
  }

  @Override
  public ResourceEntity getByKey(final long key, final ResourceAccessChecks resourceAccessChecks) {
    return findOne(key).orElse(null);
  }

  @Override
  public SearchQueryResult<ResourceEntity> search(
      final ResourceQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort = convertSort(query.sort(), ResourceSearchColumn.RESOURCE_KEY);
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        ResourceDbQuery.of(b -> b.filter(query.filter()).sort(dbSort).page(dbPage));

    LOG.trace("[RDBMS DB] Search for resource with filter {}", dbQuery);
    return executePagedQuery(
        () -> resourceMapper.count(dbQuery), () -> resourceMapper.search(dbQuery), dbPage, dbSort);
  }

  public Optional<ResourceEntity> findOne(final Long resourceKey) {
    LOG.trace("[RDBMS DB] Search for resource with resource key {}", resourceKey);
    final SearchQueryResult<ResourceEntity> queryResult =
        search(ResourceQuery.of(b -> b.filter(f -> f.resourceKeys(resourceKey))));
    return Optional.ofNullable(queryResult.items()).flatMap(hits -> hits.stream().findFirst());
  }

  public SearchQueryResult<ResourceEntity> search(final ResourceQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }
}
