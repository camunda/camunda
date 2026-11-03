/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.RequestType;
import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentIds;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Upserts the identity part of agent definition documents. The run rollups (model, limits, first
 * and last seen) are written by the agent instance import and are never touched here, so the two
 * imports can create the document in either order.
 */
@Component
public class AgentDefinitionWriter {

  private static final String IMPORT_NAME = "agent definitions";
  private static final String PARAM_DEFINITION = "definition";

  // Identity fields come from the highest process version seen, so a renamed agent shows its
  // latest name. Version and key lists are unions, which keeps the merge order-independent.
  private static final String MERGE_IDENTITY_SCRIPT =
      """
      def doc = ctx._source;
      def inc = params.definition;
      doc.agentId = inc.agentId;
      doc.tenantId = inc.tenantId;
      doc.processDefinitionKey = inc.processDefinitionKey;
      doc.flowNodeId = inc.flowNodeId;
      if (doc.agentDefinitionIds == null) { doc.agentDefinitionIds = new ArrayList(); }
      for (def id : inc.agentDefinitionIds) {
        if (!doc.agentDefinitionIds.contains(id)) { doc.agentDefinitionIds.add(id); }
      }
      int maxVersion = -1;
      if (doc.processDefinitionVersions == null) {
        doc.processDefinitionVersions = new ArrayList();
      }
      for (def v : doc.processDefinitionVersions) {
        int version = Integer.parseInt(v.toString());
        if (version > maxVersion) { maxVersion = version; }
      }
      for (def v : inc.processDefinitionVersions) {
        if (!doc.processDefinitionVersions.contains(v)) { doc.processDefinitionVersions.add(v); }
        if (Integer.parseInt(v.toString()) >= maxVersion) {
          doc.name = inc.name;
          doc.agentType = inc.agentType;
        }
      }
      """;

  private final ObjectMapper objectMapper;

  public AgentDefinitionWriter(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<ImportRequestDto> generateAgentDefinitionImports(
      final List<AgentDefinitionDto> agentDefinitions) {
    return agentDefinitions.stream()
        .map(
            definition ->
                ImportRequestDto.builder()
                    .importName(IMPORT_NAME)
                    .type(RequestType.UPDATE)
                    .id(
                        AgentIds.agentDefinitionDocumentId(
                            definition.getTenantId(),
                            definition.getProcessDefinitionKey(),
                            definition.getFlowNodeId()))
                    .indexName(AGENT_DEFINITION_INDEX_NAME)
                    .source(definition)
                    .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                    .scriptData(
                        DatabaseWriterUtil.createScriptData(
                            MERGE_IDENTITY_SCRIPT,
                            Map.of(PARAM_DEFINITION, definition),
                            objectMapper))
                    .build())
        .toList();
  }
}
