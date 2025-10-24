/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.webapps.backup.BackupException.IndexNotFoundException;
import io.camunda.webapps.backup.BackupService.SnapshotRequest;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.backup.repository.SnapshotNameProvider;
import io.camunda.webapps.backup.repository.WebappsSnapshotNameProvider;
import io.camunda.webapps.schema.descriptors.backup.BackupPriorities;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BackupServiceImplTest {

  private static final BackupPriorities DEFAULT_BACKUP_PRIORITIES =
      new BackupPriorities(
          List.of(() -> "prio2"),
          List.of(() -> "prio3"),
          List.of(() -> "prio4"),
          List.of(() -> "prio5"));
  String repositoryName = "test-repository";
  MockBackupRepository backupRepository;

  BackupServiceImpl backupService;
  ExecutorService executor;
  private final BackupRepositoryProps backupRepositoryProps =
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

  public BackupServiceImpl makeBackupService(final BackupPriorities backupPriorities) {
    return new BackupServiceImpl(
        executor, backupPriorities, backupRepositoryProps, backupRepository);
  }

  @BeforeEach
  public void setUp() {
    backupRepository = new MockBackupRepository(repositoryName, new WebappsSnapshotNameProvider());
    executor = Executors.newSingleThreadExecutor();
    backupService = makeBackupService(DEFAULT_BACKUP_PRIORITIES);
  }

  @AfterEach
  public void tearDown() {
    executor.shutdownNow();
  }

  @Test
  public void shouldCreateBackupWithAllIndices() {
    // when
    final var backup = backupService.takeBackup(new TakeBackupRequestDto().setBackupId(1L));

    // then
    assertThat(backup.getScheduledSnapshots())
        .isEqualTo(
            List.of(
                "camunda_webapps_1_8.3_part_1_of_4",
                "camunda_webapps_1_8.3_part_2_of_4",
                "camunda_webapps_1_8.3_part_3_of_4",
                "camunda_webapps_1_8.3_part_4_of_4"));

    Awaitility.await("All backups are done")
        .atMost(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              final var backupState = backupService.getBackupState(1L);
              assertThat(backupState).isNotNull();
              assertThat(backupState.getBackupId()).isEqualTo(1L);
              assertThat(backupState.getState()).isEqualTo(BackupStateDto.COMPLETED);
              assertThat(backupState.getDetails()).hasSize(4);
              assertThat(backupState.getDetails())
                  .allSatisfy(detail -> detail.getState().equals("COMPLETED"));
            });
    final var snapshotRequests = backupRepository.snapshotRequests.get(1L);
    assertThat(snapshotRequests.stream().map(i -> i.indices().allIndices()))
        .containsExactly(List.of("prio2"), List.of("prio3"), List.of("prio4"), List.of("prio5"));
  }

  @Test
  public void shouldReturnIndexNotFoundExceptionIfRequiredIndexIsMissing() {
    // given
    backupRepository.addMissingIndices("prio2", "prio3");
    // when
    assertThatThrownBy(() -> backupService.getValidIndexPatterns())
        // then
        .isInstanceOf(IndexNotFoundException.class)
        // all indices, even if they have different priorities are reported
        .hasMessageContaining("[prio2, prio3]");
  }

  @Test
  public void shouldBeAbleToDeleteASnapshotWithADifferentNumberOfParts() {
    // given
    backupService =
        makeBackupService(
            new BackupPriorities(
                List.of(() -> "prio2"), List.of(() -> "prio3"), List.of(), List.of()));
    // backup #1 is taken with only 3 parts
    final var response = backupService.takeBackup(new TakeBackupRequestDto().setBackupId(1L));
    assertThat(response.getScheduledSnapshots()).hasSize(2);

    // when
    backupService = makeBackupService(DEFAULT_BACKUP_PRIORITIES);
    backupService.deleteBackup(1L);

    // then
    assertThat(backupRepository.backups.get(1L)).isNull();
    // all the snapshot parts created are removed
    assertThat(backupRepository.removedSnasphotNames)
        .contains("camunda_webapps_1_*_part_1_of_2", "camunda_webapps_1_*_part_2_of_2");
  }

  private void waitForAllTasks() {
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
    Set<String> removedSnasphotNames = new HashSet<>();
    Map<Long, Metadata> metadata = new HashMap<>();
    private final String repositoryName;
    private final Set<String> missingIndices = new HashSet<>();

    public MockBackupRepository(
        final String repositoryName, final SnapshotNameProvider snapshotNameProvider) {
      this.repositoryName = repositoryName;
      this.snapshotNameProvider = snapshotNameProvider;
    }

    void addMissingIndices(final String... indices) {
      missingIndices.addAll(Arrays.asList(indices));
    }

    @Override
    public SnapshotNameProvider snapshotNameProvider() {
      return snapshotNameProvider;
    }

    @Override
    public void deleteSnapshot(final String repositoryName, final String snapshotName) {
      validateRepositoryExists(repositoryName);
      removedSnasphotNames.add(snapshotName);
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
    public Optional<Metadata> getMetadata(final String repositoryName, final Long backupId) {
      return Optional.ofNullable(metadata.get(backupId));
    }

    @Override
    public Set<String> checkAllIndicesExist(final List<String> indices) {
      return indices.stream()
          .filter(idx -> !missingIndices.contains(idx))
          .collect(Collectors.toSet());
    }

    @Override
    public List<GetBackupStateResponseDto> getBackups(
        final String repositoryName, final boolean verbose, final String pattern) {
      validateRepositoryExists(repositoryName);
      if (!verbose) {
        // only return minimal information when verbose = false
        return backups.values().stream()
            .map(
                b ->
                    new GetBackupStateResponseDto()
                        .setState(b.getState())
                        .setBackupId(b.getBackupId())
                        .setDetails(
                            b.getDetails().stream()
                                .map(
                                    d ->
                                        new GetBackupStateResponseDetailDto()
                                            .setState(d.getState())
                                            .setSnapshotName(d.getSnapshotName()))
                                .toList()))
            .collect(Collectors.toList());
      }
      return backups.values().stream().toList();
    }

    @Override
    public void executeSnapshotting(
        final SnapshotRequest snapshotRequest, final Runnable onSuccess, final Runnable onFailure) {
      validateRepositoryExists(snapshotRequest.repositoryName());
      final var id = snapshotNameProvider.extractBackupId(snapshotRequest.snapshotName());
      final var backup = backups.getOrDefault(id, new GetBackupStateResponseDto(id));
      snapshotRequests
          .compute(id, (k, v) -> v == null ? new ArrayList<>() : v)
          .add(snapshotRequest);
      metadata.put(snapshotRequest.metadata().backupId(), snapshotRequest.metadata());
      final var startTime = OffsetDateTime.of(2024, 12, 12, 9, 11, 23, 0, ZoneOffset.UTC);
      final ArrayList<GetBackupStateResponseDetailDto> details =
          backup.getDetails() == null ? new ArrayList<>() : new ArrayList<>(backup.getDetails());
      details.add(
          new GetBackupStateResponseDetailDto()
              .setState("SUCCESS")
              .setSnapshotName(snapshotRequest.snapshotName())
              .setStartTime(startTime));
      backup.setDetails(details);
      backup.setState(BackupStateDto.COMPLETED);
      onSuccess.run();
      backups.put(id, backup);
    }
  }
}
