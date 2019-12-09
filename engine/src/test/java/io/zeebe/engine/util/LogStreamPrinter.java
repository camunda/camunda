/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util;

import static io.zeebe.engine.processor.TypedEventRegistry.EVENT_REGISTRY;

import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.util.SynchronousLogStream;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.util.ReflectUtil;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogStreamPrinter {

  private static final String HEADER_INDENTATION = "\t\t\t";
  private static final String ENTRY_INDENTATION = HEADER_INDENTATION + "\t";

  private static final Logger LOGGER = LoggerFactory.getLogger("io.zeebe.broker.test");

  public static void printRecords(final SynchronousLogStream logStream) {
    final StringBuilder sb = new StringBuilder();
    sb.append("Records on partition ");
    sb.append(logStream.getPartitionId());
    sb.append(":\n");

    final EnumMap<ValueType, UnpackedObject> eventCache = new EnumMap<>(ValueType.class);
    EVENT_REGISTRY.forEach((t, c) -> eventCache.put(t, ReflectUtil.newInstance(c)));

    try (LogStreamReader streamReader = logStream.newLogStreamReader()) {
      streamReader.seekToFirstEvent();

      while (streamReader.hasNext()) {
        final LoggedEvent event = streamReader.next();

        writeRecord(eventCache, event, sb);
      }
    }

    LOGGER.info(sb.toString());
  }

  private static void writeRecord(
      final Map<ValueType, UnpackedObject> eventCache,
      final LoggedEvent event,
      final StringBuilder sb) {
    sb.append(HEADER_INDENTATION);
    writeRecordHeader(event, sb);
    sb.append("\n");
    final RecordMetadata metadata = new RecordMetadata();
    event.readMetadata(metadata);
    sb.append(ENTRY_INDENTATION);
    writeMetadata(metadata, sb);
    sb.append("\n");

    final UnpackedObject unpackedObject = eventCache.get(metadata.getValueType());
    event.readValue(unpackedObject);
    sb.append(ENTRY_INDENTATION).append("Value:\n");
    unpackedObject.writeJSON(sb);
    sb.append("\n");
  }

  private static void writeRecordHeader(final LoggedEvent event, final StringBuilder sb) {
    sb.append("Position: ");
    sb.append(event.getPosition());
    sb.append(" Key: ");
    sb.append(event.getKey());
  }

  private static void writeMetadata(final RecordMetadata metadata, final StringBuilder sb) {
    sb.append(metadata.toString());
  }
}
