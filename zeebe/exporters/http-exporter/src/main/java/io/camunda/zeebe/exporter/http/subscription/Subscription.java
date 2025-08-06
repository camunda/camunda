/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.http.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.RawValue;
import io.camunda.zeebe.exporter.http.client.ExporterHttpClient;
import io.camunda.zeebe.exporter.http.matcher.RecordMatcher;
import io.camunda.zeebe.protocol.record.Record;
import java.util.List;

public class Subscription {

  private final ExporterHttpClient exporterHttpClient;
  private final String url;
  private final RecordMatcher matcher;
  private final Batch batch;
  private final ObjectMapper objectMapper;

  public Subscription(
      final ExporterHttpClient exporterHttpClient,
      final String url,
      final RecordMatcher matcher,
      final ObjectMapper objectMapper,
      final Batch batch) {
    this.exporterHttpClient = exporterHttpClient;
    this.url = url;
    this.matcher = matcher;
    this.objectMapper = objectMapper;
    this.batch = batch;
  }

  public Long exportRecord(final Record<?> record) {
    final var recordJson = toJson(record);
    final var recordPosition = record.getPosition();
    final var batchEntry = new BatchEntry(recordJson, recordPosition);
    if (matcher.matches(record, recordJson)) {
      return batchRecord(batchEntry);
    }
    return null;
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
}
