/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

/** Shared flat index with one document per logical agent, across process versions. */
public abstract class AgentDefinitionIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 2;

  public static final String AGENT_ID = AgentDefinitionDto.Fields.agentId;
  public static final String TENANT_ID = AgentDefinitionDto.Fields.tenantId;
  public static final String PROCESS_DEFINITION_KEY =
      AgentDefinitionDto.Fields.processDefinitionKey;
  public static final String FLOW_NODE_ID = AgentDefinitionDto.Fields.flowNodeId;
  public static final String NAME = AgentDefinitionDto.Fields.name;
  public static final String AGENT_TYPE = AgentDefinitionDto.Fields.agentType;
  public static final String AGENT_DEFINITION_IDS = AgentDefinitionDto.Fields.agentDefinitionIds;
  public static final String PROCESS_DEFINITION_VERSIONS =
      AgentDefinitionDto.Fields.processDefinitionVersions;

  @Override
  public String getIndexName() {
    return DatabaseConstants.AGENT_DEFINITION_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(AGENT_ID, p -> p.keyword(k -> k))
        .properties(TENANT_ID, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_KEY, p -> p.keyword(k -> k))
        .properties(FLOW_NODE_ID, p -> p.keyword(k -> k))
        .properties(NAME, p -> p.keyword(k -> k))
        .properties(AGENT_TYPE, p -> p.keyword(k -> k))
        .properties(AGENT_DEFINITION_IDS, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_VERSIONS, p -> p.keyword(k -> k));
  }
}
