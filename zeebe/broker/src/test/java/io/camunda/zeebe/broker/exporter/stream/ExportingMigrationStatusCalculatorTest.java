/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.MigrationStatusCode;
import org.junit.jupiter.api.Test;

final class ExportingMigrationStatusCalculatorTest {

  private static final int PARTITION_ID = 1;

  @Test
  void shouldReportMigratedWhenNoExportersConfigured() {
    // when
    final var status = ExportingMigrationStatusCalculator.compute(PARTITION_ID, false, "8.9.0");

    // then
    assertThat(status.code()).isEqualTo(MigrationStatusCode.MIGRATED);
  }

  @Test
  void shouldReportMigratedWhenCaughtUpToTheLogHead() {
    // when - null means no next unexported record at all
    final var status = ExportingMigrationStatusCalculator.compute(PARTITION_ID, true, null);

    // then
    assertThat(status.code()).isEqualTo(MigrationStatusCode.MIGRATED);
  }

  @Test
  void shouldReportMigratedWhenNextRecordIsOnTheCurrentVersion() {
    // when
    final var status =
        ExportingMigrationStatusCalculator.compute(PARTITION_ID, true, "8.10.0", "8.10.0");

    // then
    assertThat(status.code()).isEqualTo(MigrationStatusCode.MIGRATED);
  }

  @Test
  void shouldReportMigratedWhenNextRecordIsOnlyAPatchBehind() {
    // given - a patch release never changes the on-disk/wire record format, so a record one or
    // more patches behind is not a "previous version" backlog either
    final var status =
        ExportingMigrationStatusCalculator.compute(PARTITION_ID, true, "8.10.0", "8.10.3");

    // then
    assertThat(status.code()).isEqualTo(MigrationStatusCode.MIGRATED);
  }

  @Test
  void shouldReportMigratedWhenNextRecordIsOnlyAPatchAhead() {
    // given - the record was stamped by a newer patch than the one now running, e.g. right after
    // a patch rollback
    final var status =
        ExportingMigrationStatusCalculator.compute(PARTITION_ID, true, "8.10.3", "8.10.1");

    // then
    assertThat(status.code()).isEqualTo(MigrationStatusCode.MIGRATED);
  }

  @Test
  void shouldReportMigratedWhenTheCurrentVersionHasAPreReleaseSuffix() {
    // given - the running version's "-SNAPSHOT" suffix must not make an otherwise-identical
    // version look incompatible
    final var status =
        ExportingMigrationStatusCalculator.compute(PARTITION_ID, true, "8.10.0", "8.10.0-SNAPSHOT");

    // then
    assertThat(status.code()).isEqualTo(MigrationStatusCode.MIGRATED);
  }

  @Test
  void shouldReportMigrationInProgressWhenOnAnOlderMinorEvenWithAPreReleaseSuffix() {
    // given - the same asymmetry, but on a genuine minor-version gap: it must still be reported
    // as in progress, not swallowed by stripping the suffix
    final var status =
        ExportingMigrationStatusCalculator.compute(PARTITION_ID, true, "8.9.0", "8.10.0-SNAPSHOT");

    // then
    assertThat(status.code()).isEqualTo(MigrationStatusCode.MIGRATION_IN_PROGRESS);
  }

  @Test
  void shouldNameOnlyTheMinorVersionInEveryDetailMessage() {
    // when
    final var status =
        ExportingMigrationStatusCalculator.compute(PARTITION_ID, true, "8.10.3", "8.10.5-SNAPSHOT");

    // then
    assertThat(status.detail()).contains("8.10").doesNotContain("8.10.3", "8.10.5", "SNAPSHOT");
  }

  @Test
  void shouldReportMigrationInProgressWhenNextRecordIsOnAnOlderMinorVersion() {
    // when
    final var status =
        ExportingMigrationStatusCalculator.compute(PARTITION_ID, true, "8.9.0", "8.10.0");

    // then
    assertThat(status.code()).isEqualTo(MigrationStatusCode.MIGRATION_IN_PROGRESS);
    assertThat(status.detail()).contains("8.9").contains("8.10").doesNotContain("8.9.0", "8.10.0");
  }

  @Test
  void shouldReportUnknownWhenNextRecordVersionSkipsAMinorVersion() {
    // when - an illegal upgrade path (skipping a minor) is treated the same as any other
    // incompatibility: we cannot confidently claim readiness
    final var status =
        ExportingMigrationStatusCalculator.compute(PARTITION_ID, true, "8.9.0", "8.11.0");

    // then
    assertThat(status.code()).isEqualTo(MigrationStatusCode.UNKNOWN);
  }

  @Test
  void shouldReportUnknownWhenNextRecordVersionIsAheadOfTheCurrentVersion() {
    // when - as if this broker had been downgraded; never a false "ready"
    final var status =
        ExportingMigrationStatusCalculator.compute(PARTITION_ID, true, "8.11.0", "8.10.0");

    // then
    assertThat(status.code()).isEqualTo(MigrationStatusCode.UNKNOWN);
  }

  @Test
  void shouldReportUnknownWhenTheCurrentVersionCannotBeParsed() {
    // when
    final var status =
        ExportingMigrationStatusCalculator.compute(PARTITION_ID, true, "8.9.0", "development");

    // then
    assertThat(status.code()).isEqualTo(MigrationStatusCode.UNKNOWN);
  }
}
