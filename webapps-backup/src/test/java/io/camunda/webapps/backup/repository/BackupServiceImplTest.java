/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.webapps.backup.BackupRepository;
import io.camunda.webapps.backup.BackupService;
import io.camunda.webapps.backup.BackupService.SnapshotRequest;
import io.camunda.webapps.backup.BackupServiceImpl;
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.webapps.backup.DynamicIndicesProvider;
import io.camunda.webapps.backup.GetBackupStateResponseDetailDto;
import io.camunda.webapps.backup.GetBackupStateResponseDto;
import io.camunda.webapps.backup.TakeBackupRequestDto;
import io.camunda.webapps.schema.descriptors.backup.BackupPriorities;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BackupServiceImplTest {

  String repositoryName = "test-repository";
  MockBackupRepository backupRepository;

  BackupService backupService;
  ExecutorService executor;
  BackupPriorities backupPriorities;
  BackupRepositoryProps backupRepositoryProps =
      new BackupRepositoryProps() {
        @Override
        public String version() {
          return "8.3";
        }

        @Override
        public String repositoryName() {
          return repositoryName;
        }
      };
  @Mock DynamicIndicesProvider dynamicIndicesProvider;

  @BeforeEach
  public void setUp() {
    backupRepository = new MockBackupRepository(repositoryName, new TestSnapshotProvider());
    executor = Executors.newSingleThreadExecutor();
    backupPriorities =
        new BackupPriorities(
            List.of(() -> "prio1"),
            List.of(() -> "prio2"),
            List.of(() -> "prio3"),
            List.of(() -> "prio4"),
            List.of(() -> "prio5"),
            List.of(() -> "prio6"));
    backupService =
        new BackupServiceImpl(
            executor,
            backupPriorities,
            backupRepositoryProps,
            backupRepository,
            dynamicIndicesProvider);
  }

  @AfterEach
  public void tearDown() {
    executor.shutdownNow();
  }

  @Test
  public void shouldCreateBackupWithAllIndices() throws ExecutionException, InterruptedException {
    when(dynamicIndicesProvider.getAllDynamicIndices()).thenReturn(List.of("dynamic1", "dynamic2"));
    final var backup = backupService.takeBackup(new TakeBackupRequestDto().setBackupId(1L));

    waitCompletion();
    assertThat(backup.getScheduledSnapshots())
        .isEqualTo(
            List.of(
                "test_snapshot_1_0_8",
                "test_snapshot_1_1_8",
                "test_snapshot_1_2_8",
                "test_snapshot_1_3_8",
                "test_snapshot_1_4_8",
                "test_snapshot_1_5_8",
                "test_snapshot_1_6_8",
                "test_snapshot_1_7_8"));
    final var backupState = backupService.getBackupState(1L);
    assertThat(backupState).isNotNull();
    assertThat(backupState.getBackupId()).isEqualTo(1L);
    assertThat(backupState.getState()).isEqualTo(BackupStateDto.COMPLETED);
    final var snapshotRequests = backupRepository.snapshotRequests.get(1L);
    assertThat(snapshotRequests.stream().map(i -> i.indices().allIndices()))
        .containsExactly(
            List.of("prio1"),
            List.of("prio2"),
            // no templates in the test
            List.of(),
            List.of("prio3"),
            List.of("prio4"),
            // no templates in the test
            List.of(),
            List.of("prio5"),
            List.of("prio6", "dynamic1", "dynamic2"));
  }

  private void waitCompletion() {
    try {
      // wait for the executor to process all tasks
      executor.submit(() -> {}).get();
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private static class MockBackupRepository implements BackupRepository {

    SnapshotNameProvider snapshotNameProvider;
    // From backupId
    Map<Long, GetBackupStateResponseDto> backups = new HashMap<>();
    Map<Long, List<SnapshotRequest>> snapshotRequests = new HashMap<>();
    private final String repositoryName;

    public MockBackupRepository(
        final String repositoryName, final SnapshotNameProvider snapshotNameProvider) {
      this.repositoryName = repositoryName;
      this.snapshotNameProvider = snapshotNameProvider;
    }

    @Override
    public SnapshotNameProvider snapshotNameProvider() {
      return snapshotNameProvider;
    }

    @Override
    public void deleteSnapshot(final String repositoryName, final String snapshotName) {
      validateRepositoryExists(repositoryName);
      backups.remove(snapshotNameProvider.extractBackupId(snapshotName));
    }

    @Override
    public void validateRepositoryExists(final String repositoryName) {
      assertThat(this.repositoryName).isEqualTo(repositoryName);
    }

    @Override
    public void validateNoDuplicateBackupId(final String repositoryName, final Long backupId) {
      assertThat(backups).doesNotContainKey(backupId);
    }

    @Override
    public GetBackupStateResponseDto getBackupState(
        final String repositoryName, final Long backupId) {
      validateRepositoryExists(repositoryName);
      return backups.get(backupId);
    }

    @Override
    public List<GetBackupStateResponseDto> getBackups(final String repositoryName) {
      validateRepositoryExists(repositoryName);
      return backups.values().stream().toList();
    }

    @Override
    public void executeSnapshotting(
        final SnapshotRequest snapshotRequest,
        final boolean onlyRequired,
        final Runnable onSuccess,
        final Runnable onFailure) {
      validateRepositoryExists(snapshotRequest.repositoryName());
      final var id = snapshotNameProvider.extractBackupId(snapshotRequest.snapshotName());
      final var backup = backups.getOrDefault(id, new GetBackupStateResponseDto(id));
      snapshotRequests
          .compute(id, (k, v) -> v == null ? new ArrayList<>() : v)
          .add(snapshotRequest);

      final var startTime = OffsetDateTime.of(2024, 12, 12, 9, 11, 23, 0, ZoneOffset.UTC);
      backup.setDetails(
          List.of(
              new GetBackupStateResponseDetailDto()
                  .setState("SUCCESS")
                  .setSnapshotName(snapshotRequest.snapshotName())
                  .setStartTime(startTime)));
      backup.setState(BackupStateDto.COMPLETED);
      onSuccess.run();
      backups.put(id, backup);
    }
  }
}
