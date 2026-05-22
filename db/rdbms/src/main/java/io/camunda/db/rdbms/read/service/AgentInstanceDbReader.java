/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.search.clients.reader.AgentInstanceReader;
import io.camunda.search.entities.AgentInstanceEntity;
import io.camunda.search.query.AgentInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;

/**
 * RDBMS-backed reader for agent instances.
 *
 * <p>Full implementation will be covered in <a
 * href="https://github.com/camunda/camunda/issues/52820">#52820</a>.
 */
public class AgentInstanceDbReader extends AbstractEntityReader<AgentInstanceEntity>
    implements AgentInstanceReader {

  public AgentInstanceDbReader(final RdbmsReaderConfig readerConfig) {
    // FIXME when implementing #52820 substitute `null` with `AgentInstanceSearchColumn.values()`
    super(null, readerConfig);
  }

  @Override
  public AgentInstanceEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException(
        "AgentInstanceDbReader is not yet implemented; see https://github.com/camunda/camunda/issues/52820");
  }

  @Override
  public SearchQueryResult<AgentInstanceEntity> search(
      final AgentInstanceQuery query, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException(
        "AgentInstanceDbReader is not yet implemented; see https://github.com/camunda/camunda/issues/52820");
  }
}
