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
import io.camunda.search.entities.ProcessInstanceEntity;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessInstanceReader {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessInstanceReader.class);

  private final ProcessInstanceMapper processInstanceMapper;

  public ProcessInstanceReader(final ProcessInstanceMapper processInstanceMapper) {
    this.processInstanceMapper = processInstanceMapper;
  }

  public Optional<ProcessInstanceEntity> findOne(final long processInstanceKey) {
    LOG.trace("[RDBMS DB] Search for process instance with key {}", processInstanceKey);
    return Optional.ofNullable(processInstanceMapper.findOne(processInstanceKey));
  }

  public SearchResult search(final ProcessInstanceDbQuery filter) {
    LOG.trace("[RDBMS DB] Search for process instance with filter {}", filter);
    final var totalHits = processInstanceMapper.count(filter);
    final var hits = processInstanceMapper.search(filter);
    return new SearchResult(hits, totalHits.intValue());
  }

  public record SearchResult(List<ProcessInstanceEntity> hits, Integer total) {}
}
