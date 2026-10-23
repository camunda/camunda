/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.reader.AgentRunEnrichmentReader;
import io.camunda.optimize.service.db.writer.AgentRunWriter;
import io.camunda.optimize.service.db.writer.ProcessInstanceImportListener;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.ValueType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Keeps the process-instance-derived fields of agent runs (process state, incidents, user tasks,
 * tool calls) in line with the process instance index. Both paths recompute from the whole process
 * instance document:
 *
 * <ul>
 *   <li>Main path: the agent run import enriches the runs it just wrote, by ID (real-time read).
 *   <li>Catch-up path: after any other process-instance import (flow nodes, completion, incidents,
 *       user tasks), the runs of those process instances are re-enriched. Their lookup is a search,
 *       so a run written within the refresh interval can be missed here; the main path covers it,
 *       because it reads the process instance after that data was written.
 * </ul>
 *
 * <p>Failures are logged and swallowed: enrichment must never block the imports that trigger it.
 */
@Component
public class AgentRunEnrichmentService implements ProcessInstanceImportListener {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AgentRunEnrichmentService.class);
  // Variables never feed agent metrics, and the nested agent import is mirrored by the flat one.
  private static final Set<String> IGNORED_RECORD_TYPES =
      Set.of(ValueType.VARIABLE.name(), ValueType.AGENT_INSTANCE.name());

  private final AgentRunEnrichmentReader reader;
  private final AgentRunWriter agentRunWriter;
  private final DatabaseClient databaseClient;
  private final ConfigurationService configurationService;

  public AgentRunEnrichmentService(
      final AgentRunEnrichmentReader reader,
      final AgentRunWriter agentRunWriter,
      final DatabaseClient databaseClient,
      final ConfigurationService configurationService) {
    this.reader = reader;
    this.agentRunWriter = agentRunWriter;
    this.databaseClient = databaseClient;
    this.configurationService = configurationService;
  }

  public void enrichRuns(final Collection<String> agentInstanceIds) {
    try {
      enrich(reader.getRunsByIds(agentInstanceIds));
    } catch (final RuntimeException e) {
      LOG.warn(
          "Could not enrich {} agent runs; will retry on catch-up", agentInstanceIds.size(), e);
    }
  }

  @Override
  public void onProcessInstancesImported(
      final String recordType, final List<ProcessInstanceDto> processInstances) {
    if (IGNORED_RECORD_TYPES.contains(recordType) || processInstances.isEmpty()) {
      return;
    }
    try {
      enrich(
          reader.getRunsByProcessInstanceIds(
              processInstances.stream()
                  .map(ProcessInstanceDto::getProcessInstanceId)
                  .collect(Collectors.toSet())));
    } catch (final RuntimeException e) {
      LOG.warn(
          "Could not catch up agent runs of {} process instances after {} import",
          processInstances.size(),
          recordType,
          e);
    }
  }

  private void enrich(final List<AgentRunDto> runs) {
    if (runs.isEmpty()) {
      return;
    }
    final Map<String, ProcessInstanceDto> processInstances =
        reader.getProcessInstances(
            runs.stream()
                .filter(run -> run.getProcessInstanceId() != null)
                .collect(
                    Collectors.toMap(
                        AgentRunDto::getProcessInstanceId,
                        AgentRunDto::getProcessDefinitionKey,
                        (a, b) -> a)));
    final OffsetDateTime now = OffsetDateTime.now();
    final List<AgentRunDto> enriched =
        runs.stream()
            .map(
                run ->
                    processInstances.containsKey(run.getProcessInstanceId())
                        ? AgentRunEnrichment.compute(
                            run, processInstances.get(run.getProcessInstanceId()), now)
                        : null)
            .filter(Objects::nonNull)
            .toList();
    if (enriched.isEmpty()) {
      return;
    }
    databaseClient.executeImportRequestsAsBulk(
        "Agent run enrichment",
        agentRunWriter.generateEnrichmentImports(enriched),
        configurationService.getSkipDataAfterNestedDocLimitReached());
    LOG.debug("Enriched {} of {} agent runs.", enriched.size(), runs.size());
  }
}
