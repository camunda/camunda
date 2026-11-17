/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.PagedResponseBase;
import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.ListBlobsOptions;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.ListOptions;
import io.camunda.zeebe.backup.common.BackupDescriptorImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.common.BackupImpl;
import io.camunda.zeebe.backup.common.Manifest;
import io.camunda.zeebe.backup.common.NamedFileSetImpl;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link ManifestManager#listManifests} reads manifests concurrently, bounded by
 * {@link ManifestManager#MANIFEST_READ_PARALLELISM}, rather than one blocking download at a time.
 * Uses a real (but backend-less) {@link PagedIterable} and per-blob mocked {@link BlobClient}s
 * instead of a live Azurite container, so the concurrency bound can be observed directly.
 */
final class ManifestManagerReadConcurrencyTest {

  @Test
  void shouldReadManifestsConcurrently() throws Exception {
    // given - twice as many manifests as may be read at once, each blocked until all are selected
    final var blobContainerClient = mock(BlobContainerClient.class);
    final var manager =
        new ManifestManager(
            blobContainerClient,
            false,
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("test-", 0).factory()));
    final var manifestCount = 2 * ManifestManager.MANIFEST_READ_PARALLELISM;
    final var manifests =
        IntStream.range(0, manifestCount)
            .mapToObj(checkpointId -> manifest(new BackupIdentifierImpl(1, 2, checkpointId)))
            .toList();
    final var blobItems =
        manifests.stream()
            .map(m -> new BlobItem().setName(ManifestManager.manifestPath(m)))
            .toList();
    when(blobContainerClient.listBlobs(any(ListBlobsOptions.class), any()))
        .thenReturn(pagedIterableOf(blobItems));

    final var running = new AtomicInteger();
    final var maxRunning = new AtomicInteger();
    final var permittedStarted = new CountDownLatch(ManifestManager.MANIFEST_READ_PARALLELISM);
    final var release = new CountDownLatch(1);
    for (final var manifest : manifests) {
      final var path = ManifestManager.manifestPath(manifest);
      final var blobClient = mock(BlobClient.class);
      when(blobContainerClient.getBlobClient(path)).thenReturn(blobClient);
      when(blobClient.downloadContent())
          .thenAnswer(
              invocation -> {
                maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                permittedStarted.countDown();
                release.await();
                running.decrementAndGet();
                return BinaryData.fromBytes(ManifestManager.MAPPER.writeValueAsBytes(manifest));
              });
    }

    // when
    final var wildcard =
        new BackupIdentifierWildcardImpl(Optional.empty(), Optional.of(2), CheckpointPattern.any());
    final var listing =
        Thread.ofVirtual().start(() -> manager.listManifests(wildcard, ListOptions.all()));
    assertThat(permittedStarted.await(10, TimeUnit.SECONDS)).isTrue();
    release.countDown();
    listing.join(TimeUnit.SECONDS.toMillis(10));

    // then
    assertThat(maxRunning).hasValue(ManifestManager.MANIFEST_READ_PARALLELISM);
  }

  private static Manifest manifest(final BackupIdentifierImpl id) {
    final var backup =
        new BackupImpl(
            id,
            new BackupDescriptorImpl(1, 1, "version", Instant.now(), CheckpointType.MANUAL_BACKUP),
            new NamedFileSetImpl(Map.of()),
            new NamedFileSetImpl(Map.of()));
    return Manifest.createInProgress(backup).complete();
  }

  private static PagedIterable<BlobItem> pagedIterableOf(final List<BlobItem> blobItems) {
    final var page =
        new PagedResponseBase<Void, BlobItem>(
            mock(HttpRequest.class), 200, new HttpHeaders(), blobItems, null, null);
    return new PagedIterable<>(() -> page);
  }
}
