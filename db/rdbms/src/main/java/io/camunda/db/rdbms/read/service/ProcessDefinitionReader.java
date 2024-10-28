/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.ProcessDefinitionDbQuery;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.search.entities.ProcessDefinitionEntity;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessDefinitionReader {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessDefinitionReader.class);

  private final ProcessDefinitionMapper processDefinitionMapper;

  public ProcessDefinitionReader(final ProcessDefinitionMapper processDefinitionMapper) {
    this.processDefinitionMapper = processDefinitionMapper;
  }

  public Optional<ProcessDefinitionEntity> findOne(final long processDefinitionKey) {
    final var result =
        search(
            ProcessDefinitionDbQuery.of(
                b -> b.filter(f -> f.processDefinitionKeys(processDefinitionKey))));
    if (result.hits == null || result.hits.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(result.hits.getFirst());
    }
  }

  public SearchResult search(final ProcessDefinitionDbQuery filter) {
    LOG.trace("[RDBMS DB] Search for process instance with filter {}", filter);
    final var totalHits = processDefinitionMapper.count(filter);
    final var hits = processDefinitionMapper.search(filter);
    return new SearchResult(hits, totalHits);
  }

  public record SearchResult(List<ProcessDefinitionEntity> hits, Long total) {}
}
