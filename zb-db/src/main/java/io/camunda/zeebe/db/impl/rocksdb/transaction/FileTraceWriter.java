/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.rocksdb.AbstractTraceWriter;
import org.rocksdb.RocksDBException;
import org.rocksdb.Slice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileTraceWriter extends AbstractTraceWriter {

  private static final Logger LOG = LoggerFactory.getLogger(FileTraceWriter.class);
  final Path traceFile;

  public FileTraceWriter(final Path traceFile) {
    this.traceFile = traceFile;
  }

  @Override
  public void write(final Slice data) throws RocksDBException {
    try {
      Files.write(traceFile, data.data(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (final IOException e) {
      LOG.error("Failed to write trace file");
    }
  }

  @Override
  public void closeWriter() throws RocksDBException {}

  @Override
  public long getFileSize() {
    try {
      return Files.size(traceFile);
    } catch (final IOException e) {
      return 0;
    }
  }
}
