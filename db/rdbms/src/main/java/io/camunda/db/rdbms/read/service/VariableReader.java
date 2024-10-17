/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.VariableDbQuery;
import io.camunda.db.rdbms.read.service.ProcessInstanceReader.SearchResult;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.search.entities.VariableEntity;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VariableReader {

  private static final Logger LOG = LoggerFactory.getLogger(VariableReader.class);

  private final VariableMapper variableMapper;

  public VariableReader(final VariableMapper variableMapper) {
    this.variableMapper = variableMapper;
  }

  public VariableDbModel findOne(final Long key) {
    return variableMapper.findOne(key);
  }

  public SearchResult search(final VariableDbQuery filter) {
    LOG.trace("[RDBMS DB] Search for variables with filter {}", filter);
    final var totalHits = variableMapper.count(filter);
    final var hits = variableMapper.search(filter);
    return new SearchResult(hits, totalHits.intValue());
  }

  public record SearchResult(List<VariableEntity> hits, Integer total) {}
}
