/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.file;

import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Writes every record as one JSON line into size-rolled files, one directory per physical tenant
 * and partition: {@code
 * <directory>/<physicalTenantId>/partition-<id>/records-<firstPosition>.ndjson}.
 *
 * <p>A position is only acknowledged once it has been flushed, so delivery is at-least-once: after
 * a restart, records written but not yet acknowledged are written again. Deduplicate on {@code
 * (partitionId, position)} when reading the files.
 */
@NullMarked
public class FileExporter implements Exporter {

  private static final String FILE_PREFIX = "records-";
  private static final String FILE_SUFFIX = ".ndjson";

  private @Nullable Logger log;
  private @Nullable FileExporterConfiguration config;
  private @Nullable Path partitionDirectory;
  private @Nullable Controller controller;

  private @Nullable Writer writer;
  private long currentFileSize;
  private long lastWrittenPosition = -1;
  private long lastAcknowledgedPosition = -1;

  @Override
  public void configure(final Context context) {
    log = context.getLogger();
    final var configuration =
        context.getConfiguration().instantiate(FileExporterConfiguration.class);
    final var directory = configuration.getDirectory();
    if (directory == null || directory.isBlank()) {
      throw new IllegalArgumentException("File exporter requires a non-empty 'directory' argument");
    }
    if (configuration.getMaxFileSizeBytes() <= 0) {
      throw new IllegalArgumentException(
          "File exporter requires a positive 'maxFileSizeBytes', but got "
              + configuration.getMaxFileSizeBytes());
    }
    if (configuration.getFlushInterval().isNegative()
        || configuration.getFlushInterval().isZero()) {
      throw new IllegalArgumentException(
          "File exporter requires a positive 'flushInterval', but got "
              + configuration.getFlushInterval());
    }

    config = configuration;
    partitionDirectory =
        Path.of(directory)
            .resolve(context.getPhysicalTenantId())
            .resolve("partition-" + context.getPartitionId());
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
    try {
      Files.createDirectories(partitionDirectory());
    } catch (final IOException e) {
      throw new UncheckedIOException(
          "Failed to create export directory " + partitionDirectory(), e);
    }
    logger().info("Exporting records as NDJSON to {} with {}", partitionDirectory(), config());
    scheduleFlush();
  }

  @Override
  public void close() {
    try {
      flushAndAcknowledge();
      closeWriter();
    } catch (final IOException e) {
      logger().warn("Failed to flush and close the export file on close", e);
    }
  }

  @Override
  public void export(final Record<?> record) {
    final var line = record.toJson() + '\n';
    try {
      if (writer == null || currentFileSize >= config().getMaxFileSizeBytes()) {
        rollFile(record.getPosition());
      }
      currentWriter().write(line);
    } catch (final IOException e) {
      throw new UncheckedIOException(
          "Failed to write record at position " + record.getPosition(), e);
    }
    // Counts chars rather than encoded bytes; close enough to decide when to roll the file.
    currentFileSize += line.length();
    lastWrittenPosition = record.getPosition();
  }

  private void rollFile(final long firstPosition) throws IOException {
    flushAndAcknowledge();
    closeWriter();

    final var file =
        partitionDirectory().resolve(FILE_PREFIX + "%020d".formatted(firstPosition) + FILE_SUFFIX);
    // Append, as a retried record after an IO error or a restart can land on an existing file.
    writer =
        Files.newBufferedWriter(
            file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    currentFileSize = Files.size(file);
  }

  private void flushAndAcknowledge() throws IOException {
    if (writer == null || lastWrittenPosition == lastAcknowledgedPosition) {
      return;
    }
    writer.flush();
    controller().updateLastExportedRecordPosition(lastWrittenPosition);
    lastAcknowledgedPosition = lastWrittenPosition;
  }

  private void closeWriter() throws IOException {
    if (writer != null) {
      writer.close();
      writer = null;
    }
  }

  private void flushAndReschedule() {
    try {
      flushAndAcknowledge();
    } catch (final IOException e) {
      logger().warn("Failed to flush the export file, will retry on the next flush", e);
    } finally {
      scheduleFlush();
    }
  }

  private void scheduleFlush() {
    controller().scheduleCancellableTask(config().getFlushInterval(), this::flushAndReschedule);
  }

  private Writer currentWriter() {
    if (writer == null) {
      throw new IllegalStateException("Expected an open export file");
    }
    return writer;
  }

  private FileExporterConfiguration config() {
    if (config == null) {
      throw new IllegalStateException("Expected the exporter to be configured");
    }
    return config;
  }

  private Path partitionDirectory() {
    if (partitionDirectory == null) {
      throw new IllegalStateException("Expected the exporter to be configured");
    }
    return partitionDirectory;
  }

  private Controller controller() {
    if (controller == null) {
      throw new IllegalStateException("Expected the exporter to be opened");
    }
    return controller;
  }

  private Logger logger() {
    if (log == null) {
      throw new IllegalStateException("Expected the exporter to be configured");
    }
    return log;
  }
}
