/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.search.clients.reader.AgentDefinitionReader;
import io.camunda.search.entities.AgentDefinitionEntity;
import io.camunda.search.query.AgentDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.core.authz.ResourceAccessChecks;

public class AgentDefinitionDbReader implements AgentDefinitionReader {

  @Override
  public SearchQueryResult<AgentDefinitionEntity> search(
      final AgentDefinitionQuery query, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException(
        "AgentDefinitionReader#search() not supported for RDBMS, see"
            + " https://github.com/camunda/camunda/issues/59079");
  }
}
