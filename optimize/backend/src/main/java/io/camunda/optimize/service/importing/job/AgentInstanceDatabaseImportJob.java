/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/**
 * Process instance import job that, after persisting agent-bearing instances, denormalizes the
 * agentic-process flag onto the affected process definitions. The {@link AgenticProcessFlagCache}
 * is JVM-wide to reduce redundant flips across partitions; under concurrency, duplicate flip
 * attempts are still possible but safe due to the idempotent update script.
 */
public class AgentInstanceDatabaseImportJob extends ProcessInstanceDatabaseImportJob {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AgentInstanceDatabaseImportJob.class);

  private final ProcessDefinitionWriter processDefinitionWriter;
  private final AgenticProcessFlagCache agenticProcessFlagCache;

  public AgentInstanceDatabaseImportJob(
      final ProcessInstanceWriter zeebeProcessInstanceWriter,
      final ConfigurationService configurationService,
      final Runnable importCompleteCallback,
      final String sourceExportIndex,
      final DatabaseClient databaseClient,
      final ProcessDefinitionWriter processDefinitionWriter,
      final AgenticProcessFlagCache agenticProcessFlagCache) {
    super(
        zeebeProcessInstanceWriter,
        configurationService,
        importCompleteCallback,
        sourceExportIndex,
        databaseClient);
    this.processDefinitionWriter = processDefinitionWriter;
    this.agenticProcessFlagCache = agenticProcessFlagCache;
  }

  @Override
  protected void persistEntities(final List<ProcessInstanceDto> processInstances) {
    super.persistEntities(processInstances);
    flipAgenticProcessFlagIfNeeded(processInstances);
  }

  /** Post-persist hook; package-private for unit testing. */
  void flipAgenticProcessFlagIfNeeded(final List<ProcessInstanceDto> processInstances) {
    final Set<String> idsToFlip =
        agenticProcessFlagCache.filterUnflipped(
            processInstances.stream()
                .map(ProcessInstanceDto::getProcessDefinitionId)
                .collect(Collectors.toSet()));
    if (idsToFlip.isEmpty()) {
      return;
    }
    try {
      processDefinitionWriter.markDefinitionsAsAgenticProcesses(idsToFlip);
      agenticProcessFlagCache.markFlipped(idsToFlip);
    } catch (final RuntimeException e) {
      LOG.warn(
          "Failed to flip agenticProcess flag for definition ids {}; will retry on next batch",
          idsToFlip,
          e);
    }
  }
}
