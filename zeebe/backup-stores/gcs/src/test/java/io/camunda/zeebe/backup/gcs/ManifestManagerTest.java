/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.gcs;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.cloud.storage.Storage.BlobTargetOption;
import com.google.cloud.storage.StorageException;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.BackupStoreException.UnexpectedManifestState;
import io.camunda.zeebe.backup.common.Manifest;
import io.camunda.zeebe.backup.common.Manifest.StatusCode;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

final class ManifestManagerTest {
  @Test
  void shouldCreateInitialManifest() {
    // given
    final var client = Mockito.mock(Storage.class);
    final var manager =
        new ManifestManager(
            client, BucketInfo.of("bucket"), "basePath", Executors.newSingleThreadExecutor());
    final var backup =
        new BackupImpl(
            new BackupIdentifierImpl(1, 2, 3),
            new BackupDescriptorImpl(1, 1, "version", Instant.now(), CheckpointType.MANUAL_BACKUP),
            new NamedFileSetImpl(
                Map.of("snapshotFile1", Path.of("file1"), "snapshotFile2", Path.of("file2"))),
            new NamedFileSetImpl(Map.of("segmentFile1", Path.of("file3"))));
    final var expected = Manifest.createInProgress(backup);

    final var blob = Mockito.mock(Blob.class);
    Mockito.when(blob.getGeneration()).thenReturn(1L);
    Mockito.when(
            client.create(Mockito.any(BlobInfo.class), Mockito.any(byte[].class), Mockito.any()))
        .thenReturn(blob);

    // when
    final var persisted = manager.createInitialManifest(backup);

    // then
    Assertions.assertThat(persisted.generation()).isEqualTo(1L);
    Assertions.assertThat(persisted.manifest())
        .usingRecursiveComparison()
        .ignoringFields("modifiedAt", "createdAt")
        .isEqualTo(expected);
  }

  @Test
  void shouldCompleteManifest() throws IOException {
    // given
    final var client = Mockito.mock(Storage.class);
    final var manager =
        new ManifestManager(
            client, BucketInfo.of("bucket"), "basePath", Executors.newSingleThreadExecutor());
    final var backup =
        new BackupImpl(
            new BackupIdentifierImpl(1, 2, 3),
            new BackupDescriptorImpl(1, 1, "version", Instant.now(), CheckpointType.MANUAL_BACKUP),
            new NamedFileSetImpl(
                Map.of("snapshotFile1", Path.of("file1"), "snapshotFile2", Path.of("file2"))),
            new NamedFileSetImpl(Map.of("segmentFile1", Path.of("file3"))));
    final var expectedManifest = Manifest.createInProgress(backup).complete();

    final var blob = Mockito.mock(Blob.class);
    Mockito.when(blob.getGeneration()).thenReturn(1L);
    Mockito.when(
            client.create(Mockito.any(BlobInfo.class), Mockito.any(byte[].class), Mockito.any()))
        .thenReturn(blob);
    final var persisted = manager.createInitialManifest(backup);

    // when
    manager.completeManifest(persisted);

    // then
    final var captor = ArgumentCaptor.forClass(byte[].class);
    Mockito.verify(client)
        .create(
            Mockito.any(BlobInfo.class),
            captor.capture(),
            Mockito.eq(BlobTargetOption.generationMatch(persisted.generation())));

    final var actualManifest = ManifestManager.MAPPER.readValue(captor.getValue(), Manifest.class);
    Assertions.assertThat(actualManifest)
        .usingRecursiveComparison()
        .ignoringFields("modifiedAt", "createdAt")
        .isEqualTo(expectedManifest);
    Assertions.assertThat(actualManifest.createdAt()).isEqualTo(persisted.manifest().createdAt());
  }

  @Test
  void shouldThrowWhenManifestAlreadyExists() {
    // given
    final var client = Mockito.mock(Storage.class);
    final var manager =
        new ManifestManager(
            client, BucketInfo.of("bucket"), "basePath", Executors.newSingleThreadExecutor());
    final var backup =
        new BackupImpl(
            new BackupIdentifierImpl(1, 2, 3),
            new BackupDescriptorImpl(1, 1, "version", Instant.now(), CheckpointType.MANUAL_BACKUP),
            new NamedFileSetImpl(Map.of()),
            new NamedFileSetImpl(Map.of()));

    // when
    Mockito.when(
            client.create(Mockito.any(BlobInfo.class), Mockito.any(byte[].class), Mockito.any()))
        .thenThrow(new StorageException(412, "expected"));

    // then
    Assertions.assertThatThrownBy(() -> manager.createInitialManifest(backup))
        .isInstanceOf(UnexpectedManifestState.class)
        .hasMessageContaining("but already exists");
  }

  @Test
  void shouldThrowWhenUnexpectedStorageExceptionOccurs() {
    // given
    final var client = Mockito.mock(Storage.class);
    final var manager =
        new ManifestManager(
            client, BucketInfo.of("bucket"), "basePath", Executors.newSingleThreadExecutor());
    final var backup =
        new BackupImpl(
            new BackupIdentifierImpl(1, 2, 3),
            new BackupDescriptorImpl(1, 1, "version", Instant.now(), CheckpointType.MANUAL_BACKUP),
            new NamedFileSetImpl(Map.of()),
            new NamedFileSetImpl(Map.of()));

    // when
    Mockito.when(
            client.create(Mockito.any(BlobInfo.class), Mockito.any(byte[].class), Mockito.any()))
        .thenThrow(new StorageException(500, "expected but unhandled"));

    // then
    Assertions.assertThatThrownBy(() -> manager.createInitialManifest(backup))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("expected but unhandled");
  }

  @Test
  void shouldThrowWhenManifestChangedBeforeCompletion() {
    // given
    final var client = Mockito.mock(Storage.class);
    final var manager =
        new ManifestManager(
            client, BucketInfo.of("bucket"), "basePath", Executors.newSingleThreadExecutor());
    final var backup =
        new BackupImpl(
            new BackupIdentifierImpl(1, 2, 3),
            new BackupDescriptorImpl(1, 1, "version", Instant.now(), CheckpointType.MANUAL_BACKUP),
            new NamedFileSetImpl(Map.of()),
            new NamedFileSetImpl(Map.of()));

    final var blob = Mockito.mock(Blob.class);
    Mockito.when(blob.getGeneration()).thenReturn(1L);

    Mockito.when(
            client.create(Mockito.any(BlobInfo.class), Mockito.any(byte[].class), Mockito.any()))
        .thenReturn(blob);

    final var persisted = manager.createInitialManifest(backup);

    // when
    Mockito.when(
            client.create(Mockito.any(BlobInfo.class), Mockito.any(byte[].class), Mockito.any()))
        .thenThrow(new StorageException(412, "expected"));

    // then
    Assertions.assertThatThrownBy(() -> manager.completeManifest(persisted))
        .isInstanceOf(UnexpectedManifestState.class)
        .hasMessageContaining("modification was detected");
  }

  @Test
  void shouldThrowWhenCompletingManifestThrowsUnexpectedStorageException() {
    // given
    final var client = Mockito.mock(Storage.class);
    final var manager =
        new ManifestManager(
            client, BucketInfo.of("bucket"), "basePath", Executors.newSingleThreadExecutor());
    final var backup =
        new BackupImpl(
            new BackupIdentifierImpl(1, 2, 3),
            new BackupDescriptorImpl(1, 1, "version", Instant.now(), CheckpointType.MANUAL_BACKUP),
            new NamedFileSetImpl(Map.of()),
            new NamedFileSetImpl(Map.of()));

    final var blob = Mockito.mock(Blob.class);
    Mockito.when(blob.getGeneration()).thenReturn(1L);

    Mockito.when(
            client.create(Mockito.any(BlobInfo.class), Mockito.any(byte[].class), Mockito.any()))
        .thenReturn(blob);

    final var persisted = manager.createInitialManifest(backup);

    // when
    Mockito.when(
            client.create(Mockito.any(BlobInfo.class), Mockito.any(byte[].class), Mockito.any()))
        .thenThrow(new StorageException(500, "expected but unhandled"));

    // then
    Assertions.assertThatThrownBy(() -> manager.completeManifest(persisted))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("expected but unhandled");
  }

  @Test
  void shouldRetryListBackupStatusesWhenNonHttpIoStorageExceptionOccurs() {
    // given
    final var client = Mockito.mock(Storage.class);
    final var manager =
        new ManifestManager(
            client, BucketInfo.of("bucket"), "basePath", Executors.newSingleThreadExecutor());
    final var backup =
        new BackupImpl(
            new BackupIdentifierImpl(1, 2, 3),
            new BackupDescriptorImpl(1, 1, "version", Instant.now(), CheckpointType.MANUAL_BACKUP),
            new NamedFileSetImpl(Map.of()),
            new NamedFileSetImpl(Map.of()));
    final var manifest = Manifest.createInProgress(backup).complete();
    final var blob = Mockito.mock(Blob.class);
    Mockito.when(blob.getName()).thenReturn("basePathmanifests/2/3/1/manifest.json");
    Mockito.when(blob.getMetadata()).thenReturn(ManifestMetadata.fromManifest(manifest));
    final var page = mockBlobPage(blob);
    Mockito.when(client.list(Mockito.eq("bucket"), Mockito.any(BlobListOption[].class)))
        .thenThrow(
            new StorageException(
                new IOException("universe domain metadata request failed while listing manifests")))
        .thenReturn(page);
    final var wildcard =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.empty(), BackupIdentifierWildcard.CheckpointPattern.any());

    // when
    final var statuses = manager.listBackupStatuses(wildcard);

    // then
    Assertions.assertThat(statuses)
        .singleElement()
        .satisfies(
            status -> {
              Assertions.assertThat(status.id()).isEqualTo(backup.id());
              Assertions.assertThat(status.statusCode()).isEqualTo(BackupStatusCode.COMPLETED);
            });
    Mockito.verify(client, Mockito.times(2))
        .list(Mockito.eq("bucket"), Mockito.any(BlobListOption[].class));
  }

  @Test
  void shouldRetryOnlyFailedManifestPage() {
    // given
    final var client = Mockito.mock(Storage.class);
    final var manager =
        new ManifestManager(
            client, BucketInfo.of("bucket"), "basePath", Executors.newSingleThreadExecutor());
    final var firstBackup =
        new BackupImpl(
            new BackupIdentifierImpl(1, 2, 3),
            new BackupDescriptorImpl(1, 1, "version", Instant.now(), CheckpointType.MANUAL_BACKUP),
            new NamedFileSetImpl(Map.of()),
            new NamedFileSetImpl(Map.of()));
    final var secondBackup =
        new BackupImpl(
            new BackupIdentifierImpl(1, 2, 4),
            new BackupDescriptorImpl(1, 1, "version", Instant.now(), CheckpointType.MANUAL_BACKUP),
            new NamedFileSetImpl(Map.of()),
            new NamedFileSetImpl(Map.of()));
    final var firstBlob = manifestBlob(firstBackup);
    final var secondBlob = manifestBlob(secondBackup);
    final var firstPage = mockBlobPage(List.of(firstBlob), "page-2");
    final var secondPage = mockBlobPage(List.of(secondBlob), null);
    Mockito.when(client.list(Mockito.eq("bucket"), Mockito.any(BlobListOption[].class)))
        .thenReturn(firstPage)
        .thenThrow(
            new StorageException(
                new IOException("universe domain metadata request failed while listing manifests")))
        .thenReturn(secondPage);
    final var wildcard =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.empty(), BackupIdentifierWildcard.CheckpointPattern.any());

    // when
    final var statuses = manager.listBackupStatuses(wildcard);

    // then
    Assertions.assertThat(statuses)
        .extracting(status -> status.id())
        .containsExactlyInAnyOrder(firstBackup.id(), secondBackup.id());
    final ArgumentCaptor<BlobListOption[]> optionsCaptor =
        ArgumentCaptor.forClass(BlobListOption[].class);
    Mockito.verify(client, Mockito.times(3)).list(Mockito.eq("bucket"), optionsCaptor.capture());
    final var requests = optionsCaptor.getAllValues();
    Assertions.assertThat(requests.get(0))
        .contains(BlobListOption.prefix("basePathmanifests/"), BlobListOption.pageSize(1000))
        .doesNotContain(BlobListOption.pageToken("page-2"));
    Assertions.assertThat(requests.get(1))
        .contains(
            BlobListOption.prefix("basePathmanifests/"),
            BlobListOption.pageSize(1000),
            BlobListOption.pageToken("page-2"));
    Assertions.assertThat(requests.get(2)).containsExactlyInAnyOrder(requests.get(1));
  }

  @Test
  void shouldLimitConcurrentLegacyManifestReads() throws Exception {
    // given
    final var client = Mockito.mock(Storage.class);
    final var executor =
        Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("manifest-list-test-", 0).factory());
    try {
      final var manager =
          new ManifestManager(client, BucketInfo.of("bucket"), "basePath", executor);
      final var backup =
          new BackupImpl(
              new BackupIdentifierImpl(1, 2, 3),
              new BackupDescriptorImpl(
                  1, 1, "version", Instant.now(), CheckpointType.MANUAL_BACKUP),
              new NamedFileSetImpl(Map.of()),
              new NamedFileSetImpl(Map.of()));
      final var manifestBytes =
          ManifestManager.MAPPER.writeValueAsBytes(Manifest.createInProgress(backup).complete());
      final var blobs =
          IntStream.range(0, 17)
              .mapToObj(
                  checkpointId -> {
                    final var blob = Mockito.mock(Blob.class);
                    final var blobId =
                        BlobId.of(
                            "bucket",
                            "basePathmanifests/2/%d/1/manifest.json".formatted(checkpointId));
                    Mockito.when(blob.getName()).thenReturn(blobId.getName());
                    Mockito.when(blob.getBlobId()).thenReturn(blobId);
                    return blob;
                  })
              .toList();
      final var page = mockBlobPage(blobs, null);
      Mockito.when(client.list(Mockito.eq("bucket"), Mockito.any(BlobListOption[].class)))
          .thenReturn(page);
      final var concurrentReads = new AtomicInteger();
      final var maximumConcurrentReads = new AtomicInteger();
      final var releaseReads = new CountDownLatch(1);
      final var readsStarted = new CountDownLatch(16);
      Mockito.when(client.readAllBytes(Mockito.any(BlobId.class)))
          .thenAnswer(
              ignored -> {
                maximumConcurrentReads.accumulateAndGet(
                    concurrentReads.incrementAndGet(), Math::max);
                readsStarted.countDown();
                try {
                  if (!releaseReads.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release manifest reads");
                  }
                  return manifestBytes;
                } finally {
                  concurrentReads.decrementAndGet();
                }
              });
      final var wildcard =
          new BackupIdentifierWildcardImpl(
              Optional.empty(), Optional.empty(), BackupIdentifierWildcard.CheckpointPattern.any());

      // when
      final var listing = CompletableFuture.supplyAsync(() -> manager.listBackupStatuses(wildcard));

      // then
      try {
        Assertions.assertThat(readsStarted.await(5, TimeUnit.SECONDS)).isTrue();
        Assertions.assertThat(concurrentReads).hasValue(16);
      } finally {
        releaseReads.countDown();
      }
      Assertions.assertThat(listing.get(10, TimeUnit.SECONDS)).hasSize(17);
      Assertions.assertThat(maximumConcurrentReads).hasValue(16);
    } finally {
      executor.close();
    }
  }

  @Test
  void shouldRetryListBackupStatusesWhenNonHttpIoStorageExceptionIsWrapped() {
    // given
    final var client = Mockito.mock(Storage.class);
    final var manager =
        new ManifestManager(
            client, BucketInfo.of("bucket"), "basePath", Executors.newSingleThreadExecutor());
    final var backup =
        new BackupImpl(
            new BackupIdentifierImpl(1, 2, 3),
            new BackupDescriptorImpl(1, 1, "version", Instant.now(), CheckpointType.MANUAL_BACKUP),
            new NamedFileSetImpl(Map.of()),
            new NamedFileSetImpl(Map.of()));
    final var manifest = Manifest.createInProgress(backup).complete();
    final var blob = Mockito.mock(Blob.class);
    Mockito.when(blob.getName()).thenReturn("basePathmanifests/2/3/1/manifest.json");
    Mockito.when(blob.getMetadata()).thenReturn(ManifestMetadata.fromManifest(manifest));
    final var page = mockBlobPage(blob);
    Mockito.when(client.list(Mockito.eq("bucket"), Mockito.any(BlobListOption[].class)))
        .thenThrow(
            new RuntimeException(
                new StorageException(
                    new IOException(
                        "universe domain metadata request failed while listing manifests"))))
        .thenReturn(page);
    final var wildcard =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.empty(), BackupIdentifierWildcard.CheckpointPattern.any());

    // when
    final var statuses = manager.listBackupStatuses(wildcard);

    // then
    Assertions.assertThat(statuses)
        .singleElement()
        .satisfies(
            status -> {
              Assertions.assertThat(status.id()).isEqualTo(backup.id());
              Assertions.assertThat(status.statusCode()).isEqualTo(BackupStatusCode.COMPLETED);
            });
    Mockito.verify(client, Mockito.times(2))
        .list(Mockito.eq("bucket"), Mockito.any(BlobListOption[].class));
  }

  @Test
  void shouldNotRetryListBackupStatusesWhenStorageExceptionIsNotIoBacked() {
    // given
    final var client = Mockito.mock(Storage.class);
    final var manager =
        new ManifestManager(
            client, BucketInfo.of("bucket"), "basePath", Executors.newSingleThreadExecutor());
    Mockito.when(client.list(Mockito.eq("bucket"), Mockito.any(BlobListOption[].class)))
        .thenThrow(new StorageException(0, "not an I/O failure"));
    final var wildcard =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.empty(), BackupIdentifierWildcard.CheckpointPattern.any());

    // when/then
    Assertions.assertThatThrownBy(() -> manager.listBackupStatuses(wildcard))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("not an I/O failure");
    Mockito.verify(client).list(Mockito.eq("bucket"), Mockito.any(BlobListOption[].class));
  }

  @Test
  void shouldNotLoopWhenListBackupStatusesStorageExceptionCauseChainCycles() {
    // given
    final var client = Mockito.mock(Storage.class);
    final var manager =
        new ManifestManager(
            client, BucketInfo.of("bucket"), "basePath", Executors.newSingleThreadExecutor());
    Mockito.when(client.list(Mockito.eq("bucket"), Mockito.any(BlobListOption[].class)))
        .thenThrow(new StorageException(0, "cyclic cause", new CyclicCauseException()));
    final var wildcard =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.empty(), BackupIdentifierWildcard.CheckpointPattern.any());

    // when/then
    org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
        Duration.ofSeconds(5),
        () ->
            Assertions.assertThatThrownBy(() -> manager.listBackupStatuses(wildcard))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("cyclic cause"));
    Mockito.verify(client).list(Mockito.eq("bucket"), Mockito.any(BlobListOption[].class));
  }

  @Test
  void shouldNotRetryListBackupStatusesWhenErrorIsNotTransient() {
    // given
    final var client = Mockito.mock(Storage.class);
    final var manager =
        new ManifestManager(
            client, BucketInfo.of("bucket"), "basePath", Executors.newSingleThreadExecutor());
    Mockito.when(client.list(Mockito.eq("bucket"), Mockito.any(BlobListOption[].class)))
        .thenThrow(new StorageException(400, "bad request"));
    final var wildcard =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.empty(), BackupIdentifierWildcard.CheckpointPattern.any());

    // when/then
    Assertions.assertThatThrownBy(() -> manager.listBackupStatuses(wildcard))
        .isInstanceOf(StorageException.class)
        .hasMessageContaining("bad request");
    Mockito.verify(client).list(Mockito.eq("bucket"), Mockito.any(BlobListOption[].class));
  }

  @Test
  void shouldNotLoopForeverWhenRetryCauseChainIsCyclic() {
    // given
    final var client = Mockito.mock(Storage.class);
    final var manager =
        new ManifestManager(
            client, BucketInfo.of("bucket"), "basePath", Executors.newSingleThreadExecutor());
    final var wildcard =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.empty(), BackupIdentifierWildcard.CheckpointPattern.any());

    final var firstCause = new Exception("first cause");
    final var secondCause = new Exception("second cause");
    firstCause.initCause(secondCause);
    secondCause.initCause(firstCause);

    Mockito.when(client.list(Mockito.eq("bucket"), Mockito.any(BlobListOption[].class)))
        .thenThrow(new RuntimeException("cyclic failure", firstCause));

    // when / then
    org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
        Duration.ofSeconds(5),
        () -> {
          Assertions.assertThatThrownBy(() -> manager.listBackupStatuses(wildcard))
              .isInstanceOf(RuntimeException.class)
              .hasMessageContaining("cyclic failure");
        });
    Mockito.verify(client).list(Mockito.eq("bucket"), Mockito.any(BlobListOption[].class));
  }

  private static Page<Blob> mockBlobPage(final Blob blob) {
    return mockBlobPage(List.of(blob), null);
  }

  @SuppressWarnings("unchecked")
  private static Page<Blob> mockBlobPage(final List<Blob> blobs, final String nextPageToken) {
    final var page = Mockito.mock(Page.class);
    Mockito.when(page.getValues()).thenReturn(blobs);
    Mockito.when(page.hasNextPage()).thenReturn(nextPageToken != null);
    Mockito.when(page.getNextPageToken()).thenReturn(nextPageToken);
    return page;
  }

  private static Blob manifestBlob(final BackupImpl backup) {
    final var manifest = Manifest.createInProgress(backup).complete();
    final var blob = Mockito.mock(Blob.class);
    Mockito.when(blob.getName())
        .thenReturn(
            "basePathmanifests/%d/%d/%s/manifest.json"
                .formatted(
                    backup.id().partitionId(),
                    backup.id().checkpointId(),
                    backup.id().brokerId().id()));
    Mockito.when(blob.getMetadata()).thenReturn(ManifestMetadata.fromManifest(manifest));
    return blob;
  }

  private static final class CyclicCauseException extends RuntimeException {

    @Override
    public synchronized Throwable getCause() {
      return this;
    }
  }

  @Nested
  class ManifestDeleteTransitionTest {
    @Test
    void shouldMarkInProgressManifestAsDeleted() throws IOException {
      // given
      final var client = Mockito.mock(Storage.class);
      final var manager =
          new ManifestManager(
              client, BucketInfo.of("bucket"), "basePath", Executors.newSingleThreadExecutor());
      final var backup =
          new BackupImpl(
              new BackupIdentifierImpl(1, 2, 3),
              new BackupDescriptorImpl(
                  1, 1, "version", Instant.now(), CheckpointType.MANUAL_BACKUP),
              new NamedFileSetImpl(Map.of()),
              new NamedFileSetImpl(Map.of()));
      final var inProgressManifest = Manifest.createInProgress(backup);

      final var blob = Mockito.mock(Blob.class);
      Mockito.when(blob.getContent())
          .thenReturn(ManifestManager.MAPPER.writeValueAsBytes(inProgressManifest));
      Mockito.when(client.get(Mockito.any(BlobId.class))).thenReturn(blob);

      // when
      manager.markAsDeleted(inProgressManifest);

      // then
      final var captor = ArgumentCaptor.forClass(byte[].class);
      Mockito.verify(client).create(Mockito.any(BlobInfo.class), captor.capture());

      final var actualManifest =
          ManifestManager.MAPPER.readValue(captor.getValue(), Manifest.class);
      Assertions.assertThat(actualManifest.statusCode()).isEqualTo(StatusCode.DELETED);
      Assertions.assertThat(actualManifest.id()).isEqualTo(inProgressManifest.id());
    }

    @Test
    void shouldMarkCompletedManifestAsDeleted() throws IOException {
      // given
      final var client = Mockito.mock(Storage.class);
      final var manager =
          new ManifestManager(
              client, BucketInfo.of("bucket"), "basePath", Executors.newSingleThreadExecutor());
      final var backup =
          new BackupImpl(
              new BackupIdentifierImpl(1, 2, 3),
              new BackupDescriptorImpl(
                  1, 1, "version", Instant.now(), CheckpointType.MANUAL_BACKUP),
              new NamedFileSetImpl(Map.of()),
              new NamedFileSetImpl(Map.of()));
      final var completedManifest = Manifest.createInProgress(backup).complete();

      final var blob = Mockito.mock(Blob.class);
      Mockito.when(blob.getContent())
          .thenReturn(ManifestManager.MAPPER.writeValueAsBytes(completedManifest));
      Mockito.when(client.get(Mockito.any(BlobId.class))).thenReturn(blob);

      // when
      manager.markAsDeleted(completedManifest);

      // then
      final var captor = ArgumentCaptor.forClass(byte[].class);
      Mockito.verify(client).create(Mockito.any(BlobInfo.class), captor.capture());

      final var actualManifest =
          ManifestManager.MAPPER.readValue(captor.getValue(), Manifest.class);
      Assertions.assertThat(actualManifest.statusCode()).isEqualTo(StatusCode.DELETED);
      Assertions.assertThat(actualManifest.id()).isEqualTo(completedManifest.id());
    }

    @Test
    void shouldMarkFailedManifestAsDeleted() throws IOException {
      // given
      final var client = Mockito.mock(Storage.class);
      final var manager =
          new ManifestManager(
              client, BucketInfo.of("bucket"), "basePath", Executors.newSingleThreadExecutor());
      final var backup =
          new BackupImpl(
              new BackupIdentifierImpl(1, 2, 3),
              new BackupDescriptorImpl(
                  1, 1, "version", Instant.now(), CheckpointType.MANUAL_BACKUP),
              new NamedFileSetImpl(Map.of()),
              new NamedFileSetImpl(Map.of()));
      final var failedManifest = Manifest.createInProgress(backup).fail("failure reason");

      final var blob = Mockito.mock(Blob.class);
      Mockito.when(blob.getContent())
          .thenReturn(ManifestManager.MAPPER.writeValueAsBytes(failedManifest));
      Mockito.when(client.get(Mockito.any(BlobId.class))).thenReturn(blob);

      // when
      manager.markAsDeleted(failedManifest);

      // then
      final var captor = ArgumentCaptor.forClass(byte[].class);
      Mockito.verify(client).create(Mockito.any(BlobInfo.class), captor.capture());

      final var actualManifest =
          ManifestManager.MAPPER.readValue(captor.getValue(), Manifest.class);
      Assertions.assertThat(actualManifest.statusCode()).isEqualTo(StatusCode.DELETED);
      Assertions.assertThat(actualManifest.id()).isEqualTo(failedManifest.id());
    }

    @Test
    void shouldNotUpdateAlreadyDeletedManifest() throws IOException {
      // given
      final var client = Mockito.mock(Storage.class);
      final var manager =
          new ManifestManager(
              client, BucketInfo.of("bucket"), "basePath", Executors.newSingleThreadExecutor());
      final var backup =
          new BackupImpl(
              new BackupIdentifierImpl(1, 2, 3),
              new BackupDescriptorImpl(
                  1, 1, "version", Instant.now(), CheckpointType.MANUAL_BACKUP),
              new NamedFileSetImpl(Map.of()),
              new NamedFileSetImpl(Map.of()));
      final var deletedManifest = Manifest.createInProgress(backup).complete().delete();

      final var blob = Mockito.mock(Blob.class);
      Mockito.when(blob.getContent())
          .thenReturn(ManifestManager.MAPPER.writeValueAsBytes(deletedManifest));
      Mockito.when(client.get(Mockito.any(BlobId.class))).thenReturn(blob);

      // when
      manager.markAsDeleted(deletedManifest);

      // then - should not call create since manifest is already deleted
      Mockito.verify(client, Mockito.never()).create(Mockito.any(BlobInfo.class), Mockito.any());
    }
  }
}
