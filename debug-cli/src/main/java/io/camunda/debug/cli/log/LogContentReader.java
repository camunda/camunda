/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.log;

import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.RaftLogReader;
import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import io.camunda.debug.cli.ProcessInstanceRelatedValue;
import io.camunda.zeebe.logstreams.impl.log.LoggedEventImpl;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;
import org.agrona.concurrent.UnsafeBuffer;

// Minimal stub for LogContentReader to allow LogPrintCommand to compile
public class LogContentReader implements Iterator<PersistedRecord> {
  private final RaftLogReader reader;
  private Predicate<PersistedRecord> isInLimit = r -> true;
  private Predicate<ApplicationRecord> applicationRecordFilter = null;
  private PersistedRecord next;
  private boolean hasNextEvaluated = false;
  private boolean hasNextResult = false;

  public LogContentReader(final LogFactory factory, final Path logPath) {
    reader = factory.newReader(logPath);
  }

  @Override
  public boolean hasNext() {
    if (hasNextEvaluated) {
      return hasNextResult;
    }
    while (reader.hasNext()) {
      next = convertToPersistedRecord(reader.next());
      if (!isInLimit.test(next)) {
        hasNextResult = false;
        hasNextEvaluated = true;
        return false;
      }
      if (applicationRecordFilter == null) {
        hasNextResult = true;
        hasNextEvaluated = true;
        return true;
      }
      if (applyFiltering()) {
        hasNextResult = true;
        hasNextEvaluated = true;
        return true;
      }
    }
    hasNextResult = false;
    hasNextEvaluated = true;
    return false;
  }

  @Override
  public PersistedRecord next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    hasNextEvaluated = false;
    return next;
  }

  private boolean applyFiltering() {
    if (next instanceof ApplicationRecord
        && applicationRecordFilter != null
        && applicationRecordFilter.test((ApplicationRecord) next)) {
      return true;
    }
    // skip until next matching record or end
    while (reader.hasNext()) {
      next = convertToPersistedRecord(reader.next());
      if (!isInLimit.test(next)) {
        return false;
      }
      if (next instanceof ApplicationRecord
          && applicationRecordFilter.test((ApplicationRecord) next)) {
        return true;
      }
    }
    return false;
  }

  private PersistedRecord convertToPersistedRecord(final IndexedRaftLogEntry entry) {
    if (entry.isApplicationEntry()) {
      final SerializedApplicationEntry applicationEntry =
          (SerializedApplicationEntry) entry.getApplicationEntry();
      final long highestPosition = applicationEntry.highestPosition();
      final long lowestPosition = applicationEntry.lowestPosition();
      final ApplicationRecord appRecord =
          new ApplicationRecord(entry.index(), entry.term(), highestPosition, lowestPosition);
      final UnsafeBuffer readBuffer = new UnsafeBuffer(applicationEntry.data());
      int offset = 0;
      while (offset < readBuffer.capacity()) {
        final LoggedEventImpl loggedEvent = new LoggedEventImpl();
        final RecordMetadata metadata = new RecordMetadata();
        metadata.reset();
        loggedEvent.wrap(readBuffer, offset);
        loggedEvent.readMetadata(metadata);
        // Convert value buffer to JSON string
        final String valueJson =
            MsgPackConverter.convertToJson(
                new UnsafeBuffer(
                    loggedEvent.getValueBuffer(),
                    loggedEvent.getValueOffset(),
                    loggedEvent.getValueLength()));
        // Parse piRelatedValue if possible from valueJson
        ProcessInstanceRelatedValue piRelatedValue = null;
        try {
          // Try to extract processInstanceKey and bpmnElementType from valueJson
          if (valueJson.contains("processInstanceKey")) {
            // Simple extraction without a full JSON parser for performance and dependency reasons
            Long processInstanceKey = null;
            String bpmnElementType = null;
            Long processDefinitionKey = null;
            int idx = valueJson.indexOf("processInstanceKey");
            if (idx != -1) {
              final int colon = valueJson.indexOf(':', idx);
              final int comma = valueJson.indexOf(',', colon);
              if (colon != -1) {
                final String keyStr =
                    valueJson
                        .substring(colon + 1, comma != -1 ? comma : valueJson.length())
                        .replaceAll("[^0-9]", "")
                        .trim();
                if (!keyStr.isEmpty()) {
                  processInstanceKey = Long.parseLong(keyStr);
                }
              }
            }
            idx = valueJson.indexOf("bpmnElementType");
            if (idx != -1) {
              final int colon = valueJson.indexOf(':', idx);
              final int comma = valueJson.indexOf(',', colon);
              if (colon != -1) {
                final String typeStr =
                    valueJson
                        .substring(colon + 1, comma != -1 ? comma : valueJson.length())
                        .replaceAll("[\"{}]", "")
                        .trim();
                if (!typeStr.isEmpty()) {
                  bpmnElementType = typeStr;
                }
              }
            }
            idx = valueJson.indexOf("processDefinitionKey");
            if (idx != -1) {
              final int colon = valueJson.indexOf(':', idx);
              final int comma = valueJson.indexOf(',', colon);
              if (colon != -1) {
                final String keyStr =
                    valueJson
                        .substring(colon + 1, comma != -1 ? comma : valueJson.length())
                        .replaceAll("[^0-9]", "")
                        .trim();
                if (!keyStr.isEmpty()) {
                  processDefinitionKey = Long.parseLong(keyStr);
                }
              }
            }
            if (processInstanceKey != null
                || bpmnElementType != null
                || processDefinitionKey != null) {
              piRelatedValue =
                  new ProcessInstanceRelatedValue(
                      processInstanceKey, bpmnElementType, processDefinitionKey);
            }
          }
        } catch (final Exception e) {
          // Ignore parse errors, leave piRelatedValue as null
        }
        // Build Record object
        final Record record =
            new Record(
                loggedEvent.getPosition(),
                loggedEvent.getSourceEventPosition(),
                loggedEvent.getTimestamp(),
                loggedEvent.getKey(),
                metadata.getRecordType(),
                metadata.getValueType(),
                metadata.getIntent(),
                metadata.getRejectionType(),
                metadata.getRejectionReason(),
                metadata.getRequestId(),
                metadata.getRequestStreamId(),
                metadata.getProtocolVersion(),
                metadata.getBrokerVersion() != null ? metadata.getBrokerVersion().toString() : null,
                metadata.getRecordVersion(),
                null,
                valueJson,
                piRelatedValue);
        appRecord.entries.add(record);
        offset += loggedEvent.getLength();
      }
      return appRecord;
    } else {
      return new RaftRecord(entry.index(), entry.term());
    }
  }

  public LogContent readAll() {
    final LogContent logContent = new LogContent();
    forEachRemaining(logContent.records::add);
    return logContent;
  }

  public String asDotFile(final String content) {
    // TODO: Implement DOT file conversion if needed
    return content;
  }

  public void seekToPosition(final long position) {
    reader.seekToAsqn(position);
  }

  public void limitToPosition(final long toPosition) {
    isInLimit =
        record ->
            record instanceof RaftRecord
                || (record instanceof ApplicationRecord
                    && ((ApplicationRecord) record).lowestPosition() < toPosition);
  }

  public void filterForProcessInstance(final long instanceKey) {
    applicationRecordFilter =
        record ->
            record.entries.stream()
                .map(Record::piRelatedValue)
                .filter(pi -> pi != null && pi.processInstanceKey != null)
                .anyMatch(pi -> instanceKey == pi.processInstanceKey);
  }
}
