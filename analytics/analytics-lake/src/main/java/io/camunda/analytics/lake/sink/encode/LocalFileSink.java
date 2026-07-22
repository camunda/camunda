/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.encode;

import io.camunda.analytics.lake.sink.FileSink;
import io.camunda.analytics.lake.sink.TableSchema;
import io.camunda.analytics.lake.write.LocalFileIO;
import java.nio.file.Path;
import org.apache.iceberg.io.OutputFile;

/**
 * {@link FileSink} over a local directory tree: {@code <baseDir>/<table>/data/<relativePath>},
 * mirroring the {@code <table.location()>/data/<fileName>} convention {@code IcebergLakeWriter}
 * already uses for the DuckDB-written raw tables, so both write paths land under the same kind of
 * layout. Delegates the actual {@code file:} URI / plain-path reconciliation to {@link LocalFileIO}
 * rather than reimplementing it (see that class's javadoc for why it has to exist at all).
 */
public final class LocalFileSink implements FileSink {

  private final Path baseDir;
  private final LocalFileIO fileIO = new LocalFileIO();

  public LocalFileSink(final Path baseDir) {
    this.baseDir = baseDir;
  }

  @Override
  public OutputFile newOutputFile(final TableSchema schema, final String relativePath) {
    return fileIO.newOutputFile(tableDataLocation(schema.table()) + "/" + relativePath);
  }

  private String tableDataLocation(final String table) {
    final String uri = baseDir.resolve(table).resolve("data").toAbsolutePath().toUri().toString();
    return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
  }
}
