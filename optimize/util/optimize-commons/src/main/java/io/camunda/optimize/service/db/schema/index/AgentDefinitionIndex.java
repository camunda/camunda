/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

/** Shared flat index with one document per logical agent, across process versions. */
public abstract class AgentDefinitionIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 1;

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
  public static final String MODEL = AgentDefinitionDto.Fields.model;
  public static final String PROVIDER = AgentDefinitionDto.Fields.provider;
  public static final String MAX_MODEL_CALLS = AgentDefinitionDto.Fields.maxModelCalls;
  public static final String MAX_TOOL_CALLS = AgentDefinitionDto.Fields.maxToolCalls;
  public static final String MAX_TOKENS = AgentDefinitionDto.Fields.maxTokens;
  public static final String FIRST_SEEN = AgentDefinitionDto.Fields.firstSeen;
  public static final String LAST_SEEN = AgentDefinitionDto.Fields.lastSeen;

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
        .properties(PROCESS_DEFINITION_VERSIONS, p -> p.keyword(k -> k))
        .properties(MODEL, p -> p.keyword(k -> k))
        .properties(PROVIDER, p -> p.keyword(k -> k))
        .properties(MAX_MODEL_CALLS, p -> p.long_(k -> k))
        .properties(MAX_TOOL_CALLS, p -> p.long_(k -> k))
        .properties(MAX_TOKENS, p -> p.long_(k -> k))
        .properties(FIRST_SEEN, p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(LAST_SEEN, p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)));
  }
}
