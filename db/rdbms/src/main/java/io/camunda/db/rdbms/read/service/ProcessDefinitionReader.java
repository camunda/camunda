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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ProcessDefinitionReader {

  private final ProcessDefinitionMapper processDefinitionMapper;
  private final ConcurrentHashMap<Long, ProcessDefinitionDbModel> cache = new ConcurrentHashMap<>();

  public ProcessDefinitionReader(final ProcessDefinitionMapper processDefinitionMapper) {
    this.processDefinitionMapper = processDefinitionMapper;
  }

  public Optional<ProcessDefinitionDbModel> findOne(final long processDefinitionKey) {
    if (!cache.containsKey(processDefinitionKey)) {
      final var result = processDefinitionMapper.findOne(processDefinitionKey);

      if (result != null) {
        cache.put(processDefinitionKey, result);
        return Optional.of(result);
      }
    }

    return Optional.ofNullable(cache.get(processDefinitionKey));
  }
}
