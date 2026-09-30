/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FileExporterTest {

  private static final Duration FLUSH_INTERVAL = Duration.ofSeconds(1);

  @TempDir private Path directory;

  private final ExporterTestController controller = new ExporterTestController();
  private final FileExporter exporter = new FileExporter();

  @Test
  void shouldWriteEveryRecordAsJsonLineIntoPartitionDirectory() throws IOException {
    // given
    open(new FileExporterConfiguration().setDirectory(directory.toString()), "tenant-a", 3);

    // when
    exporter.export(record(1, "{\"position\":1}"));
    exporter.export(record(2, "{\"position\":2}"));
    exporter.close();

    // then
    final var partitionDirectory = directory.resolve("tenant-a").resolve("partition-3");
    assertThat(readAllLines(partitionDirectory))
        .containsExactly("{\"position\":1}", "{\"position\":2}");
  }

  @Test
  void shouldAcknowledgePositionOnlyOnceFlushed() {
    // given
    open(new FileExporterConfiguration().setDirectory(directory.toString()), "tenant", 1);
    final var positionBeforeExport = controller.getPosition();

    // when
    exporter.export(record(5, "{}"));

    // then
    assertThat(controller.getPosition()).isEqualTo(positionBeforeExport);
    controller.runScheduledTasks(FLUSH_INTERVAL);
    assertThat(controller.getPosition()).isEqualTo(5);
  }

  @Test
  void shouldRollFileWhenMaxFileSizeIsReached() throws IOException {
    // given
    open(
        new FileExporterConfiguration().setDirectory(directory.toString()).setMaxFileSizeBytes(1),
        "tenant",
        1);

    // when
    exporter.export(record(10, "{\"position\":10}"));
    exporter.export(record(11, "{\"position\":11}"));
    exporter.close();

    // then
    final var partitionDirectory = directory.resolve("tenant").resolve("partition-1");
    try (final Stream<Path> files = Files.list(partitionDirectory)) {
      assertThat(files.map(file -> file.getFileName().toString()))
          .containsExactlyInAnyOrder(
              "records-00000000000000000010.ndjson", "records-00000000000000000011.ndjson");
    }
    assertThat(readAllLines(partitionDirectory))
        .containsExactly("{\"position\":10}", "{\"position\":11}");
    assertThat(controller.getPosition()).isEqualTo(11);
  }

  @Test
  void shouldRejectMissingDirectory() {
    // given
    final var context = context(new FileExporterConfiguration(), "tenant", 1);

    // when / then
    assertThatThrownBy(() -> exporter.configure(context))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("directory");
  }

  private void open(
      final FileExporterConfiguration configuration,
      final String physicalTenantId,
      final int partitionId) {
    exporter.configure(
        context(configuration.setFlushInterval(FLUSH_INTERVAL), physicalTenantId, partitionId));
    exporter.open(controller);
  }

  private static ExporterTestContext context(
      final FileExporterConfiguration configuration,
      final String physicalTenantId,
      final int partitionId) {
    return new ExporterTestContext()
        .setConfiguration(new ExporterTestConfiguration<>("file", configuration))
        .setPhysicalTenantId(physicalTenantId)
        .setPartitionId(partitionId);
  }

  private static Record<?> record(final long position, final String json) {
    final Record<?> record = mock(Record.class);
    when(record.getPosition()).thenReturn(position);
    when(record.toJson()).thenReturn(json);
    return record;
  }

  private static List<String> readAllLines(final Path partitionDirectory) throws IOException {
    try (final Stream<Path> files = Files.list(partitionDirectory)) {
      return files.sorted().flatMap(FileExporterTest::readLines).toList();
    }
  }

  private static Stream<String> readLines(final Path file) {
    try {
      return Files.readAllLines(file).stream();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
