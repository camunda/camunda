/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecordLogger {
  public static final String STYLE_PROPERTY = "RECORD_LOGGER_STYLE";
  public static final String STYLE_RAW = "RAW";
  public static final Logger LOG = LoggerFactory.getLogger("io.camunda.zeebe.test");

  public static void logRecords() {
    LOG.info("Test failed, following records were exported:");
    if (STYLE_RAW.equals(System.getenv(STYLE_PROPERTY))) {
      logRecordsRaw(RecordingExporter.getRecords());
    } else {
      logRecordsCompact(RecordingExporter.getRecords());
    }
  }

  public static void logRecordsCompact(final Collection<Record<?>> records) {
    if (!records.isEmpty()) {
      new CompactRecordLogger(records).log();
    }
  }

  public static void logRecordsRaw(final Collection<Record<?>> records) {
    records.forEach(r -> LOG.info(r.toString()));
  }
}
