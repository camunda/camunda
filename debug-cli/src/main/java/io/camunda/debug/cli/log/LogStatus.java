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
import java.io.IOException;
import java.nio.file.Path;

public class LogStatus implements AutoCloseable {
  private final RaftLogReader reader;
  private final LogFactory logFactory = new LogFactory();

  public LogStatus(final Path logPath) throws IOException {
    reader = logFactory.newReader(logPath);
  }

  public LogStatusDetails status() {
    var highestTerm = -1L;
    var highestIndex = -1L;
    var lowestIndex = Long.MAX_VALUE;
    var highestRecordPosition = -1L;
    var lowestRecordPosition = Long.MAX_VALUE;
    var records = 0L;
    while (reader.hasNext()) {
      final IndexedRaftLogEntry entry = (IndexedRaftLogEntry) reader.next();
      highestTerm = Math.max(highestTerm, entry.term());
      highestIndex = Math.max(highestIndex, entry.index());
      lowestIndex = Math.min(lowestIndex, entry.index());
      highestRecordPosition =
          Math.max(highestRecordPosition, entry.getApplicationEntry().highestPosition());
      lowestRecordPosition =
          Math.min(lowestRecordPosition, entry.getApplicationEntry().lowestPosition());
      records++;
    }
    if (records == 0) {
      throw new RuntimeException("No records found in the log");
    }
    return new LogStatusDetails(
        highestTerm,
        highestIndex,
        lowestRecordPosition,
        highestRecordPosition,
        lowestRecordPosition);
  }

  @Override
  public void close() throws Exception {
    logFactory.close();
    reader.close();
  }

  public record LogStatusDetails(
      long highestTerm,
      long highestIndex,
      long lowestIndex,
      long highestRecordPosition,
      long lowestRecordPosition) {
    public LogStatusDetails() {
      this(0, 0, Long.MAX_VALUE, 0, Long.MAX_VALUE);
    }
  }
}
