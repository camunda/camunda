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
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.RequestType;
import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentIds;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  // Rollups from runs: the config of the most recently active run wins, first/last seen widen.
  // Identity fields are only filled when the agent definition import hasn't created the doc yet.
  private static final String MERGE_ROLLUP_SCRIPT =
      """
      def doc = ctx._source;
      def r = params.rollup;
      def formatter = new SimpleDateFormat(params.dateFormatPattern);
      if (doc.agentId == null) { doc.agentId = r.agentId; }
      if (doc.tenantId == null) { doc.tenantId = r.tenantId; }
      if (doc.processDefinitionKey == null) { doc.processDefinitionKey = r.processDefinitionKey; }
      if (doc.flowNodeId == null) { doc.flowNodeId = r.flowNodeId; }
      if (r.firstSeen != null && (doc.firstSeen == null
          || formatter.parse(r.firstSeen).before(formatter.parse(doc.firstSeen)))) {
        doc.firstSeen = r.firstSeen;
      }
      if (r.lastSeen != null && (doc.lastSeen == null
          || !formatter.parse(r.lastSeen).before(formatter.parse(doc.lastSeen)))) {
        doc.lastSeen = r.lastSeen;
        doc.model = r.model;
        doc.provider = r.provider;
        doc.maxModelCalls = r.maxModelCalls;
        doc.maxToolCalls = r.maxToolCalls;
        doc.maxTokens = r.maxTokens;
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

  /** Rolls up runs of one import page into one update per agent. */
  public List<ImportRequestDto> generateAgentRunRollupImports(final List<AgentRunDto> runs) {
    final Map<String, List<AgentRunDto>> runsByAgent = new LinkedHashMap<>();
    runs.forEach(
        run ->
            runsByAgent
                .computeIfAbsent(
                    AgentIds.agentDefinitionDocumentId(
                        run.getTenantId(), run.getProcessDefinitionKey(), run.getFlowNodeId()),
                    id -> new ArrayList<>())
                .add(run));
    return runsByAgent.entrySet().stream()
        .map(
            entry -> {
              final AgentDefinitionDto rollup = toRollup(entry.getValue());
              return ImportRequestDto.builder()
                  .importName(IMPORT_NAME)
                  .type(RequestType.UPDATE)
                  .id(entry.getKey())
                  .indexName(AGENT_DEFINITION_INDEX_NAME)
                  .source(rollup)
                  .retryNumberOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
                  .scriptData(
                      DatabaseWriterUtil.createScriptData(
                          MERGE_ROLLUP_SCRIPT,
                          Map.of("rollup", rollup, "dateFormatPattern", OPTIMIZE_DATE_FORMAT),
                          objectMapper))
                  .build();
            })
        .toList();
  }

  static AgentDefinitionDto toRollup(final Collection<AgentRunDto> runsOfOneAgent) {
    final AgentRunDto latest =
        runsOfOneAgent.stream()
            .filter(run -> lastSeen(run) != null)
            .max(Comparator.comparing(AgentDefinitionWriter::lastSeen))
            .orElse(runsOfOneAgent.iterator().next());
    final AgentDefinitionDto rollup = new AgentDefinitionDto();
    rollup.setAgentId(latest.getAgentId());
    rollup.setTenantId(latest.getTenantId());
    rollup.setProcessDefinitionKey(latest.getProcessDefinitionKey());
    rollup.setFlowNodeId(latest.getFlowNodeId());
    rollup.setModel(latest.getModel());
    rollup.setProvider(latest.getProvider());
    rollup.setMaxModelCalls(latest.getMaxModelCalls());
    rollup.setMaxToolCalls(latest.getMaxToolCalls());
    rollup.setMaxTokens(latest.getMaxTokens());
    rollup.setLastSeen(lastSeen(latest));
    rollup.setFirstSeen(
        runsOfOneAgent.stream()
            .map(AgentRunDto::getStartDate)
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder())
            .orElse(null));
    return rollup;
  }

  private static OffsetDateTime lastSeen(final AgentRunDto run) {
    return run.getLastActivityDate() != null ? run.getLastActivityDate() : run.getStartDate();
  }
}
