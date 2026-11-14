/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentIds;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.reader.AgentRunEnrichmentReader;
import io.camunda.optimize.service.db.writer.AgentRunWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Copies what the dashboard needs from a run's process instance onto the run, once the run is
 * complete.
 *
 * <ul>
 *   <li>Main path: the agent run import enriches the runs whose {@code COMPLETED} record was in the
 *       page it just wrote. The agent import is gated on the process instance and incident imports
 *       of the partition, so the process instance is complete when it is read. Runs of running
 *       process instances are never enriched: the dashboard only shows completed runs.
 *   <li>Exporter retry path: if the Zeebe exporter retried a partly failed bulk, an incident can
 *       become visible after the agent import passed its position. The incident import then
 *       re-enriches the finished runs of that process instance. In normal operation it finds no
 *       such incident and does nothing.
 * </ul>
 *
 * <p>Enrichment always recomputes everything from the process instance, so running it again gives
 * the same result.
 */
@Component
public class AgentRunEnrichmentService {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AgentRunEnrichmentService.class);

  private final AgentRunEnrichmentReader reader;
  private final AgentRunWriter agentRunWriter;
  private final DatabaseClient databaseClient;
  private final ConfigurationService configurationService;
  // processDefinitionKeys with agents: from the agent definitions, plus keys seen on runs
  private final Set<String> agenticProcessKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();
  private volatile boolean agenticProcessKeysLoaded;

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

  /**
   * Enriches the given runs, which completed in the import page just written. Failures propagate,
   * so the import job retries the page.
   */
  public void enrichCompletedRuns(final Collection<String> agentInstanceIds) {
    if (agentInstanceIds.isEmpty()) {
      return;
    }
    enrich(reader.getRunsByIds(agentInstanceIds));
  }

  /** Remembers the processes of imported runs as agentic. */
  public void addAgenticProcesses(final Collection<String> processDefinitionKeys) {
    processDefinitionKeys.stream().filter(Objects::nonNull).forEach(agenticProcessKeys::add);
  }

  /**
   * Re-enriches the completed runs of finished agentic process instances whose incidents became
   * visible after the agent import passed them.
   *
   * @param processDefinitionKeyByProcessInstanceId process instances of the late incidents
   */
  public void reEnrichAfterLateIncidents(
      final Map<String, String> processDefinitionKeyByProcessInstanceId) {
    final Map<String, String> agentic =
        processDefinitionKeyByProcessInstanceId.entrySet().stream()
            .filter(entry -> isAgentic(entry.getValue()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    if (agentic.isEmpty()) {
      return;
    }
    final Set<String> finished =
        reader.getProcessInstances(agentic).entrySet().stream()
            .filter(entry -> entry.getValue().getEndDate() != null)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    if (finished.isEmpty()) {
      return;
    }
    LOG.info(
        "Re-enriching agent runs of {} finished process instances after late incidents",
        finished.size());
    enrich(reader.getCompletedRunsByProcessInstanceIds(finished));
  }

  private boolean isAgentic(final String processDefinitionKey) {
    if (!agenticProcessKeysLoaded) {
      synchronized (this) {
        if (!agenticProcessKeysLoaded) {
          agenticProcessKeys.addAll(reader.getAgenticProcessDefinitionKeys());
          agenticProcessKeysLoaded = true;
        }
      }
    }
    return agenticProcessKeys.contains(processDefinitionKey);
  }

  private void enrich(final List<AgentRunDto> storedRuns) {
    final List<AgentRunDto> runs =
        storedRuns.stream().filter(run -> run.getEndDate() != null).toList();
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
    final Set<String> rootsToLookUp = new HashSet<>();
    runs.stream()
        .map(AgentRunDto::getRootProcessInstanceId)
        .filter(Objects::nonNull)
        .filter(root -> !processInstances.containsKey(root))
        .forEach(rootsToLookUp::add);
    final Map<String, String> rootProcessDefinitionKeys =
        reader.getProcessDefinitionKeys(rootsToLookUp);
    final Map<String, String> agentTypes =
        reader.getAgentTypes(runs.stream().map(AgentRunEnrichmentService::definitionId).toList());
    final OffsetDateTime now = OffsetDateTime.now();
    final List<AgentRunDto> enriched =
        runs.stream()
            .map(
                run -> {
                  final ProcessInstanceDto processInstance =
                      processInstances.get(run.getProcessInstanceId());
                  if (processInstance == null) {
                    LOG.warn(
                        "Process instance {} of completed agent run {} is not imported; the run"
                            + " stays unenriched",
                        run.getProcessInstanceId(),
                        run.getAgentInstanceId());
                    return null;
                  }
                  return AgentRunEnrichment.compute(
                      run,
                      processInstance,
                      rootProcessDefinitionKey(run, processInstances, rootProcessDefinitionKeys),
                      agentTypes.get(definitionId(run)),
                      now);
                })
            .filter(Objects::nonNull)
            .toList();
    if (enriched.isEmpty()) {
      return;
    }
    databaseClient.executeImportRequestsAsBulk(
        "Agent run enrichment",
        agentRunWriter.generateEnrichmentImports(enriched),
        configurationService.getSkipDataAfterNestedDocLimitReached());
    LOG.debug("Enriched {} of {} completed agent runs.", enriched.size(), runs.size());
  }

  private static String definitionId(final AgentRunDto run) {
    return AgentIds.agentDefinitionDocumentId(
        run.getTenantId(), run.getProcessDefinitionKey(), run.getFlowNodeId());
  }

  private static String rootProcessDefinitionKey(
      final AgentRunDto run,
      final Map<String, ProcessInstanceDto> processInstances,
      final Map<String, String> lookedUp) {
    final String root = run.getRootProcessInstanceId();
    if (root == null) {
      return null;
    }
    final ProcessInstanceDto loaded = processInstances.get(root);
    return loaded != null ? loaded.getProcessDefinitionKey() : lookedUp.get(root);
  }
}
