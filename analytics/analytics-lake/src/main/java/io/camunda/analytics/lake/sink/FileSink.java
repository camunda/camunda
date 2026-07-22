/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink;

import org.apache.iceberg.io.OutputFile;

/**
 * Where data files physically go. Deliberately thin over Iceberg's own IO abstraction so encoder
 * implementations hand the {@link OutputFile} straight to the format library: local filesystem now
 * (see {@code io.camunda.analytics.lake.write.LocalFileIO}), S3 multipart later — swapping the sink
 * must never touch encoder or pipeline code.
 *
 * <p>Crash contract: files are worthless until their descriptor is committed — on restart,
 * incomplete or unreferenced files are abandoned (orphan sweep / S3 lifecycle rule), never resumed.
 * Retries re-send bytes from reused buffers, never re-encode.
 */
public interface FileSink {

  /**
   * @param relativePath data-file path relative to the table's data location, e.g. {@code
   *     day=2026-07-22/f-p0-000481.parquet}
   */
  OutputFile newOutputFile(TableSchema schema, String relativePath);
}
