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

public class Subscription {

  private final ExporterHttpClient exporterHttpClient;
  private final ObjectMapper objectMapper;
  private final String url;
  private final RecordMatcher matcher;
  private final Batch batch;

  public Subscription(
      final ExporterHttpClient exporterHttpClient,
      final ObjectMapper objectMapper,
      final RecordMatcher matcher,
      final String url,
      final Batch batch) {
    this.exporterHttpClient = exporterHttpClient;
    this.url = url;
    this.matcher = matcher;
    this.objectMapper = objectMapper;
    this.batch = batch;
  }

  public Long exportRecord(final Record<?> record) {
    if (matcher.matches(record)) {
      // Record matches the filter criteria, we can add it to the batch
      return batchRecord(new BatchEntry(toJson(record), record.getPosition()));
    } else if (batch.isEmpty()) {
      // An empty batch allows us to save the exported record position
      return record.getPosition();
    } else {
      // We cant save the position
      return null;
    }
  }

  private Long batchRecord(final BatchEntry batchEntry) {
    synchronized (batch) {
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
  }

  public Long attemptFlush() {
    synchronized (batch) {
      if (batch.shouldFlush()) {
        return flush();
      }
      return null;
    }
  }

  private Long flush() {
    postRecords(batch.getEntries());
    return batch.flush();
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
