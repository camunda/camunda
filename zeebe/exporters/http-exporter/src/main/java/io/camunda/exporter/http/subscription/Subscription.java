/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.http.subscription;

import io.camunda.zeebe.protocol.record.Record;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Subscription<T> {

  private final Logger log = LoggerFactory.getLogger(getClass().getPackageName());
  private final Transport<T> transport;
  private final RecordMatcher matcher;
  private final Batch<T> batch;
  private final ReentrantLock lock = new ReentrantLock();
  private final boolean continueOnError;

  public Subscription(
      final Transport<T> transport,
      final RecordMatcher matcher,
      final Batch<T> batch,
      final boolean continueOnError) {
    this.transport = transport;
    this.matcher = matcher;
    this.batch = batch;
    this.continueOnError = continueOnError;
    if (continueOnError) {
      log.warn(
          "Subscription is configured to continue on error. This may lead to data loss if errors occur during export.");
    }
  }

  public Long exportRecord(final Record<?> record) {
    if (matcher.matches(record)) {
      // Record matches the filter criteria, we can add it to the batch
      return batchRecord(record);
    } else if (batch.isEmpty()) {
      // An empty batch allows us to save the exported record position
      return record.getPosition();
    } else {
      // Batch has entries, but the record does not match the filter
      // We do not export it, but we return null to indicate no position was pushed
      return null;
    }
  }

  private Long batchRecord(final Record<?> record) {
    lock.lock();
    try {
      return verifyAndAddToBatch(record);
    } finally {
      lock.unlock();
    }
  }

  private Long verifyAndAddToBatch(final Record<?> record) {
    final var spaceLeft = batch.spaceLeft();
    switch (spaceLeft) {
      case 0:
        {
          // We flush the batch as it is full
          final var logPositionFlushed = flush();
          batch.addRecord(record, this::prepareForTransport);
          // We return the last log position that was flushed
          return logPositionFlushed;
        }
      case 1:
        {
          // We add to the batch if it has only one space left
          if (batch.addRecord(record, this::prepareForTransport)) {
            // Flush if the record was added successfully as its full now
            return flush();
          } else {
            // The record was not added, likely because it has an older log position
            return null;
          }
        }
      default:
        {
          // We have space left in the batch, we can add the record
          if (batch.addRecord(record, this::prepareForTransport) && batch.shouldFlush()) {
            // If the record was added successfully and the batch should flush, we flush it
            return flush();
          } else {
            // We return null to indicate no flush was performed
            return null;
          }
        }
    }
  }

  private T prepareForTransport(final Record<?> record) {
    return transport.prepare(record);
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
      transport.send(batch.getEntries());
      lastPosition = batch.flush();
    } catch (final Exception e) {
      if (continueOnError) {
        lastPosition = batch.flush();
        log.debug("Failed to send records. Continuing with last position: {}.", lastPosition, e);
      } else {
        throw new RuntimeException(e);
      }
    }
    return lastPosition;
  }

  public Batch<T> getBatch() {
    return batch;
  }

  public void close() {
    transport.close();
  }
}
