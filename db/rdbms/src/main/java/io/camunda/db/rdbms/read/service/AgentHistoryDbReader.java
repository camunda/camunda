/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.search.clients.reader.AgentHistoryReader;
import io.camunda.search.entities.AgentInstanceHistoryEntity;
import io.camunda.search.query.AgentInstanceHistoryQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.core.authz.ResourceAccessChecks;

public class AgentHistoryDbReader extends AbstractEntityReader<AgentInstanceHistoryEntity>
    implements AgentHistoryReader {

  public AgentHistoryDbReader(final RdbmsReaderConfig readerConfig) {
    super(null, readerConfig);
  }

  @Override
  public SearchQueryResult<AgentInstanceHistoryEntity> search(
      final AgentInstanceHistoryQuery query, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException(
        "AgentHistoryDbReader is not yet implemented. Tracked in #55271.");
  }
}
