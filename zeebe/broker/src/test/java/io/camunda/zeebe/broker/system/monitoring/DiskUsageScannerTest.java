/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DiskUsageScannerTest {

  @TempDir private Path directory;

  @Test
  void shouldSumSizesOfFilesInNestedDirectories() throws IOException {
    // given
    final var partition = Files.createDirectories(directory.resolve("partitions/1"));
    write(partition.resolve("journal-1.log"), 1000);
    write(Files.createDirectories(partition.resolve("runtime")).resolve("000001.sst"), 300);

    // when
    final long used = new DiskUsageScanner(List.of(directory)).getAsLong();

    // then
    assertThat(used).isEqualTo(1300);
  }

  @Test
  void shouldCountHardLinkedFileOnce() throws IOException {
    // given
    final var runtime = Files.createDirectories(directory.resolve("runtime"));
    final var snapshot = Files.createDirectories(directory.resolve("snapshots/1-1-1-1"));
    final var sst = write(runtime.resolve("000001.sst"), 500);
    Files.createLink(snapshot.resolve("000001.sst"), sst);

    // when
    final long used = new DiskUsageScanner(List.of(directory)).getAsLong();

    // then
    assertThat(used).isEqualTo(500);
  }

  @Test
  void shouldSumAcrossRoots() throws IOException {
    // given
    final var data = Files.createDirectories(directory.resolve("data/tenanta"));
    final var runtime = Files.createDirectories(directory.resolve("runtime/tenanta"));
    write(data.resolve("journal-1.log"), 200);
    write(runtime.resolve("000001.sst"), 50);

    // when
    final long used = new DiskUsageScanner(List.of(data, runtime)).getAsLong();

    // then
    assertThat(used).isEqualTo(250);
  }

  @Test
  void shouldReportNothingForMissingRoot() {
    // when
    final long used = new DiskUsageScanner(List.of(directory.resolve("missing"))).getAsLong();

    // then
    assertThat(used).isZero();
  }

  @Test
  void shouldNotFollowSymbolicLinks() throws IOException {
    // given
    final var tenant = Files.createDirectories(directory.resolve("tenanta"));
    final var outside = write(directory.resolve("outside.bin"), 700);
    write(tenant.resolve("journal-1.log"), 100);
    Files.createSymbolicLink(tenant.resolve("link.bin"), outside);

    // when
    final long used = new DiskUsageScanner(List.of(tenant)).getAsLong();

    // then
    assertThat(used).isEqualTo(100);
  }

  private static Path write(final Path file, final int size) throws IOException {
    return Files.write(file, new byte[size]);
  }
}
