/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.cache;

import io.camunda.optimize.dto.zeebe.ZeebeGenericRecordDto;
import io.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.PreFlattenedWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.ValueType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A per-partition sliding window cache that buffers imported Zeebe records for the last {@value
 * #WINDOW_DURATION_SECONDS} seconds.
 *
 * <p>Every second the cache:
 *
 * <ol>
 *   <li>Evicts entries older than {@value #WINDOW_DURATION_SECONDS} seconds.
 *   <li>Scans records added since the previous tick for PROCESS_INSTANCE / ELEMENT_ACTIVATING /
 *       PROCESS events and constructs a {@link PreFlattenedDTO} for each one, resolving the {@code
 *       region} field from the entire 10-second window (only considering variables whose {@code
 *       processInstanceKey} matches the activating process instance).
 *   <li>Flushes the collected {@link PreFlattenedDTO} documents to Elasticsearch/OpenSearch via
 *       {@link PreFlattenedWriter}.
 * </ol>
 */
public class ZeebeImportSlidingWindowCache {

  static final long WINDOW_DURATION_SECONDS = 10;
  private static final Logger LOG = LoggerFactory.getLogger(ZeebeImportSlidingWindowCache.class);

  private final int partitionId;
  private final String recordType;
  private final CopyOnWriteArrayList<BatchEntry> entries = new CopyOnWriteArrayList<>();

  // ---- generic (combined-index) record storage ----
  private final CopyOnWriteArrayList<GenericBatchEntry> genericEntries =
      new CopyOnWriteArrayList<>();

  /** Timestamp of the last 1-second poll; used to detect records added since the previous tick. */
  private volatile long lastGenericPollTimeMs = System.currentTimeMillis();

  private final PreFlattenedWriter preFlattenedWriter;
  private final DatabaseClient databaseClient;
  private final ConfigurationService configurationService;
  private final ScheduledExecutorService scheduler;

  public ZeebeImportSlidingWindowCache(
      final int partitionId,
      final String recordType,
      final PreFlattenedWriter preFlattenedWriter,
      final DatabaseClient databaseClient,
      final ConfigurationService configurationService) {
    this.partitionId = partitionId;
    this.recordType = recordType;
    this.preFlattenedWriter = preFlattenedWriter;
    this.databaseClient = databaseClient;
    this.configurationService = configurationService;
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              final Thread thread =
                  new Thread(
                      r, "zeebe-sliding-window-cache-partition-" + partitionId + "-" + recordType);
              thread.setDaemon(true);
              return thread;
            });
    // Every second: evict stale entries, produce PreFlattenedDTOs, flush to DB.
    scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
  }

  /**
   * Accepts a batch of newly imported records and adds them to the sliding window cache.
   *
   * @param records the records that were just written to Elasticsearch
   */
  public void accept(final List<? extends ZeebeRecordDto<?, ?>> records) {
    if (records == null || records.isEmpty()) {
      return;
    }
    entries.add(new BatchEntry(System.currentTimeMillis(), new ArrayList<>(records)));
  }

  /**
   * Accepts a batch of generic Zeebe records (from the combined index) and adds them to the generic
   * sliding window. These records are polled every second for {@link PreFlattenedDTO} production.
   *
   * @param records records fetched from the combined Zeebe index
   */
  public void acceptGeneric(final List<ZeebeGenericRecordDto> records) {
    if (records == null || records.isEmpty()) {
      return;
    }
    genericEntries.add(new GenericBatchEntry(System.currentTimeMillis(), new ArrayList<>(records)));
  }

  /**
   * Single scheduled tick (every second):
   *
   * <ol>
   *   <li>Evict entries older than {@value #WINDOW_DURATION_SECONDS} seconds.
   *   <li>Produce {@link PreFlattenedDTO} objects for newly-seen process-start events.
   *   <li>Flush them to the database.
   * </ol>
   */
  void tick() {
    evict();
    final List<PreFlattenedDTO> dtos = pollForPreFlattenedDTOs();
    flushPreFlattenedDTOs(dtos);
  }

  /**
   * Evicts entries that have fallen outside the 10-second sliding window and logs a summary. Called
   * once per second via {@link #tick()}.
   */
  void evict() {
    final long cutoffMs = System.currentTimeMillis() - (WINDOW_DURATION_SECONDS * 1_000L);
    entries.removeIf(entry -> entry.receivedAtMs() < cutoffMs);
    genericEntries.removeIf(entry -> entry.receivedAtMs() < cutoffMs);

    final long totalRecordCount = entries.stream().mapToLong(e -> e.records().size()).sum();
    LOG.debug(
        "Sliding window cache [partition={}, recordType={}]: {} record(s) in the last {}s window"
            + " across {} batch(es)",
        partitionId,
        recordType,
        totalRecordCount,
        WINDOW_DURATION_SECONDS,
        entries.size());
  }

  /**
   * Inspects records added since the previous 1-second tick and creates a {@link PreFlattenedDTO}
   * for every PROCESS_INSTANCE / ELEMENT_ACTIVATING / PROCESS event found. The {@code region} field
   * is resolved by scanning the entire 10-second generic window for a VARIABLE record whose name is
   * {@code "region"} <em>and</em> whose {@code processInstanceKey} matches the activating process
   * instance.
   *
   * @return list of DTOs produced during this tick (may be empty)
   */
  List<PreFlattenedDTO> pollForPreFlattenedDTOs() {
    final long now = System.currentTimeMillis();
    final long pollCutoff = lastGenericPollTimeMs;
    lastGenericPollTimeMs = now;

    // Records added since the last 1-second tick
    final List<ZeebeGenericRecordDto> recentRecords =
        genericEntries.stream()
            .filter(e -> e.receivedAtMs() >= pollCutoff)
            .flatMap(e -> e.records().stream())
            .toList();

    final List<PreFlattenedDTO> result = new ArrayList<>();

    for (final ZeebeGenericRecordDto record : recentRecords) {
      if (!ValueType.PROCESS_INSTANCE.equals(record.getValueType())) {
        continue;
      }
      if (!"ELEMENT_ACTIVATING".equals(record.getIntent())) {
        continue;
      }
      final Map<String, Object> value = record.getValue();
      if (value == null || !"PROCESS".equals(value.get("bpmnElementType"))) {
        continue;
      }

      final long processInstanceKey = toLong(value.get("processInstanceKey"));

      final PreFlattenedDTO dto = new PreFlattenedDTO();
      dto.setProcessInstanceId(processInstanceKey);
      dto.setProcessDefinitionId((String) value.get("bpmnProcessId"));
      dto.setProcessDefinitionKey(toLong(value.get("processDefinitionKey")));
      dto.setTenant((String) value.get("tenantId"));
      dto.setPartition(record.getPartitionId());
      dto.setStartTime(
          OffsetDateTime.ofInstant(
              Instant.ofEpochMilli(record.getTimestamp()), ZoneId.systemDefault()));

      // Search the entire 10-second window for a variable named "region" that belongs to
      // the same process instance.
      final Optional<String> region =
          genericEntries.stream()
              .flatMap(e -> e.records().stream())
              .filter(
                  r ->
                      ValueType.VARIABLE.equals(r.getValueType())
                          && r.getValue() != null
                          && "region".equals(r.getValue().get("name"))
                          && processInstanceKey == toLong(r.getValue().get("processInstanceKey")))
              .map(r -> (String) r.getValue().get("value"))
              .filter(Objects::nonNull)
              .findFirst();
      region.ifPresent(dto::setRegion);

      LOG.info("PreFlattenedDTO [partition={}, recordType={}]: {}", partitionId, recordType, dto);

      result.add(dto);
    }

    return result;
  }

  /**
   * Flushes the given list of {@link PreFlattenedDTO} objects to the database. Called once per
   * second by {@link #tick()}.
   *
   * @param dtos the DTOs to flush; no-op when empty
   */
  void flushPreFlattenedDTOs(final List<PreFlattenedDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return;
    }
    try {
      databaseClient.executeImportRequestsAsBulk(
          "pre-flattened process instances",
          preFlattenedWriter.generatePreFlattenedImports(dtos),
          configurationService.getSkipDataAfterNestedDocLimitReached());
      LOG.debug(
          "Flushed {} PreFlattenedDTO(s) [partition={}, recordType={}]",
          dtos.size(),
          partitionId,
          recordType);
    } catch (final Exception e) {
      LOG.error(
          "Failed to flush {} PreFlattenedDTO(s) [partition={}, recordType={}]: {}",
          dtos.size(),
          partitionId,
          recordType,
          e.getMessage(),
          e);
    }
  }

  /**
   * Returns the number of records currently in the sliding window. Useful for testing and
   * monitoring.
   */
  public long getWindowRecordCount() {
    final long cutoffMs = System.currentTimeMillis() - (WINDOW_DURATION_SECONDS * 1_000L);
    return entries.stream()
        .filter(e -> e.receivedAtMs() >= cutoffMs)
        .mapToLong(e -> e.records().size())
        .sum();
  }

  /** Shuts down the background eviction/logging scheduler gracefully. */
  public void shutdown() {
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        LOG.warn(
            "Sliding window cache scheduler for partition={}, recordType={} did not terminate"
                + " within 5 seconds; forcing shutdown.",
            partitionId,
            recordType);
        scheduler.shutdownNow();
      }
    } catch (final InterruptedException e) {
      LOG.warn(
          "Interrupted while waiting for sliding window cache scheduler to terminate"
              + " [partition={}, recordType={}].",
          partitionId,
          recordType);
      Thread.currentThread().interrupt();
      scheduler.shutdownNow();
    }
  }

  private static long toLong(final Object value) {
    if (value instanceof final Number n) {
      return n.longValue();
    }
    return 0L;
  }

  private record BatchEntry(long receivedAtMs, List<? extends ZeebeRecordDto<?, ?>> records) {}

  private record GenericBatchEntry(long receivedAtMs, List<ZeebeGenericRecordDto> records) {}
}
