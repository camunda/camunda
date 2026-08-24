/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.db.reader.JobRegistryReader;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DeletedProcessDefinitionFilter {

  private static final Logger LOG = LoggerFactory.getLogger(DeletedProcessDefinitionFilter.class);

  private final JobRegistryReader jobRegistryReader;

  public DeletedProcessDefinitionFilter(final JobRegistryReader jobRegistryReader) {
    this.jobRegistryReader = jobRegistryReader;
  }

  /**
   * Returns the subset of {@code processDefinitionIds} that have a job registry entry (in any
   * status) for a DELETE job on PROCESS_DEFINITION.
   */
  public Set<String> suppressedDefinitionIds(final Collection<String> processDefinitionIds) {
    if (processDefinitionIds.isEmpty()) {
      return Set.of();
    }
    return jobRegistryReader.findEntityIdsWithJob(
        JobType.DELETE, EntityType.PROCESS_DEFINITION, processDefinitionIds);
  }

  /**
   * Filters out entries whose process definition (identified via {@code
   * processDefinitionIdExtractor}) has a pending or completed deletion job in the job registry.
   */
  public <T> List<T> filterOutSuppressed(
      final List<T> entries, final Function<T, String> processDefinitionIdExtractor) {
    if (entries.isEmpty()) {
      return entries;
    }
    final Set<String> candidateIds =
        entries.stream().map(processDefinitionIdExtractor).collect(Collectors.toSet());
    final Set<String> suppressedIds = suppressedDefinitionIds(candidateIds);
    if (suppressedIds.isEmpty()) {
      return entries;
    }
    final List<T> filteredEntries =
        entries.stream()
            .filter(entry -> !suppressedIds.contains(processDefinitionIdExtractor.apply(entry)))
            .toList();
    LOG.info(
        "Suppressing import of {} entries for process definition ids {} with a deletion job.",
        entries.size() - filteredEntries.size(),
        suppressedIds);
    return filteredEntries;
  }
}
