/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.MappingDbQuery;
import io.camunda.db.rdbms.sql.MappingMapper;
import io.camunda.db.rdbms.sql.columns.MappingSearchColumn;
import io.camunda.search.entities.MappingEntity;
import io.camunda.search.query.MappingQuery;
import io.camunda.search.query.SearchQueryResult;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MappingReader extends AbstractEntityReader<MappingEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(MappingReader.class);

  private final MappingMapper mappingMapper;

  public MappingReader(final MappingMapper mappingMapper) {
    super(MappingSearchColumn::findByProperty);
    this.mappingMapper = mappingMapper;
  }

  public Optional<MappingEntity> findOne(final String mappingId) {
    LOG.trace("[RDBMS DB] Search for mapping with mapping ID {}", mappingId);
    final SearchQueryResult<MappingEntity> queryResult =
        search(MappingQuery.of(b -> b.filter(f -> f.id(mappingId))));
    return Optional.ofNullable(queryResult.items()).flatMap(hits -> hits.stream().findFirst());
  }

  public SearchQueryResult<MappingEntity> search(final MappingQuery query) {
    final var dbSort = convertSort(query.sort(), MappingSearchColumn.ID);
    final var dbQuery =
        MappingDbQuery.of(
            b -> b.filter(query.filter()).sort(dbSort).page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for mapping with filter {}", dbQuery);
    final var totalHits = mappingMapper.count(dbQuery);
    final var hits = mappingMapper.search(dbQuery);
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }
}
