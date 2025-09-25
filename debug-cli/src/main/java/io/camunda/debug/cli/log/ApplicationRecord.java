/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.log;

import java.util.ArrayList;
import java.util.List;

public class ApplicationRecord implements PersistedRecord {
  public final List<Record> entries = new ArrayList<>();
  private final long lowestPosition;
  private final long index;
  private final long term;
  private final long highestPosition;

  public ApplicationRecord(
      final long index, final long term, final long highestPosition, final long lowestPosition) {
    this.index = index;
    this.term = term;
    this.highestPosition = highestPosition;
    this.lowestPosition = lowestPosition;
  }

  public long lowestPosition() {
    return lowestPosition;
  }

  @Override
  public long index() {
    return index;
  }

  @Override
  public long term() {
    return term;
  }

  @Override
  public String asColumnString() {
    final String prefix = index + " " + term + " ";
    final StringBuilder sb = new StringBuilder();
    for (final Record r : entries) {
      sb.append(prefix).append(entryAsColumn(r)).append(System.lineSeparator());
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    final var sb = new StringBuilder(128);

    sb.append(
        String.format(
            "{\"index\":%d, \"term\":%d,\"highestPosition\":%d,\"lowestPosition\":%d,\"entries\":[",
            index, term, highestPosition, lowestPosition));
    var isFirst = true;
    for (final Record r : entries) {
      if (!isFirst) {
        sb.append(',');
        sb.append(System.lineSeparator());
      }
      isFirst = false;
      sb.append(r.toString());
    }
    sb.append("]}");
    return sb.toString();
  }

  public String entryAsColumn(final Record record) {
    final StringBuilder sb = new StringBuilder();
    final String sep = " ";
    sb.append(record.position())
        .append(sep)
        .append(record.sourceRecordPosition())
        .append(sep)
        .append(record.timestamp())
        .append(sep)
        .append(record.key())
        .append(sep)
        .append(record.recordType())
        .append(sep)
        .append(record.valueType())
        .append(sep)
        .append(record.intent());
    if (record.piRelatedValue() != null) {
      if (record.piRelatedValue().processInstanceKey != null) {
        sb.append(sep).append(record.piRelatedValue().processInstanceKey).append(sep);
      }
      if (record.piRelatedValue().bpmnElementType != null) {
        sb.append(record.piRelatedValue().bpmnElementType).append(sep);
      }
    }
    return sb.toString();
  }
}
