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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessDefinitionReader {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessDefinitionReader.class);

  private final ProcessDefinitionMapper processDefinitionMapper;
  private final ConcurrentHashMap<Long, ProcessDefinitionEntity> cache = new ConcurrentHashMap<>();

  public ProcessDefinitionReader(final ProcessDefinitionMapper processDefinitionMapper) {
    this.processDefinitionMapper = processDefinitionMapper;
  }

  public Optional<ProcessDefinitionEntity> findOne(final long processDefinitionKey) {
    if (!cache.containsKey(processDefinitionKey)) {
      final var processDefinition = processDefinitionMapper.findOne(processDefinitionKey);

      if (processDefinition != null) {
        final var result = cache.putIfAbsent(processDefinitionKey, processDefinition);
        return Optional.of(Objects.requireNonNullElse(result, processDefinition));
      }
    }

    return Optional.ofNullable(cache.get(processDefinitionKey));
  }

  public SearchResult search(final ProcessDefinitionDbQuery filter) {
    LOG.trace("[RDBMS DB] Search for process instance with filter {}", filter);
    final var totalHits = processDefinitionMapper.count(filter);
    final var hits = processDefinitionMapper.search(filter);
    return new SearchResult(hits, totalHits);
  }


  public record SearchResult(List<ProcessDefinitionEntity> hits, Integer total) {

  }
}
