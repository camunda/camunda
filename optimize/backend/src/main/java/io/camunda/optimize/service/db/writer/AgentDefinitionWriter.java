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
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Upserts one agent definition document per agent and tenant. The engine writes an agent definition
 * record per process version and repeats it on every partition, so records of the same agent arrive
 * several times and in any order.
 */
@Component
public class AgentDefinitionWriter {

  private static final String IMPORT_NAME = "zeebe agent definitions";
  private static final String PARAM_DEFINITION = "definition";
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(AgentDefinitionWriter.class);

  // Versions are a union. Name and agent type follow the highest process version seen, so an older
  // version imported late does not overwrite them. Nothing changed means noop, so replaying the
  // same records (re-import, or the copy from another partition) writes nothing.
  private static final String MERGE_SCRIPT =
      """
      def doc = ctx._source;
      def incoming = params.definition;
      boolean changed = false;
      if (doc.processDefinitionVersions == null) {
        doc.processDefinitionVersions = new ArrayList();
      }
      int storedMaxVersion = -1;
      for (def version : doc.processDefinitionVersions) {
        storedMaxVersion = (int) Math.max(storedMaxVersion, Integer.parseInt(version.toString()));
      }
      int incomingMaxVersion = -1;
      for (def version : incoming.processDefinitionVersions) {
        incomingMaxVersion =
          (int) Math.max(incomingMaxVersion, Integer.parseInt(version.toString()));
        if (!doc.processDefinitionVersions.contains(version)) {
          doc.processDefinitionVersions.add(version);
          changed = true;
        }
      }
      if (incomingMaxVersion >= storedMaxVersion
          && (doc.name != incoming.name || doc.agentType != incoming.agentType)) {
        doc.name = incoming.name;
        doc.agentType = incoming.agentType;
        changed = true;
      }
      if (!changed) {
        ctx.op = 'noop';
      }
      """;

  private final ObjectMapper objectMapper;

  public AgentDefinitionWriter(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public List<ImportRequestDto> generateAgentDefinitionImports(
      final List<AgentDefinitionDto> agentDefinitions) {
    LOG.debug("Creating imports for {} [{}].", agentDefinitions.size(), IMPORT_NAME);
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
                            MERGE_SCRIPT, Map.of(PARAM_DEFINITION, definition), objectMapper))
                    .build())
        .toList();
  }
}
