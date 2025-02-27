/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.DbQuerySorting;
import io.camunda.db.rdbms.read.domain.ProcessDefinitionDbQuery;
import io.camunda.db.rdbms.read.mapper.BatchOperationEntityMapper;
import io.camunda.db.rdbms.sql.BatchOperationMapper;
import io.camunda.db.rdbms.sql.ProcessDefinitionMapper;
import io.camunda.db.rdbms.sql.columns.BatchOperationSearchColumn;
import io.camunda.db.rdbms.sql.columns.ProcessDefinitionSearchColumn;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationReader extends AbstractEntityReader<BatchOperationEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationReader.class);

  private final BatchOperationMapper batchOperationMapper;

  public BatchOperationReader(final BatchOperationMapper batchOperationMapper) {
    super(BatchOperationSearchColumn::findByProperty);
    this.batchOperationMapper = batchOperationMapper;
  }

  public SearchQueryResult<BatchOperationEntity> search() {
    LOG.trace("[RDBMS DB] Search for batch operations");
    final var totalHits = batchOperationMapper.count();
    final var hits = batchOperationMapper.search().stream().map(BatchOperationEntityMapper::toEntity).toList();
    return buildSearchQueryResult(totalHits, hits, DbQuerySorting.of(b -> b));
  }
}
