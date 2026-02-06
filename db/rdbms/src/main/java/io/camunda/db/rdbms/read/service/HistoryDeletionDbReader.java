/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.sql.HistoryDeletionMapper;
import io.camunda.db.rdbms.write.domain.HistoryDeletionBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistoryDeletionDbReader {

  private static final Logger LOG = LoggerFactory.getLogger(HistoryDeletionDbReader.class);
  private final HistoryDeletionMapper historyDeletionMapper;

  public HistoryDeletionDbReader(HistoryDeletionMapper historyDeletionMapper) {
    this.historyDeletionMapper = historyDeletionMapper;
  }

  public HistoryDeletionBatch getNextBatch(final int partitionId, final int limit) {
    LOG.trace(
        "[RDBMS DB] Fetch first {} history deletion records from partition {}", limit, partitionId);

    return new HistoryDeletionBatch(
        historyDeletionMapper.getHistoryDeletionBatch(partitionId, limit));
  }
}
