/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ProcessDefinitionReader {

  private final ProcessDefinitionMapper processDefinitionMapper;
  private final HashMap<CacheKey, ProcessDefinitionDbModel> cache = new HashMap<>();

  public ProcessDefinitionReader(final ProcessDefinitionMapper processDefinitionMapper) {
    this.processDefinitionMapper = processDefinitionMapper;
  }

  public Optional<ProcessDefinitionDbModel> findOne(
      final Long processDefinitionKey, final long version) {
    final var cacheKey = new CacheKey(processDefinitionKey, version);
    if (!cache.containsKey(cacheKey)) {
      final var result =
          processDefinitionMapper.findOne(
              Map.of("processDefinitionKey", processDefinitionKey, "version", version));

      if (result != null) {
        cache.put(cacheKey, result);
        return Optional.of(result);
      }
    }

    return Optional.ofNullable(cache.get(cacheKey));
  }

  private record CacheKey(Long processDefinitionKey, Long version) {}
}
