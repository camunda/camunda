/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.mapper.SequenceFlowEntityMapper;
import io.camunda.db.rdbms.sql.SequenceFlowMapper;
import io.camunda.search.clients.reader.SequenceFlowReader;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.entities.SequenceFlowEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SequenceFlowQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SequenceFlowDbReader extends AbstractEntityReader<SequenceFlowEntity>
    implements SequenceFlowReader {

  private static final Logger LOG = LoggerFactory.getLogger(SequenceFlowDbReader.class);

  private final SequenceFlowMapper sequenceFlowMapper;

  public SequenceFlowDbReader(final SequenceFlowMapper sequenceFlowMapper) {
    super(null);
    this.sequenceFlowMapper = sequenceFlowMapper;
  }

  public SearchQueryResult<SequenceFlowEntity> search(final SequenceFlowQuery filter) {
    LOG.trace("[RDBMS DB] Search for sequence flows with {}", filter);
    final var hits =
        sequenceFlowMapper.search(filter).stream().map(SequenceFlowEntityMapper::toEntity).toList();
    return buildSearchQueryResult(hits.size(), hits, null);
  }

  @Override
  public SequenceFlowEntity getByKey(
      final String key, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException("SequenceFlowReader#getByKey not supported");
  }

  @Override
  public SearchQueryResult<SequenceFlowEntity> search(
      final SequenceFlowQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return search(query);
  }
}
