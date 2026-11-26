/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupReplicationFailed;
import io.camunda.zeebe.backup.s3.S3BackupStoreException.BackupReplicationTimedOut;
import io.camunda.zeebe.backup.s3.manifest.CompletedBackupManifest;
import io.camunda.zeebe.backup.s3.manifest.ValidBackupManifest;
import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.ReplicationStatus;

/**
 * Tests how {@link S3BackupStore#save(Backup)} waits for bucket replication. The client is an
 * in-memory fake that keeps manifests, so the test can observe what the manifest says at each step,
 * and reports a replication status per object through {@link #replication}.
 */
@ExtendWith(MockitoExtension.class)
final class S3BackupStoreReplicationTest {

  private static final Duration WAIT = Duration.ofSeconds(10);

  @Mock private S3AsyncClient client;

  /** Manifests written so far, by key. */
  private final Map<String, byte[]> manifests = new ConcurrentHashMap<>();

  /** Keys of all uploaded objects, in upload order. */
  private final List<String> uploads = new CopyOnWriteArrayList<>();

  /** Keys reported as replicated when the manifest was marked as completed. */
  private final AtomicReference<Set<String>> replicatedWhenCompleted = new AtomicReference<>();

  /** Keys that have been reported as replicated by a HEAD so far. */
  private final Set<String> replicated = ConcurrentHashMap.newKeySet();

  /** Replication status reported for a key, given how often it was polled already. */
  private volatile ReplicationFake replication = (key, polls) -> ReplicationStatus.COMPLETED;

  private final Map<String, Integer> polls = new ConcurrentHashMap<>();

  /** Replication status the bucket reports when a manifest is read, or null for none. */
  private volatile ReplicationStatus manifestReplicationStatus;

  private S3BackupStore store;

  @BeforeEach
  void setUp() {
    lenient()
        .when(client.putObject(any(Consumer.class), any(AsyncRequestBody.class)))
        .thenAnswer(
            invocation -> {
              final var key = putKey(invocation.getArgument(0));
              uploads.add(key);
              if (key.endsWith("manifest.json")) {
                final var manifest = read(invocation.getArgument(1));
                manifests.put(key, manifest);
                if (S3BackupStore.MAPPER.readValue(manifest, ValidBackupManifest.class)
                    instanceof CompletedBackupManifest) {
                  replicatedWhenCompleted.set(Set.copyOf(replicated));
                }
              }
              return CompletableFuture.completedFuture(PutObjectResponse.builder().build());
            });
    lenient()
        .when(client.getObject(any(Consumer.class), any(AsyncResponseTransformer.class)))
        .thenAnswer(
            invocation -> {
              final var key = getKey(invocation.getArgument(0));
              final var manifest = manifests.get(key);
              if (manifest == null) {
                return CompletableFuture.failedFuture(
                    NoSuchKeyException.builder().message("no such key").build());
              }
              return CompletableFuture.completedFuture(
                  ResponseBytes.fromByteArray(
                      GetObjectResponse.builder()
                          .replicationStatus(manifestReplicationStatus)
                          .build(),
                      manifest));
            });
    lenient()
        .when(client.headObject(any(Consumer.class)))
        .thenAnswer(
            invocation -> {
              final var key = headKey(invocation.getArgument(0));
              final int previousPolls = polls.getOrDefault(key, 0);
              polls.put(key, previousPolls + 1);
              final var status = replication.status(key, previousPolls);
              if (status == ReplicationStatus.COMPLETED) {
                replicated.add(key);
              }
              return CompletableFuture.completedFuture(
                  HeadObjectResponse.builder().replicationStatus(status).build());
            });
    lenient()
        .when(client.headBucket(any(HeadBucketRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(HeadBucketResponse.builder().build()));
  }

  @AfterEach
  void tearDown() {
    if (store != null) {
      store.closeAsync().join();
    }
  }

  @Test
  void shouldCompleteBackupOnceAllObjectsAreReplicated() throws IOException {
    // given
    store = storeWaitingForReplication(WAIT);
    final var backup = TestBackupProvider.simpleBackup();
    replication =
        (key, previousPolls) ->
            previousPolls < 2 ? ReplicationStatus.PENDING : ReplicationStatus.COMPLETED;

    // when
    final var saved = store.save(backup);

    // then
    assertThat(saved).succeedsWithin(WAIT);
    assertThat(store.getStatus(backup.id()).join().statusCode())
        .isEqualTo(BackupStatusCode.COMPLETED);
    assertThat(replicated)
        .containsExactlyInAnyOrderElementsOf(uploads.stream().distinct().toList());
  }

  @Test
  void shouldMarkManifestCompletedOnlyAfterContentsAreReplicated() throws IOException {
    // given
    store = storeWaitingForReplication(WAIT);
    final var backup = TestBackupProvider.simpleBackup();
    replication =
        (key, previousPolls) ->
            previousPolls < 1 ? ReplicationStatus.PENDING : ReplicationStatus.COMPLETED;

    // when
    store.save(backup).join();

    // then
    final var contents =
        uploads.stream().filter(key -> !key.endsWith("manifest.json")).distinct().toList();
    assertThat(contents).hasSize(4);
    assertThat(replicatedWhenCompleted.get()).containsExactlyInAnyOrderElementsOf(contents);
    assertThat(polls.keySet()).anyMatch(key -> key.endsWith("manifest.json"));
  }

  @Test
  void shouldMarkBackupFailedWhenReplicationFails() throws IOException {
    // given
    store = storeWaitingForReplication(WAIT);
    final var backup = TestBackupProvider.simpleBackup();
    replication =
        (key, previousPolls) ->
            key.contains("segment-file-1") ? ReplicationStatus.FAILED : ReplicationStatus.PENDING;

    // when
    final var saved = store.save(backup);

    // then
    assertThat(saved)
        .failsWithin(WAIT)
        .withThrowableThat()
        .havingCause()
        .isInstanceOf(BackupReplicationFailed.class);
    final var status = store.getStatus(backup.id()).join();
    assertThat(status.statusCode()).isEqualTo(BackupStatusCode.FAILED);
    assertThat(status.failureReason()).hasValueSatisfying(r -> assertThat(r).contains("failed"));
    assertThat(replicatedWhenCompleted.get()).isNull();
  }

  @Test
  void shouldMarkBackupFailedWhenReplicationTimesOut() throws IOException {
    // given
    store = storeWaitingForReplication(Duration.ofMillis(100));
    final var backup = TestBackupProvider.simpleBackup();
    replication = (key, previousPolls) -> ReplicationStatus.PENDING;

    // when
    final var saved = store.save(backup);

    // then
    assertThat(saved)
        .failsWithin(WAIT)
        .withThrowableThat()
        .havingCause()
        .isInstanceOf(BackupReplicationTimedOut.class);
    final var status = store.getStatus(backup.id()).join();
    assertThat(status.statusCode()).isEqualTo(BackupStatusCode.FAILED);
    assertThat(status.failureReason()).hasValueSatisfying(r -> assertThat(r).contains("Timed out"));
    assertThat(replicatedWhenCompleted.get()).isNull();
  }

  @Test
  void shouldMarkBackupFailedWhenManifestReplicationTimesOut() throws IOException {
    // given
    store = storeWaitingForReplication(Duration.ofMillis(200));
    final var backup = TestBackupProvider.simpleBackup();
    replication =
        (key, previousPolls) ->
            key.endsWith("manifest.json") ? ReplicationStatus.PENDING : ReplicationStatus.COMPLETED;

    // when
    final var saved = store.save(backup);

    // then
    assertThat(saved)
        .failsWithin(WAIT)
        .withThrowableThat()
        .havingCause()
        .isInstanceOf(BackupReplicationTimedOut.class);
    assertThat(replicatedWhenCompleted.get()).isNotNull();
    assertThat(store.getStatus(backup.id()).join().statusCode()).isEqualTo(BackupStatusCode.FAILED);
  }

  @Test
  void shouldMarkBackupFailedWhenBucketHasNoReplication() throws IOException {
    // given no replication rule covers the backup's objects, so they report no status
    store = storeWaitingForReplication(WAIT);
    replication = (key, previousPolls) -> null;
    final var backup = TestBackupProvider.simpleBackup();

    // when
    final var saved = store.save(backup);

    // then
    assertThat(saved)
        .failsWithin(WAIT)
        .withThrowableThat()
        .havingCause()
        .isInstanceOf(BackupReplicationFailed.class)
        .withMessageContaining("has no replication status");
    assertThat(store.getStatus(backup.id()).join().statusCode()).isEqualTo(BackupStatusCode.FAILED);
  }

  @Test
  void shouldNotReadTheBucketReplicationConfiguration() throws IOException {
    // given
    store = storeWaitingForReplication(WAIT);
    final var backup = TestBackupProvider.simpleBackup();

    // when
    store.verifyConnection().join();
    store.save(backup).join();

    // then only object-level requests are needed
    verify(client, never()).getBucketReplication(any(Consumer.class));
  }

  @Test
  void shouldNotWaitForReplicationWhenDisabled() throws IOException {
    // given
    store =
        new S3BackupStore(new S3BackupConfig.Builder().withBucketName("bucket").build(), client);
    final var backup = TestBackupProvider.simpleBackup();
    replication = (key, previousPolls) -> ReplicationStatus.PENDING;

    // when
    final var saved = store.save(backup);

    // then
    assertThat(saved).succeedsWithin(WAIT);
    assertThat(store.getStatus(backup.id()).join().statusCode())
        .isEqualTo(BackupStatusCode.COMPLETED);
    verify(client, never()).headObject(any(Consumer.class));
    verify(client, never()).getBucketReplication(any(Consumer.class));
  }

  @Test
  void shouldReportBackupInProgressUntilItsManifestIsReplicated() throws IOException {
    // given
    store = storeWaitingForReplication(WAIT);
    final var backup = TestBackupProvider.simpleBackup();
    store.save(backup).join();

    // when the completed manifest has not reached the replica site yet
    manifestReplicationStatus = ReplicationStatus.PENDING;

    // then
    assertThat(store.getStatus(backup.id()).join().statusCode())
        .isEqualTo(BackupStatusCode.IN_PROGRESS);

    // when it has
    manifestReplicationStatus = ReplicationStatus.COMPLETED;

    // then
    assertThat(store.getStatus(backup.id()).join().statusCode())
        .isEqualTo(BackupStatusCode.COMPLETED);
  }

  @Test
  void shouldReportBackupFailedWhenItsManifestFailedToReplicate() throws IOException {
    // given
    store = storeWaitingForReplication(WAIT);
    final var backup = TestBackupProvider.simpleBackup();
    store.save(backup).join();

    // when
    manifestReplicationStatus = ReplicationStatus.FAILED;

    // then
    final var status = store.getStatus(backup.id()).join();
    assertThat(status.statusCode()).isEqualTo(BackupStatusCode.FAILED);
    assertThat(status.failureReason())
        .hasValueSatisfying(reason -> assertThat(reason).contains("failed to replicate"));
  }

  @Test
  void shouldReportReplicaOfCompletedManifestAsCompleted() throws IOException {
    // given the backup is read at the replica site
    store = storeWaitingForReplication(WAIT);
    final var backup = TestBackupProvider.simpleBackup();
    store.save(backup).join();

    // when
    manifestReplicationStatus = ReplicationStatus.REPLICA;

    // then
    assertThat(store.getStatus(backup.id()).join().statusCode())
        .isEqualTo(BackupStatusCode.COMPLETED);
  }

  @Test
  void shouldReportCompletedManifestWithoutReplicationStatusAsCompleted() throws IOException {
    // given a backup written before the bucket had replication rules
    store = storeWaitingForReplication(WAIT);
    final var backup = TestBackupProvider.simpleBackup();
    store.save(backup).join();

    // when
    manifestReplicationStatus = null;

    // then
    assertThat(store.getStatus(backup.id()).join().statusCode())
        .isEqualTo(BackupStatusCode.COMPLETED);
  }

  @Test
  void shouldIgnoreManifestReplicationStatusWhenNotWaitingForReplication() throws IOException {
    // given
    store =
        new S3BackupStore(new S3BackupConfig.Builder().withBucketName("bucket").build(), client);
    final var backup = TestBackupProvider.simpleBackup();
    store.save(backup).join();

    // when
    manifestReplicationStatus = ReplicationStatus.PENDING;

    // then
    assertThat(store.getStatus(backup.id()).join().statusCode())
        .isEqualTo(BackupStatusCode.COMPLETED);
  }

  private S3BackupStore storeWaitingForReplication(final Duration timeout) {
    final var config =
        new S3BackupConfig.Builder()
            .withBucketName("bucket")
            .withWaitForReplication(true)
            .withReplicationPollInterval(Duration.ofMillis(10))
            .withReplicationTimeout(timeout)
            .build();
    return new S3BackupStore(config, client);
  }

  private static String putKey(final Consumer<PutObjectRequest.Builder> request) {
    final var builder = PutObjectRequest.builder();
    request.accept(builder);
    return builder.build().key();
  }

  private static String getKey(final Consumer<GetObjectRequest.Builder> request) {
    final var builder = GetObjectRequest.builder();
    request.accept(builder);
    return builder.build().key();
  }

  private static String headKey(final Consumer<HeadObjectRequest.Builder> request) {
    final var builder = HeadObjectRequest.builder();
    request.accept(builder);
    return builder.build().key();
  }

  private static byte[] read(final AsyncRequestBody body) {
    final var out = new ByteArrayOutputStream();
    body.subscribe(
            buffer -> {
              final var bytes = new byte[buffer.remaining()];
              buffer.get(bytes);
              out.writeBytes(bytes);
            })
        .join();
    return out.toByteArray();
  }

  @FunctionalInterface
  private interface ReplicationFake {
    ReplicationStatus status(String key, int previousPolls);
  }
}
