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
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowNodeInstanceReader {

  private static final Logger LOG = LoggerFactory.getLogger(FlowNodeInstanceReader.class);

  private final FlowNodeInstanceMapper flowNodeInstanceMapper;

  public FlowNodeInstanceReader(final FlowNodeInstanceMapper flowNodeInstanceMapper) {
    this.flowNodeInstanceMapper = flowNodeInstanceMapper;
  }

  public Optional<FlowNodeInstanceEntity> findOne(final long processInstanceKey) {
    LOG.trace(
        "[RDBMS DB] Search for process instance with flowNodeInstanceKey {}", processInstanceKey);

    final var searchResult =
        search(
            FlowNodeInstanceDbQuery.of(
                b ->
                    b.filter(
                        FlowNodeInstanceFilter.of(
                            f -> f.flowNodeInstanceKeys(processInstanceKey)))));
    if (searchResult.hits == null || searchResult.hits.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(searchResult.hits.getFirst());
    }
  }

  public SearchResult search(final FlowNodeInstanceDbQuery filter) {
    LOG.trace("[RDBMS DB] Search for process instance with filter {}", filter);
    final var totalHits = flowNodeInstanceMapper.count(filter);
    final var hits = flowNodeInstanceMapper.search(filter);
    return new SearchResult(hits, totalHits.intValue());
  }

  public record SearchResult(List<FlowNodeInstanceEntity> hits, Integer total) {

  }
}
