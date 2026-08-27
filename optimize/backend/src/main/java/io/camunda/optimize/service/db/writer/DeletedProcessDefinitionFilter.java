/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.annotations.VisibleForTesting;
import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.db.reader.JobRegistryReader;
import io.camunda.optimize.service.util.configuration.CacheConfiguration;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Filters out entries whose process definition has a DELETE job registry entry (any status), backed
 * by a cache of suppressed process definition ids. The whole set is held as a single cache entry
 * that expires after a configurable TTL and is re-fetched wholesale on the next lookup.
 */
@Component
public class DeletedProcessDefinitionFilter {

  private static final Logger LOG = LoggerFactory.getLogger(DeletedProcessDefinitionFilter.class);
  private static final String SUPPRESSED_IDS_CACHE_KEY = "suppressedProcessDefinitionIds";

  private final JobRegistryReader jobRegistryReader;
  private final int maxSize;
  private final Cache<String, Set<String>> cache;

  public DeletedProcessDefinitionFilter(
      final JobRegistryReader jobRegistryReader, final ConfigurationService configurationService) {
    this(jobRegistryReader, configurationService, Ticker.systemTicker());
  }

  @VisibleForTesting
  DeletedProcessDefinitionFilter(
      final JobRegistryReader jobRegistryReader,
      final ConfigurationService configurationService,
      final Ticker ticker) {
    this.jobRegistryReader = jobRegistryReader;
    final CacheConfiguration cacheConfig =
        configurationService.getCaches().getDeletedProcessDefinitions();
    maxSize = cacheConfig.getMaxSize();
    cache =
        Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(Duration.ofMillis(cacheConfig.getDefaultTtlMillis()))
            .ticker(ticker)
            .build();
  }

  /**
   * Returns the subset of {@code processDefinitionIds} that have a job registry entry (in any
   * status) for a DELETE job on PROCESS_DEFINITION.
   */
  public Set<String> suppressedDefinitionIds(final Collection<String> processDefinitionIds) {
    if (processDefinitionIds.isEmpty()) {
      return Set.of();
    }
    final Set<String> suppressedIds = getSuppressedIds();
    return processDefinitionIds.stream()
        .filter(Objects::nonNull)
        .filter(suppressedIds::contains)
        .collect(Collectors.toSet());
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
    LOG.debug(
        "Suppressing import of {} entries for process definition ids {} with a deletion job.",
        entries.size() - filteredEntries.size(),
        suppressedIds);
    return filteredEntries;
  }

  private Set<String> getSuppressedIds() {
    return cache.get(SUPPRESSED_IDS_CACHE_KEY, key -> fetchSuppressedIds());
  }

  private Set<String> fetchSuppressedIds() {
    final List<String> entityIds =
        jobRegistryReader.findNewestEntityIds(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, maxSize);
    if (entityIds.size() >= maxSize) {
      LOG.warn(
          "Fetched the maximum number of results ({}). There may be more deleted process"
              + " definitions not included in the result set, whose data could be reimported.",
          maxSize);
    }
    return Set.copyOf(entityIds);
  }
}
