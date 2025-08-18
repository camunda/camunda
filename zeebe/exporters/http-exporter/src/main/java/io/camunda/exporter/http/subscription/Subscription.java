/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.http.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.RawValue;
import io.camunda.exporter.http.client.ExporterHttpClient;
import io.camunda.exporter.http.matcher.RecordMatcher;
import io.camunda.zeebe.protocol.record.Record;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Subscription {

  private final Logger log = LoggerFactory.getLogger(getClass().getPackageName());
  private final ExporterHttpClient exporterHttpClient;
  private final ObjectMapper objectMapper;
  private final String url;
  private final RecordMatcher matcher;
  private final Batch batch;
  private final ReentrantLock lock = new ReentrantLock();
  private final boolean continueOnError;

  public Subscription(
      final ExporterHttpClient exporterHttpClient,
      final ObjectMapper objectMapper,
      final RecordMatcher matcher,
      final String url,
      final Batch batch,
      final boolean continueOnError) {
    this.exporterHttpClient = exporterHttpClient;
    this.url = url;
    this.matcher = matcher;
    this.objectMapper = objectMapper;
    this.batch = batch;
    this.continueOnError = continueOnError;
    if (continueOnError) {
      log.warn(
          "Subscription to {} is configured to continue on error. This may lead to data loss if errors occur during export.",
          url);
    }
  }

  public Long exportRecord(final Record<?> record) {
    if (matcher.matches(record)) {
      // Record matches the filter criteria, we can add it to the batch
      return batchRecord(new BatchEntry(toJson(record), record.getPosition()));
    } else if (batch.isEmpty()) {
      // An empty batch allows us to save the exported record position
      return record.getPosition();
    } else {
      // Batch has entries, but the record does not match the filter
      // We do not export it, but we return null to indicate no position was pushed
      return null;
    }
  }

  private Long batchRecord(final BatchEntry batchEntry) {
    lock.lock();
    try {
      return verifyAndAddToBatch(batchEntry);
    } finally {
      lock.unlock();
    }
  }

  private Long verifyAndAddToBatch(final BatchEntry batchEntry) {
    final var spaceLeft = batch.spaceLeft();
    switch (spaceLeft) {
      case 0:
        {
          // We flush the batch as it is full
          final var logPositionPushed = flush();
          batch.addRecord(batchEntry);
          return logPositionPushed;
        }
      case 1:
        {
          // We add to the batch if it has only one space left
          if (batch.addRecord(batchEntry)) {
            // Flush if the record was added successfully as its full now
            return flush();
          } else {
            return null;
          }
        }
      default:
        {
          batch.addRecord(batchEntry);
          return null;
        }
    }
  }

  public Long attemptFlush() {
    if (lock.tryLock()) {
      if (batch.shouldFlush()) {
        try {
          return flush();
        } finally {
          lock.unlock();
        }
      }
    }
    return null;
  }

  private Long flush() {
    long lastPosition;
    try {
      postRecords(batch.getEntries());
      lastPosition = batch.flush();
    } catch (final Exception e) {
      if (continueOnError) {
        lastPosition = batch.flush();
        log.debug(
            "Failed to post records to {}. Continuing with last position: {}.",
            url,
            lastPosition,
            e);
      } else {
        throw new RuntimeException(e);
      }
    }
    return lastPosition;
  }

  private void postRecords(final List<BatchEntry> batchEntries) {
    final var json = toJson(toRawValues(batchEntries));
    exporterHttpClient.postRecords(url, json);
  }

  private List<RawValue> toRawValues(final List<BatchEntry> batchEntries) {
    return batchEntries.stream().map(batchEntry -> new RawValue(batchEntry.record())).toList();
  }

  private String toJson(final Object object) {
    try {
      return objectMapper.writeValueAsString(object);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public Batch getBatch() {
    return batch;
  }

  public void close() {
    exporterHttpClient.close();
  }
}
