/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.ProcessInstanceDbQuery;
import io.camunda.db.rdbms.sql.ProcessInstanceMapper;
import io.camunda.db.rdbms.sql.columns.ProcessInstanceSearchColumn;
import io.camunda.search.clients.reader.ProcessInstanceReader;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessInstanceDbReader extends AbstractEntityReader<ProcessInstanceEntity>
    implements ProcessInstanceReader {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessInstanceDbReader.class);

  private final ProcessInstanceMapper processInstanceMapper;

  public ProcessInstanceDbReader(final ProcessInstanceMapper processInstanceMapper) {
    super(ProcessInstanceSearchColumn.values());
    this.processInstanceMapper = processInstanceMapper;
  }

  public Optional<ProcessInstanceEntity> findOne(final long processInstanceKey) {
    LOG.trace("[RDBMS DB] Search for process instance with key {}", processInstanceKey);
    return Optional.ofNullable(processInstanceMapper.findOne(processInstanceKey));
  }

  public SearchQueryResult<ProcessInstanceEntity> search(final ProcessInstanceQuery query) {
    final var dbSort = convertSort(query.sort(), ProcessInstanceSearchColumn.PROCESS_INSTANCE_KEY);
    final var dbQuery =
        ProcessInstanceDbQuery.of(
            b -> b.filter(query.filter()).sort(dbSort).page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for process instance with filter {}", dbQuery);
    final var totalHits = processInstanceMapper.count(dbQuery);
    final var hits = processInstanceMapper.search(dbQuery);
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  @Override
  public ProcessInstanceEntity getByKey(
      final String key, final ResourceAccessChecks resourceAccessChecks) {
    return findOne(Long.parseLong(key)).orElse(null);
  }

  @Override
  public SearchQueryResult<ProcessInstanceEntity> search(
      final ProcessInstanceQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return search(query);
  }
}
