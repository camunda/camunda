/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.metrics;

import static io.camunda.zeebe.util.StreamProcessingConstants.*;
import static java.util.Optional.empty;
import static java.util.Optional.of;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.metrics.PersistedUsageMetrics;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.protocol.impl.record.value.metrics.UsageMetricRecord;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.IntervalType;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
public class UsageMetricsExportProcessor implements TypedRecordProcessor<UsageMetricRecord> {

  private static final Logger LOG = LoggerFactory.getLogger(UsageMetricsExportProcessor.class);

  private final MutableUsageMetricState usageMetricState;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final SideEffectWriter sideEffectWriter;
  private final TypedCommandWriter typedCommandWriter;

  public UsageMetricsExportProcessor(
      final MutableUsageMetricState usageMetricState,
      final Writers writers,
      final KeyGenerator keyGenerator) {
    this.usageMetricState = usageMetricState;
    stateWriter = writers.state();
    this.keyGenerator = keyGenerator;
    sideEffectWriter = writers.sideEffect();
    typedCommandWriter = writers.command();
  }

  @Override
  public void processRecord(final TypedRecord<UsageMetricRecord> usageMetricRecord) {

    final var usageMetricRecordValue = usageMetricRecord.getValue();
    if (EventType.NONE != usageMetricRecord.getValue().getEventType()) {
      appendFollowUpEvent(usageMetricRecordValue);
      return;
    }

    final UsageMetricRecord eventRecord =
        new UsageMetricRecord()
            .setIntervalType(IntervalType.ACTIVE)
            .setEventType(EventType.NONE)
            .setResetTime(usageMetricRecord.getTimestamp());

    final var bucket = usageMetricState.getActiveBucket();
    if (bucket == null || !bucket.isInitialized()) {
      appendFollowUpEvent(eventRecord);
      return;
    }

    final var isRPIMapEmpty = bucket.getTenantRPIMap().isEmpty();
    final var isEDIMapEmpty = bucket.getTenantEDIMap().isEmpty();
    final var isTUMapEmpty = bucket.getTenantTUMap().isEmpty();

    final var events = new ArrayList<UsageMetricRecord>();
    if (!isRPIMapEmpty || !isEDIMapEmpty || !isTUMapEmpty) {
      processMetricType(
              bucket, eventRecord, EventType.RPI, isRPIMapEmpty, bucket.getTenantRPIMapValue())
          .ifPresent(events::add);
      processMetricType(
              bucket, eventRecord, EventType.EDI, isEDIMapEmpty, bucket.getTenantEDIMapValue())
          .ifPresent(events::add);
      processMetricType(
              bucket, eventRecord, EventType.TU, isTUMapEmpty, bucket.getTenantTUMapValue())
          .ifPresent(events::add);
    } else {
      events.add(eventRecord);
    }

    // append events at the end so bucket is not reset while processing
    events.forEach(this::appendFollowUpEvent);
  }

  /** Processes a specific metric type and appends the resulting records. */
  private Optional<UsageMetricRecord> processMetricType(
      final PersistedUsageMetrics bucket,
      final UsageMetricRecord baseRecord,
      final EventType eventType,
      final boolean valuesMapIsEmpty,
      final DirectBuffer valuesBuffer) {
    if (!valuesMapIsEmpty) {
      final UsageMetricRecord clonedRecord = initializeEventRecord(baseRecord);
      enhanceEventRecord(clonedRecord, bucket, eventType, valuesBuffer);
      return of(clonedRecord);
    }
    return empty();
  }

  /** Creates a UsageMetricRecord with original properties. */
  private UsageMetricRecord initializeEventRecord(final UsageMetricRecord original) {
    return new UsageMetricRecord()
        .setIntervalType(original.getIntervalType())
        .setEventType(original.getEventType())
        .setResetTime(original.getResetTime());
  }

  /** Composes the event record with additional information. */
  private void enhanceEventRecord(
      final UsageMetricRecord usageMetricRecord,
      final PersistedUsageMetrics bucket,
      final EventType eventType,
      final DirectBuffer valuesBuffer) {
    usageMetricRecord
        .setEventType(eventType)
        .setStartTime(bucket.getFromTime())
        .setEndTime(bucket.getToTime());
    if (EventType.TU == eventType) {
      usageMetricRecord.setSetValues(valuesBuffer);
    } else {
      usageMetricRecord.setCounterValues(valuesBuffer);
    }
  }

  private void appendFollowUpEvent(final UsageMetricRecord eventRecord) {
    if (stateWriter.canWriteEventOfLength(eventRecord.getLength())) {
      stateWriter.appendFollowUpEvent(
          keyGenerator.nextKey(), UsageMetricIntent.EXPORTED, eventRecord);
    } else {
      LOG.warn("Usage metric event exceeds batch size limit, splitting into smaller records.");
      splitAndAppendRecordHybrid(eventRecord);
    }
  }

  /**
   * Splits a large usage metric record using a hybrid approach. Appends chunks that fit within
   * batch limits as follow-up events and defers remaining chunks as side effects.
   */
  private void splitAndAppendRecordHybrid(final UsageMetricRecord eventRecord) {
    if (!isRecordEligibleForSplitting(eventRecord)) {
      return;
    }

    final var chunkingStrategy = calculateChunkingStrategy(eventRecord);
    final var chunks =
        createChunks(eventRecord.getSetValues(), chunkingStrategy.targetEntriesPerChunk());

    LOG.debug(
        "Splitting record: total entries: {}, target chunk size: {}, estimated bytes per entry: {}, chunks created: {}",
        chunkingStrategy.totalEntries(),
        chunkingStrategy.targetEntriesPerChunk(),
        chunkingStrategy.estimatedBytesPerEntry(),
        chunks.size());

    final var categorizedChunks = categorizeChunks(eventRecord, chunks);

    // Process immediate chunks and handle failures by moving them to deferred
    appendImmediateChunks(categorizedChunks.immediate(), categorizedChunks.deferred());
    deferRemainingChunks(categorizedChunks.deferred());
  }

  /** Calculates the optimal chunking strategy for the given record. */
  private ChunkingStrategy calculateChunkingStrategy(final UsageMetricRecord eventRecord) {
    final var originalSetValues = eventRecord.getSetValues();
    final int baseRecordOverhead = calculateBaseRecordOverhead(eventRecord);
    final int availableSpace = CONSERVATIVE_FRAGMENT_SIZE_LIMIT - baseRecordOverhead;
    final int totalEntries = calculateTotalEntries(originalSetValues);
    final int estimatedBytesPerEntry =
        estimateAverageBytesPerEntry(originalSetValues, baseRecordOverhead);
    final int targetEntriesPerChunk =
        calculateOptimalChunkSize(availableSpace, estimatedBytesPerEntry, totalEntries);

    return new ChunkingStrategy(
        CONSERVATIVE_FRAGMENT_SIZE_LIMIT,
        baseRecordOverhead,
        availableSpace,
        totalEntries,
        estimatedBytesPerEntry,
        targetEntriesPerChunk);
  }

  /** Calculates the total number of entries across all tenants. */
  private int calculateTotalEntries(final Map<String, Set<Long>> setValues) {
    return setValues.values().stream().mapToInt(Set::size).sum();
  }

  /** Calculates optimal chunk size based on available space and entry size. */
  private int calculateOptimalChunkSize(
      final int availableSpace, final int estimatedBytesPerEntry, final int totalEntries) {
    final int baseTargetSize = Math.max(1, availableSpace / estimatedBytesPerEntry);
    final int maxChunkSize = Math.max(MIN_CHUNK_SIZE, totalEntries / CHUNK_SIZE_DIVISOR);
    return Math.min(baseTargetSize, maxChunkSize);
  }

  /** Creates chunks from the set values using the specified target size. */
  private List<Map<String, Set<Long>>> createChunks(
      final Map<String, Set<Long>> setValues, final int targetEntriesPerChunk) {
    final var tenantEntries = new ArrayList<>(setValues.entrySet());
    return createOptimizedChunks(tenantEntries, targetEntriesPerChunk);
  }

  /** Categorizes chunks into immediate and deferred based on size constraints. */
  private CategorizedChunks categorizeChunks(
      final UsageMetricRecord eventRecord, final List<Map<String, Set<Long>>> chunks) {
    final var immediateChunks = new ArrayList<UsageMetricRecord>();
    final var deferredChunks = new ArrayList<UsageMetricRecord>();

    for (final var chunk : chunks) {
      final var chunkRecord = createChunkRecord(eventRecord, chunk);
      if (stateWriter.canWriteEventOfLength(chunkRecord.getLength())) {
        immediateChunks.add(chunkRecord);
      } else {
        processOversizedChunk(eventRecord, chunk, immediateChunks, deferredChunks);
      }
    }

    LOG.debug(
        "Categorized chunks: {} immediate, {} deferred",
        immediateChunks.size(),
        deferredChunks.size());
    return new CategorizedChunks(immediateChunks, deferredChunks);
  }

  /** Processes oversized chunks by creating micro-chunks. */
  private void processOversizedChunk(
      final UsageMetricRecord eventRecord,
      final Map<String, Set<Long>> chunk,
      final List<UsageMetricRecord> immediateChunks,
      final List<UsageMetricRecord> deferredChunks) {

    final var microChunks = createMicroChunks(eventRecord, chunk);
    for (final var microChunk : microChunks) {
      if (stateWriter.canWriteEventOfLength(microChunk.getLength())) {
        immediateChunks.add(microChunk);
      } else {
        deferredChunks.add(microChunk);
      }
    }
  }

  /**
   * Appends immediate chunks as follow-up events and moves failed chunks to deferred processing.
   */
  private void appendImmediateChunks(
      final List<UsageMetricRecord> immediateChunks, final List<UsageMetricRecord> deferredChunks) {
    for (int i = 0; i < immediateChunks.size(); i++) {
      final var chunkRecord = immediateChunks.get(i);

      try {
        stateWriter.appendFollowUpEvent(
            keyGenerator.nextKey(), UsageMetricIntent.EXPORTED, chunkRecord);
      } catch (final ExceededBatchRecordSizeException e) {
        LOG.warn(
            "Failed to append immediate chunk {}, moving to deferred processing: {}",
            i,
            e.getMessage());
        deferredChunks.add(chunkRecord);
      }
    }
  }

  /** Defers remaining chunks as side effects. */
  private void deferRemainingChunks(final List<UsageMetricRecord> deferredChunks) {
    if (deferredChunks.isEmpty()) {
      return;
    }

    sideEffectWriter.appendSideEffect(
        () -> {
          for (int i = 0; i < deferredChunks.size(); i++) {
            final var deferredChunk = deferredChunks.get(i);

            if (typedCommandWriter.canWriteCommandOfLength(deferredChunk.getLength())) {
              typedCommandWriter.appendFollowUpCommand(
                  keyGenerator.nextKey(), UsageMetricIntent.EXPORT, deferredChunk);
            } else {
              LOG.error("Deferred chunk {} still exceeds size limit even for commands", i);
            }
          }
          return true;
        });
  }

  /** Creates micro chunks from an oversized chunk. */
  private List<UsageMetricRecord> createMicroChunks(
      final UsageMetricRecord originalRecord, final Map<String, Set<Long>> oversizedChunk) {

    final var microChunks = new ArrayList<UsageMetricRecord>();

    oversizedChunk.forEach(
        (tenantId, assigneeSet) ->
            assigneeSet.forEach(
                assignee -> {
                  final var singleEntryMap = Map.of(tenantId, Set.of(assignee));
                  microChunks.add(createChunkRecord(originalRecord, singleEntryMap));
                }));

    LOG.debug("Created {} micro-chunks for oversized chunk", microChunks.size());
    return microChunks;
  }

  /** Creates optimized chunks with improved algorithm. */
  private List<Map<String, Set<Long>>> createOptimizedChunks(
      final List<Map.Entry<String, Set<Long>>> tenantEntries, final int targetEntriesPerChunk) {

    final var chunks = new ArrayList<Map<String, Set<Long>>>();
    var currentChunk = new HashMap<String, Set<Long>>();
    var currentSize = 0;

    for (final var entry : tenantEntries) {
      final var assigneeSet = entry.getValue();
      final var entrySize = assigneeSet.size();

      if (entrySize > targetEntriesPerChunk) {
        addCurrentChunkIfNotEmpty(chunks, currentChunk);
        currentChunk = new HashMap<>();
        currentSize = 0;
        addLargeEntryAsMultipleChunks(chunks, entry, targetEntriesPerChunk);
      } else if (currentSize + entrySize > targetEntriesPerChunk && !currentChunk.isEmpty()) {
        addCurrentChunkIfNotEmpty(chunks, currentChunk);
        currentChunk = new HashMap<>();
        currentChunk.put(entry.getKey(), assigneeSet);
        currentSize = entrySize;
      } else {
        currentChunk.put(entry.getKey(), assigneeSet);
        currentSize += entrySize;
      }
    }

    addCurrentChunkIfNotEmpty(chunks, currentChunk);
    return chunks;
  }

  /** Adds current chunk to the list if it's not empty. */
  private void addCurrentChunkIfNotEmpty(
      final List<Map<String, Set<Long>>> chunks, final Map<String, Set<Long>> currentChunk) {
    if (!currentChunk.isEmpty()) {
      chunks.add(new HashMap<>(currentChunk));
    }
  }

  /** Splits a large entry into multiple chunks. */
  private void addLargeEntryAsMultipleChunks(
      final List<Map<String, Set<Long>>> chunks,
      final Map.Entry<String, Set<Long>> entry,
      final int targetEntriesPerChunk) {

    final var assigneeList = new ArrayList<>(entry.getValue());
    for (int i = 0; i < assigneeList.size(); i += targetEntriesPerChunk) {
      final var endIndex = Math.min(i + targetEntriesPerChunk, assigneeList.size());
      final var subSet = new HashSet<>(assigneeList.subList(i, endIndex));
      chunks.add(Map.of(entry.getKey(), subSet));
    }
  }

  /** Creates a new record for a chunk with the same metadata as the original. */
  private UsageMetricRecord createChunkRecord(
      final UsageMetricRecord originalRecord, final Map<String, Set<Long>> chunkData) {

    final var chunkRecord =
        new UsageMetricRecord()
            .setEventType(originalRecord.getEventType())
            .setIntervalType(originalRecord.getIntervalType())
            .setResetTime(originalRecord.getResetTime())
            .setStartTime(originalRecord.getStartTime())
            .setEndTime(originalRecord.getEndTime());

    final var chunkBuffer =
        BufferUtil.wrapArray(
            io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter.convertToMsgPack(chunkData));
    chunkRecord.setSetValues(chunkBuffer);

    return chunkRecord;
  }

  /** Estimates the average bytes per entry using sampling. */
  private int estimateAverageBytesPerEntry(
      final Map<String, Set<Long>> setValues, final int baseRecordOverhead) {

    var totalBytes = 0;
    var totalEntries = 0;
    var sampledEntries = 0;
    final int maxSamples = setValues.size() / 2 + 1;

    for (final var entry : setValues.entrySet()) {
      if (sampledEntries >= maxSamples) {
        break;
      }

      final var tenantIdBytes = entry.getKey().getBytes().length;
      final var assigneeSetSize = entry.getValue().size();
      final var entryBytes =
          baseRecordOverhead + tenantIdBytes + (assigneeSetSize * BYTES_PER_LONG);

      totalBytes += entryBytes;
      totalEntries += assigneeSetSize;
      sampledEntries++;
    }

    return sampledEntries > 0 ? totalBytes / totalEntries : DEFAULT_BYTES_PER_ENTRY;
  }

  /** Calculates the base overhead of a record without the set values data. */
  private int calculateBaseRecordOverhead(final UsageMetricRecord originalRecord) {
    final var testRecord =
        new UsageMetricRecord()
            .setEventType(originalRecord.getEventType())
            .setIntervalType(originalRecord.getIntervalType())
            .setResetTime(originalRecord.getResetTime())
            .setStartTime(originalRecord.getStartTime())
            .setEndTime(originalRecord.getEndTime())
            .setSetValues(BufferUtil.wrapArray(new byte[0]));

    return testRecord.getLength();
  }

  /**
   * Validates if the event record is eligible for splitting.
   *
   * @param eventRecord the usage metric record to validate
   * @return true if the record can be split, false otherwise
   */
  private boolean isRecordEligibleForSplitting(final UsageMetricRecord eventRecord) {
    if (EventType.TU != eventRecord.getEventType()) {
      return false;
    }

    final var originalSetValues = eventRecord.getSetValues();
    return originalSetValues != null && !originalSetValues.isEmpty();
  }

  /** Strategy for chunking records based on size constraints. */
  private record ChunkingStrategy(
      int maxRecordLength,
      int baseRecordOverhead,
      int availableSpace,
      int totalEntries,
      int estimatedBytesPerEntry,
      int targetEntriesPerChunk) {}

  /** Categorized chunks for processing. */
  private record CategorizedChunks(
      List<UsageMetricRecord> immediate, List<UsageMetricRecord> deferred) {}
}
