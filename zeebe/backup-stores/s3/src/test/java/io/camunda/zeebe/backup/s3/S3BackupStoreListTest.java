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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.ListOptions;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.s3.manifest.Manifest;
import io.camunda.zeebe.backup.s3.manifest.NoBackupManifest;
import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Lists a bucket that holds current-layout manifests for checkpoints 0 to 16 and 999 of partition
 * 1, plus two legacy backups for checkpoints 500 and 700, where checkpoint 700 was stored by two
 * brokers. The mocked client answers list requests from that layout and records every request, so
 * the tests can assert which listings and which manifest reads a page costs.
 */
@ExtendWith(MockitoExtension.class)
final class S3BackupStoreListTest {

  private static final int CURRENT_FIRST_PAGE = 17;

  @Mock private S3AsyncClient client;

  private final List<ListObjectsV2Request> requests = new CopyOnWriteArrayList<>();
  private final Set<BackupIdentifier> readManifests = ConcurrentHashMap.newKeySet();
  private final Set<BackupIdentifier> vanishedManifests = ConcurrentHashMap.newKeySet();
  private S3BackupStore store;

  @BeforeEach
  void setUp() {
    final var config = new S3BackupConfig.Builder().withBucketName("test-bucket").build();
    store = spy(new S3BackupStore(config, client));

    when(client.listObjectsV2(ArgumentMatchers.<Consumer<ListObjectsV2Request.Builder>>any()))
        .thenAnswer(
            invocation -> {
              final Consumer<ListObjectsV2Request.Builder> configurer = invocation.getArgument(0);
              final var builder = ListObjectsV2Request.builder();
              configurer.accept(builder);
              final var request = builder.build();
              requests.add(request);
              return CompletableFuture.completedFuture(respondTo(request));
            });

    doAnswer(
            invocation -> {
              final BackupIdentifier id = invocation.getArgument(0);
              readManifests.add(id);
              final var noBackup = new NoBackupManifest(BackupIdentifierImpl.from(id));
              if (vanishedManifests.contains(id)) {
                return CompletableFuture.completedFuture(noBackup);
              }
              return CompletableFuture.completedFuture(
                  (Manifest) noBackup.asInProgress(backupWithId(id)));
            })
        .when(store)
        .readManifestObject(any());
  }

  @Test
  void shouldReadOnlyManifestsOfTheSelectedPage() {
    // when
    final var page =
        store
            .list(partition(1), ListOptions.newestFirst(OptionalLong.empty(), OptionalInt.of(3)))
            .join();

    // then
    assertThat(page)
        .extracting(status -> status.id().checkpointId())
        .containsExactly(999L, 700L, 700L, 500L);
    assertThat(readManifests)
        .containsExactlyInAnyOrder(id(1, 999), id(0, 700), id(2, 700), id(0, 500));
  }

  @Test
  void shouldEnumerateLegacyBackupsByCheckpointWithoutWalkingTheirContents() {
    // when
    store
        .list(partition(1), ListOptions.newestFirst(OptionalLong.empty(), OptionalInt.of(3)))
        .join();

    // then: current manifests are listed page by page under their own prefix
    assertThat(requests)
        .filteredOn(request -> request.prefix().equals("manifests/1/"))
        .extracting(ListObjectsV2Request::continuationToken)
        .containsExactly(null, "current-page-2");
    // legacy checkpoints are listed as common prefixes, never as objects
    assertThat(requests)
        .filteredOn(request -> request.prefix().equals("1/"))
        .allSatisfy(request -> assertThat(request.delimiter()).isEqualTo("/"))
        .extracting(ListObjectsV2Request::continuationToken)
        .containsExactly(null, "legacy-page-2");
    // only the selected legacy checkpoints are listed for their member copies
    assertThat(requests)
        .filteredOn(request -> request.prefix().matches("1/\\d+/"))
        .extracting(ListObjectsV2Request::prefix)
        .containsExactlyInAnyOrder("1/700/", "1/500/");
    assertThat(requests).allSatisfy(request -> assertThat(request.maxKeys()).isEqualTo(1000));
  }

  @Test
  void shouldContinueAfterTheCursor() {
    // when
    final var page =
        store
            .list(partition(1), ListOptions.newestFirst(OptionalLong.of(500), OptionalInt.of(5)))
            .join();

    // then
    assertThat(page)
        .extracting(status -> status.id().checkpointId())
        .containsExactly(16L, 15L, 14L, 13L, 12L);
    assertThat(readManifests)
        .containsExactlyInAnyOrder(id(1, 16), id(1, 15), id(1, 14), id(1, 13), id(1, 12));
    assertThat(requests).noneSatisfy(request -> assertThat(request.prefix()).matches("1/\\d+/"));
  }

  @Test
  void shouldListEverythingNewestFirstWithoutOptions() {
    // when
    final var statuses = store.list(partition(1)).join();

    // then
    final var expectedCheckpointIds = new ArrayList<Long>(List.of(999L, 700L, 700L, 500L));
    IntStream.iterate(CURRENT_FIRST_PAGE - 1, i -> i >= 0, i -> i - 1)
        .forEach(i -> expectedCheckpointIds.add((long) i));
    assertThat(statuses)
        .extracting(status -> status.id().checkpointId())
        .containsExactlyElementsOf(expectedCheckpointIds);
    assertThat(readManifests).hasSize(CURRENT_FIRST_PAGE + 4);
  }

  @Test
  void shouldSkipManifestsDeletedAfterListing() {
    // given
    vanishedManifests.add(id(1, 999));

    // when
    final var page =
        store
            .list(partition(1), ListOptions.newestFirst(OptionalLong.empty(), OptionalInt.of(2)))
            .join();

    // then
    assertThat(page).extracting(BackupStatus::id).containsExactly(id(0, 700), id(2, 700));
  }

  @Test
  void shouldWalkTheWholeBucketWhenTheWildcardHasNoPartition() {
    // when
    final var wildcard =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.empty(), CheckpointPattern.any());
    final var statuses = store.list(wildcard, ListOptions.all()).join();

    // then: the legacy layout can only be walked flat, but content objects are not read
    assertThat(statuses).extracting(BackupStatus::id).containsExactly(id(0, 700), id(1, 0));
    assertThat(readManifests).containsExactlyInAnyOrder(id(0, 700), id(1, 0));
    assertThat(requests)
        .extracting(ListObjectsV2Request::prefix)
        .containsExactlyInAnyOrder("manifests/", "");
  }

  private static ListObjectsV2Response respondTo(final ListObjectsV2Request request) {
    final var prefix = request.prefix();
    final var delimited = "/".equals(request.delimiter());
    final var token = request.continuationToken();
    if (prefix.equals("manifests/1/") && !delimited) {
      if (token == null) {
        return objects(
            IntStream.range(0, CURRENT_FIRST_PAGE)
                .mapToObj("manifests/1/%d/1/manifest.json"::formatted)
                .toList(),
            "current-page-2");
      }
      return objects(List.of("manifests/1/999/1/manifest.json"), null);
    }
    if (prefix.equals("1/") && delimited) {
      return token == null
          ? prefixes(List.of("1/500/"), "legacy-page-2")
          : prefixes(List.of("1/700/"), null);
    }
    if (prefix.equals("1/700/") && delimited) {
      return prefixes(List.of("1/700/0/", "1/700/2/"), null);
    }
    if (prefix.equals("1/500/") && delimited) {
      return prefixes(List.of("1/500/0/"), null);
    }
    if (prefix.equals("manifests/") && !delimited) {
      return objects(List.of("manifests/1/0/1/manifest.json"), null);
    }
    if (prefix.isEmpty() && !delimited) {
      return objects(
          List.of(
              "1/700/0/manifest.json",
              "1/700/0/segments/segment-1",
              "1/700/0/snapshot/snapshot-1",
              "manifests/1/0/1/manifest.json"),
          null);
    }
    throw new AssertionError("Unexpected listing request: " + request);
  }

  private static ListObjectsV2Response objects(final List<String> keys, final String nextToken) {
    return ListObjectsV2Response.builder()
        .contents(keys.stream().map(key -> S3Object.builder().key(key).build()).toList())
        .nextContinuationToken(nextToken)
        .build();
  }

  private static ListObjectsV2Response prefixes(
      final List<String> commonPrefixes, final String nextToken) {
    return ListObjectsV2Response.builder()
        .commonPrefixes(
            commonPrefixes.stream()
                .map(prefix -> CommonPrefix.builder().prefix(prefix).build())
                .toList())
        .nextContinuationToken(nextToken)
        .build();
  }

  private static BackupIdentifierWildcardImpl partition(final int partitionId) {
    return new BackupIdentifierWildcardImpl(
        Optional.empty(), Optional.of(partitionId), CheckpointPattern.any());
  }

  private static BackupIdentifierImpl id(final int nodeId, final long checkpointId) {
    return new BackupIdentifierImpl(nodeId, 1, checkpointId);
  }

  private static io.camunda.zeebe.backup.api.Backup backupWithId(final BackupIdentifier id) {
    try {
      return TestBackupProvider.minimalBackupWithId(BackupIdentifierImpl.from(id));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
