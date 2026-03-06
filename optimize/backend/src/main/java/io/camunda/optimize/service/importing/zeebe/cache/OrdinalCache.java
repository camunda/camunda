/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.cache;

import io.camunda.optimize.service.db.repository.OrdinalRepository;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

/**
 * In-memory cache that maps ordinal values (short counters incremented every minute by the Zeebe
 * engine) to their corresponding epoch-millis timestamp.
 *
 * <p>On startup the cache is pre-populated from the ordinal index in Elasticsearch via {@link
 * OrdinalRepository}. Thereafter, new entries are added by {@link ZeebeOrdinalImportService} as
 * ORDINAL records arrive.
 *
 * <p>The formatted tick string (e.g. {@code "20260306-1430"}) derived from the ordinal timestamp is
 * used as a suffix in flat index names to form a combined key: {@code
 * <processDefinitionKey>-<yyyyMMdd-HHmm>}.
 */
@Component
public class OrdinalCache {

  /** Format used for the ordinal tick portion of index names: {@code yyyyMMdd-HHmm}. */
  public static final DateTimeFormatter TICK_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneOffset.UTC);

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(OrdinalCache.class);

  private final ConcurrentHashMap<Integer, Long> cache = new ConcurrentHashMap<>();
  private final OrdinalRepository ordinalRepository;

  public OrdinalCache(final OrdinalRepository ordinalRepository) {
    this.ordinalRepository = ordinalRepository;
  }

  @PostConstruct
  public void loadFromDatabase() {
    final var loaded = ordinalRepository.loadAllOrdinals();
    cache.putAll(loaded);
    LOG.info(
        "OrdinalCache: loaded {} ordinal entries from Elasticsearch on startup.", loaded.size());
  }

  /**
   * Adds or updates the timestamp for the given ordinal value.
   *
   * @param ordinal the ordinal value (short counter from Zeebe)
   * @param timestampMs epoch-millis timestamp of the ordinal tick
   */
  public void update(final int ordinal, final long timestampMs) {
    cache.put(ordinal, timestampMs);
  }

  /**
   * Returns the formatted ordinal tick string (e.g. {@code "20260306-1430"}) for the given ordinal
   * value.
   *
   * @param ordinal the ordinal value
   * @return the formatted tick string
   * @throws IllegalArgumentException if the ordinal value is not found in the cache
   */
  public String getTickString(final int ordinal) {
    final Long timestampMs = cache.get(ordinal);
    if (timestampMs == null) {
      throw new IllegalArgumentException(
          "Ordinal value "
              + ordinal
              + " is not present in the OrdinalCache. "
              + "The ordinal must be known before indexing flat events.");
    }
    return TICK_FORMATTER.format(Instant.ofEpochMilli(timestampMs));
  }

  /** Returns {@code true} if the cache contains an entry for the given ordinal. */
  public boolean contains(final int ordinal) {
    return cache.containsKey(ordinal);
  }

  /** Returns the current number of ordinal entries held in memory. */
  public int size() {
    return cache.size();
  }

  /**
   * Returns the combined index key used as the suffix when creating per-ordinal flat indices: the
   * process definition key joined with the ordinal tick string by a dash, e.g. {@code
   * "myprocess-20260306-1430"}.
   *
   * @param processDefinitionKey the BPMN process ID
   * @param ordinal the ordinal value
   * @throws IllegalArgumentException if the ordinal value is not in the cache
   */
  public String combinedIndexKey(final String processDefinitionKey, final int ordinal) {
    return processDefinitionKey + "-" + getTickString(ordinal);
  }
}
