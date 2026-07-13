/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service;

import io.camunda.optimize.dto.optimize.AgentCostRateConfigDto;
import io.camunda.optimize.dto.optimize.AgentCostRateConfigDto.AgentCostRateDto;
import io.camunda.optimize.service.db.reader.AgentCostRateReader;
import io.camunda.optimize.service.db.writer.AgentCostRateWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Owns the LLM cost-rate config. Wraps the DB reader/writer with a short-TTL in-memory cache so the
 * importer can read the current rates ({@link #getRateSnapshot()}) on every batch without an ES/OS
 * round-trip per instance. The cache is refreshed immediately on write and otherwise every {@link
 * #CACHE_TTL_MS} milliseconds.
 */
@Component
public class AgentCostRateService {

  public static final String INPUT_KEY = "input";
  public static final String OUTPUT_KEY = "output";
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(AgentCostRateService.class);
  private static final long CACHE_TTL_MS = 30_000L;

  private final AgentCostRateReader reader;
  private final AgentCostRateWriter writer;
  private final AtomicReference<Cached> cache = new AtomicReference<>(null);

  public AgentCostRateService(final AgentCostRateReader reader, final AgentCostRateWriter writer) {
    this.reader = reader;
    this.writer = writer;
  }

  /** The current config, or an empty (no rates) config when none has been saved yet. */
  public AgentCostRateConfigDto getConfig() {
    return loadCached();
  }

  public void upsert(final AgentCostRateConfigDto config) {
    writer.upsertConfig(config);
    // Refresh the cache immediately so a subsequent import prices at the new rates.
    cache.set(new Cached(config, System.currentTimeMillis()));
  }

  /**
   * Flattened, painless-friendly snapshot: {@code model -> {input, output}} rate per 1000 tokens.
   * Rates with a missing model or direction price are skipped.
   */
  public Map<String, Map<String, Double>> getRateSnapshot() {
    final Map<String, Map<String, Double>> snapshot = new HashMap<>();
    for (final AgentCostRateDto rate : loadCached().getRates()) {
      if (rate.getModel() == null) {
        continue;
      }
      final Map<String, Double> perDirection =
          snapshot.computeIfAbsent(rate.getModel(), k -> new HashMap<>());
      if (rate.getInputRatePer1k() != null) {
        perDirection.put(INPUT_KEY, rate.getInputRatePer1k());
      }
      if (rate.getOutputRatePer1k() != null) {
        perDirection.put(OUTPUT_KEY, rate.getOutputRatePer1k());
      }
    }
    return snapshot;
  }

  private AgentCostRateConfigDto loadCached() {
    final Cached current = cache.get();
    if (current != null && !current.isExpired()) {
      return current.config;
    }
    final AgentCostRateConfigDto config;
    try {
      config = reader.getConfig().orElseGet(AgentCostRateConfigDto::new);
    } catch (final RuntimeException e) {
      // If the store is briefly unavailable, keep serving the last known config rather than
      // failing the import; fall back to an empty config only when nothing is cached.
      LOG.warn("Could not refresh agent cost rate config; serving last known value.", e);
      return current != null ? current.config : new AgentCostRateConfigDto();
    }
    cache.set(new Cached(config, System.currentTimeMillis()));
    return config;
  }

  private static final class Cached {
    private final AgentCostRateConfigDto config;
    private final long loadedAtMs;

    private Cached(final AgentCostRateConfigDto config, final long loadedAtMs) {
      this.config = config;
      this.loadedAtMs = loadedAtMs;
    }

    private boolean isExpired() {
      return System.currentTimeMillis() - loadedAtMs > CACHE_TTL_MS;
    }
  }
}
