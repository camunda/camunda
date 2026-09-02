/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.state.migration.DbMigrationState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.encoding.MigrationStatusCode;
import io.camunda.zeebe.protocol.impl.encoding.PartitionMigrationStatus;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.util.VersionUtil;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ZeebePartitionAdminAccessTest {

  private final PartitionAdminControl adminControl = mock(PartitionAdminControl.class);
  private final ZeebePartitionAdminAccess sut =
      new ZeebePartitionAdminAccess(new TestConcurrencyControl(), 1, adminControl);

  @Test
  void shouldCompleteResumeProcessingOnlyAfterStreamProcessorResumes() throws IOException {
    // given
    final var streamProcessor = mock(StreamProcessor.class);
    final CompletableActorFuture<Void> processorResume = new CompletableActorFuture<>();
    when(adminControl.getStreamProcessor()).thenReturn(streamProcessor);
    when(adminControl.shouldProcess()).thenReturn(true);
    when(streamProcessor.resumeProcessing()).thenReturn(processorResume);

    // when
    final ActorFuture<Void> resumed = sut.resumeProcessing();

    // then
    verify(adminControl).resumeProcessing();
    assertThat(resumed).isNotDone();

    // when
    processorResume.complete(null);

    // then
    assertThat(resumed).succeedsWithin(Duration.ofSeconds(5));
  }

  /**
   * Uses a real, on-disk ZeebeDb since {@link ZeebePartitionAdminAccess#getMigrationStatus()} opens
   * a second transaction context on the live db, the same technique already used for banning
   * instances -- mocking ZeebeDb itself would not exercise that path.
   */
  @Nested
  final class MigrationStatus {

    @TempDir private File dbDirectory;
    private ZeebeDb<ZbColumnFamilies> zeebeDb;

    @BeforeEach
    void openDb() throws Exception {
      zeebeDb = DefaultZeebeDbFactory.<ZbColumnFamilies>defaultFactory().createDb(dbDirectory);
      when(adminControl.getZeebeDb()).thenReturn(zeebeDb);
    }

    @AfterEach
    void closeDb() throws Exception {
      zeebeDb.close();
    }

    @Test
    void shouldReportMigratedWhenVersionMatchesAndSnapshotTaken() {
      // given
      writeMigratedByVersion(VersionUtil.getVersion());
      when(adminControl.isMigrationSnapshotTaken()).thenReturn(true);

      // when
      final var status = sut.getMigrationStatus().join();

      // then
      assertThat(status.code()).isEqualTo(MigrationStatusCode.MIGRATED);
    }

    @Test
    void shouldReportMigrationInProgressWhenVersionMatchesButSnapshotNotYetTaken() {
      // given
      writeMigratedByVersion(VersionUtil.getVersion());
      when(adminControl.isMigrationSnapshotTaken()).thenReturn(false);

      // when
      final var status = sut.getMigrationStatus().join();

      // then
      assertThat(status.code()).isEqualTo(MigrationStatusCode.MIGRATION_IN_PROGRESS);
    }

    @Test
    void shouldReportMigrationInProgressWhenNoVersionRecordedYet() {
      // given - nothing written, fresh partition

      // when
      final var status = sut.getMigrationStatus().join();

      // then
      assertThat(status.code()).isEqualTo(MigrationStatusCode.MIGRATION_IN_PROGRESS);
      assertThat(status.detail()).contains("no migrated-by-version");
    }

    @Test
    void shouldReportUnknownWhenZeebeDbIsNotYetOpen() {
      // given
      when(adminControl.getZeebeDb()).thenReturn(null);

      // when
      final var status = sut.getMigrationStatus().join();

      // then
      assertThat(status.code()).isEqualTo(MigrationStatusCode.UNKNOWN);
    }

    @Test
    void shouldSeeFreshDataOnRepeatedReadsInsteadOfOpeningANewContextEachTime() {
      // given - readMigrationStatus() reuses one transaction context across calls (see
      // ZeebePartitionAdminAccess#migrationState) instead of leaking a fresh one on every read;
      // this must not mean later reads see stale data
      when(adminControl.isMigrationSnapshotTaken()).thenReturn(true);
      assertThat(sut.getMigrationStatus().join().code())
          .isEqualTo(MigrationStatusCode.MIGRATION_IN_PROGRESS);

      // when - the version is written after the first read, through a different transaction
      writeMigratedByVersion(VersionUtil.getVersion());

      // then - the reused context still picks up the newly committed value
      assertThat(sut.getMigrationStatus().join().code()).isEqualTo(MigrationStatusCode.MIGRATED);
    }

    private void writeMigratedByVersion(final String version) {
      new DbMigrationState(zeebeDb, zeebeDb.createContext()).setMigratedByVersion(version);
    }
  }

  /**
   * The actual algorithm lives on {@link ExporterDirector} itself (it needs the partition's {@code
   * LogStream}, not the {@code ZeebeDb}), so {@code ZeebePartitionAdminAccess}'s own responsibility
   * here is pure delegation -- a mocked {@link ExporterDirector} is enough to verify it.
   */
  @Nested
  final class ExportingMigrationStatus {

    @Test
    void shouldDelegateToTheExporterDirector() {
      // given
      final var exporterDirector = mock(ExporterDirector.class);
      final var expectedStatus =
          new PartitionMigrationStatus(MigrationStatusCode.MIGRATED, "caught up");
      when(adminControl.getExporterDirector()).thenReturn(exporterDirector);
      when(exporterDirector.getExportingMigrationStatus())
          .thenReturn(CompletableActorFuture.completed(expectedStatus));

      // when
      final var status = sut.getExportingMigrationStatus().join();

      // then
      assertThat(status).isEqualTo(expectedStatus);
    }

    @Test
    void shouldReportUnknownWhenNoExporterDirectorRunningYet() {
      // given
      when(adminControl.getExporterDirector()).thenReturn(null);

      // when
      final var status = sut.getExportingMigrationStatus().join();

      // then
      assertThat(status.code()).isEqualTo(MigrationStatusCode.UNKNOWN);
      assertThat(status.detail()).contains("no exporter director running");
    }

    @Test
    void shouldReportUnknownRatherThanHangWhenReadingTheStatusThrows() {
      // given - a synchronous failure, e.g. before the exporter director's own future is even
      // returned, must still complete this method's future rather than leave it hanging forever
      when(adminControl.getExporterDirector())
          .thenThrow(new RuntimeException("Exporter director lookup fails"));

      // when
      final var status = sut.getExportingMigrationStatus().join();

      // then
      assertThat(status.code()).isEqualTo(MigrationStatusCode.UNKNOWN);
      assertThat(status.detail()).contains("Exporter director lookup fails");
    }
  }
}
