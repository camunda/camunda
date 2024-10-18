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
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowNodeInstanceReader {

  private static final Logger LOG = LoggerFactory.getLogger(FlowNodeInstanceReader.class);

  private final FlowNodeInstanceMapper flowNodeInstanceMapper;

  public FlowNodeInstanceReader(final FlowNodeInstanceMapper flowNodeInstanceMapper) {
    this.flowNodeInstanceMapper = flowNodeInstanceMapper;
  }

  public FlowNodeInstanceEntity findOne(final long processInstanceKey) {
    LOG.trace("[RDBMS DB] Search for process instance with flowNodeInstanceKey {}",
        processInstanceKey);
    return flowNodeInstanceMapper.findOne(processInstanceKey);
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
