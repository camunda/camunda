/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.IncidentDbQuery;
import io.camunda.db.rdbms.sql.IncidentMapper;
import io.camunda.db.rdbms.sql.columns.IncidentSearchColumn;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.SearchQueryResult;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncidentReader extends AbstractEntityReader<IncidentEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(IncidentReader.class);

  private final IncidentMapper incidentMapper;

  public IncidentReader(final IncidentMapper incidentMapper) {
    super(IncidentSearchColumn::findByProperty);
    this.incidentMapper = incidentMapper;
  }

  public Optional<IncidentEntity> findOne(final long key) {
    final var result = search(IncidentQuery.of(b -> b.filter(f -> f.incidentKeys(key))));
    return Optional.ofNullable(result.items()).flatMap(it -> it.stream().findFirst());
  }

  public SearchQueryResult<IncidentEntity> search(final IncidentQuery query) {
    final var dbSort = convertSort(query.sort(), IncidentSearchColumn.INCIDENT_KEY);
    final var dbQuery =
        IncidentDbQuery.of(
            b -> b.filter(query.filter()).sort(dbSort).page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for incident with filter {}", dbQuery);
    final var totalHits = incidentMapper.count(dbQuery);
    final var hits = incidentMapper.search(dbQuery);
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }
}
