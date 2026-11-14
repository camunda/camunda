/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.search.clients.reader.VariableHistoryReader;
import io.camunda.search.entities.VariableHistoryItemEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.VariableHistoryItemQuery;
import io.camunda.security.core.authz.ResourceAccessChecks;

public class VariableHistoryDbReader implements VariableHistoryReader {

  @Override
  public SearchQueryResult<VariableHistoryItemEntity> search(
      final VariableHistoryItemQuery query, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException(
        "VariableHistoryDbReader#search() not implemented yet, see"
            + " camunda/pdp-3517-variable-trace#158");
  }

  @Override
  public VariableHistoryItemEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException(
        "VariableHistoryDbReader#getByKey() not implemented yet, see"
            + " camunda/pdp-3517-variable-trace#160");
  }
}
