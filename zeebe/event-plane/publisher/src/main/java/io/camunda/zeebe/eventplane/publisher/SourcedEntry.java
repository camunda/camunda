/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.publisher;

import static io.camunda.zeebe.eventplane.segment.Entry.NO_POSITION;

import io.camunda.zeebe.eventplane.segment.Entry;

/**
 * An entry of a journal, with the latest timestamp of its records in epoch milliseconds, or {@link
 * Entry#NO_POSITION} if it holds none. The source knows it; the publisher doesn't decode records.
 */
public record SourcedEntry(Entry entry, long latestRecordTimestamp) {

  public SourcedEntry {
    if (entry.hasRecords() && latestRecordTimestamp < 0) {
      throw new IllegalArgumentException(
          "Expected a latest record timestamp of at least 0 for entry %d, which holds records, but got %d"
              .formatted(entry.index(), latestRecordTimestamp));
    }
    if (!entry.hasRecords() && latestRecordTimestamp != NO_POSITION) {
      throw new IllegalArgumentException(
          "Expected latest record timestamp %d for entry %d, which holds no records, but got %d"
              .formatted(NO_POSITION, entry.index(), latestRecordTimestamp));
    }
  }
}
