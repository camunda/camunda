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
    return String.format(
        "{\"index\":%d, \"term\":%d,\"highestPosition\":%d,\"lowestPosition\":%d,\"entries\":[%s]}",
        index, term, highestPosition, lowestPosition, entriesAsJson());
  }

  public String entriesAsJson() {
    return String.join(",", entries.stream().map(Record::toString).toArray(String[]::new));
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
