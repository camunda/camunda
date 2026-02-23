/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.subscription;

import io.camunda.exporter.appint.config.BatchConfig;
import io.camunda.exporter.appint.dispatch.Dispatcher;
import io.camunda.exporter.appint.dispatch.DispatcherImpl;
import io.camunda.exporter.appint.mapper.RecordMapper;
import io.camunda.exporter.appint.transport.Transport;
import io.camunda.zeebe.protocol.record.Record;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Subscription<T> {

  private final Logger log = LoggerFactory.getLogger(getClass().getPackageName());
  private final Transport<T> transport;
  private final RecordMapper<T> mapper;
  private final ReentrantLock lock = new ReentrantLock();
  private final Dispatcher dispatcher;
  private final BatchConfig batchConfig;
  private final Consumer<Long> positionConsumer;

  private Batch<T> currentBatch;
  private volatile boolean closed = false;

  public Subscription(
      final Transport<T> transport,
      final RecordMapper<T> mapper,
      final BatchConfig batchConfig,
      final Consumer<Long> positionConsumer) {
    this.transport = transport;
    this.mapper = mapper;
    this.batchConfig = batchConfig;
    this.positionConsumer = positionConsumer;
    if (batchConfig.continueOnError()) {
      log.warn(
          "Subscription is configured to continue on error. This may lead to data loss if errors occur during export.");
    }
    dispatcher = new DispatcherImpl(batchConfig.maxBatchesInFlight());
    currentBatch = new Batch<>(batchConfig.batchSize(), batchConfig.batchIntervalMs());
  }

  public void exportRecord(final Record<?> record) {
    if (closed) {
      throw new IllegalStateException("Cannot export record as subscription is already closed.");
    }
    if (mapper.supports(record)) {
      doWithLock(() -> verifyAndAddToBatch(record));
    } else {
      doWithLock(
          () -> {
            if (hasNoActiveBatch()) {
              positionConsumer.accept(record.getPosition());
            }
          });
    }
  }

  private void doWithLock(final Runnable runnable) {
    lock.lock();
    try {
      runnable.run();
    } finally {
      lock.unlock();
    }
  }

  private void verifyAndAddToBatch(final Record<?> record) {
    if (currentBatch.shouldFlush()) {
      flush();
    }
    if (currentBatch.addRecord(record, this::mapForBatch) && currentBatch.shouldFlush()) {
      flush();
    }
  }

  private T mapForBatch(final Record<?> record) {
    final T mapped = mapper.map(record);
    if (mapped == null) {
      throw new IllegalStateException(
          "Mapper returned null for record at position " + record.getPosition());
    }
    return mapped;
  }

  public void attemptFlush() {
    if (closed) {
      throw new IllegalStateException("Cannot flush subscription as it is already closed.");
    }
    if (lock.tryLock()) {
      try {
        if (currentBatch.shouldFlush()) {
          flush();
        }
      } finally {
        lock.unlock();
      }
    }
  }

  private void flush() {
    try {
      dispatch(currentBatch);
      currentBatch = new Batch<>(batchConfig.batchSize(), batchConfig.batchIntervalMs());
    } catch (final Exception e) {
      log.error("Failed to dispatch batch. Batch will be retained and retried.", e);
      throw e;
    }
  }

  private void dispatch(final Batch<T> batch) {
    final var lastPosition = batch.getLastLogPosition();
    final var entries = batch.getEntries();
    dispatcher.dispatch(
        () -> {
          try {
            transport.send(entries);
          } catch (final Exception e) {
            if (batchConfig.continueOnError()) {
              log.warn(
                  "Error during transport of batch. Will continue with next batch. This may lead to data loss.",
                  e);
            } else {
              log.debug("Error during transport of batch. Will retry with next attempt.", e);
              throw e;
            }
          }
          positionConsumer.accept(lastPosition);
        });
  }

  public Batch<T> getBatch() {
    return currentBatch;
  }

  public void close() {
    log.debug("Closing subscription.");
    doWithLock(
        () -> {
          closed = true;
          dispatcher.close();
          transport.close();
        });
  }

  public boolean hasNoActiveBatch() {
    return currentBatch.isEmpty() && !dispatcher.isActive();
  }
}
