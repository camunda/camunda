/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.entities.AgentDefinitionEntity;
import io.camunda.search.query.AgentDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.core.auth.SecurityContext;

public interface AgentDefinitionSearchClient {

  AgentDefinitionEntity getAgentDefinition(final long key);

  SearchQueryResult<AgentDefinitionEntity> searchAgentDefinitions(AgentDefinitionQuery filter);

  AgentDefinitionSearchClient withSecurityContext(SecurityContext securityContext);
}
