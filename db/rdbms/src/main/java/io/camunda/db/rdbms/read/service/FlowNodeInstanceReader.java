/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.FlowNodeInstanceDbQuery;
import io.camunda.db.rdbms.sql.FlowNodeInstanceMapper;
import io.camunda.db.rdbms.sql.columns.FlowNodeInstanceSearchColumn;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowNodeInstanceReader extends AbstractEntityReader<FlowNodeInstanceEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(FlowNodeInstanceReader.class);

  private final FlowNodeInstanceMapper flowNodeInstanceMapper;

  public FlowNodeInstanceReader(final FlowNodeInstanceMapper flowNodeInstanceMapper) {
    super(FlowNodeInstanceSearchColumn::findByProperty);
    this.flowNodeInstanceMapper = flowNodeInstanceMapper;
  }

  public Optional<FlowNodeInstanceEntity> findOne(final long key) {
    final var result =
        search(FlowNodeInstanceQuery.of(b -> b.filter(f -> f.flowNodeInstanceKeys(key))));
    return Optional.ofNullable(result.items()).flatMap(it -> it.stream().findFirst());
  }

  public SearchQueryResult<FlowNodeInstanceEntity> search(final FlowNodeInstanceQuery query) {
    final var dbSort =
        convertSort(query.sort(), FlowNodeInstanceSearchColumn.FLOW_NODE_INSTANCE_KEY);
    final var dbQuery =
        FlowNodeInstanceDbQuery.of(
            b -> b.filter(query.filter()).sort(dbSort).page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for process instance with filter {}", dbQuery);
    final var totalHits = flowNodeInstanceMapper.count(dbQuery);
    final var hits = flowNodeInstanceMapper.search(dbQuery);
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }
}
