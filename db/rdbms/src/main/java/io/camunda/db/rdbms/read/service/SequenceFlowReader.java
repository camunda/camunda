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
import io.camunda.search.entities.SequenceFlowEntity;
import io.camunda.search.query.SequenceFlowQuery;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SequenceFlowReader extends AbstractEntityReader<SequenceFlowEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(SequenceFlowReader.class);

  private final SequenceFlowMapper sequenceFlowMapper;

  public SequenceFlowReader(final SequenceFlowMapper sequenceFlowMapper) {
    super(null);
    this.sequenceFlowMapper = sequenceFlowMapper;
  }

  public List<SequenceFlowEntity> search(final SequenceFlowQuery filter) {
    LOG.trace("[RDBMS DB] Search for sequence flows with {}", filter);
    return sequenceFlowMapper.search(filter).stream()
        .map(SequenceFlowEntityMapper::toEntity)
        .toList();
  }
}
