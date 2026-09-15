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
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.s3.manifest.Manifest;
import io.camunda.zeebe.backup.s3.manifest.NoBackupManifest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

@ExtendWith(MockitoExtension.class)
final class S3BackupStoreListTest {

  private static final int PAGE_SIZE = 1000;
  private static final int FIRST_PAGE_MANIFESTS = 17;

  @Mock private S3AsyncClient client;

  private final List<ListObjectsV2Request> requests = new ArrayList<>();
  private final ConcurrentLinkedQueue<PendingManifest> pendingManifests =
      new ConcurrentLinkedQueue<>();
  private final AtomicInteger currentManifestReads = new AtomicInteger();
  private final AtomicInteger maximumCurrentManifestReads = new AtomicInteger();
  private S3BackupStore store;

  @BeforeEach
  void setUp() {
    final var config = new S3BackupConfig.Builder().withBucketName("test-bucket").build();
    store = spy(new S3BackupStore(config, client));

    when(client.listObjectsV2(
            org.mockito.ArgumentMatchers.<Consumer<ListObjectsV2Request.Builder>>any()))
        .thenAnswer(
            invocation -> {
              final Consumer<ListObjectsV2Request.Builder> requestConfigurer =
                  invocation.getArgument(0);
              final var requestBuilder = ListObjectsV2Request.builder();
              requestConfigurer.accept(requestBuilder);
              final var request = requestBuilder.build();
              requests.add(request);
              return CompletableFuture.completedFuture(responseFor(request));
            });

    doAnswer(
            invocation -> {
              final BackupIdentifier id = invocation.getArgument(0);
              final var manifest = new NoBackupManifest(BackupIdentifierImpl.from(id));
              final var result = new CompletableFuture<Manifest>();
              if (id.partitionId() == 1 && id.checkpointId() < FIRST_PAGE_MANIFESTS - 1) {
                maximumCurrentManifestReads.accumulateAndGet(
                    currentManifestReads.incrementAndGet(), Math::max);
                result.whenComplete((ignored, error) -> currentManifestReads.decrementAndGet());
              }
              pendingManifests.add(new PendingManifest(manifest, result));
              return result;
            })
        .when(store)
        .readManifestObject(any());
  }

  @Test
  void shouldListAllManifestPagesWithBoundedReads() {
    // given
    final var wildcard =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.empty(), CheckpointPattern.any());

    // when
    final var result = store.list(wildcard);

    // then
    assertThat(requests).hasSize(3);
    assertThat(currentManifestReads).hasValue(16);
    assertThat(maximumCurrentManifestReads).hasValue(16);

    pendingManifests.stream()
        .filter(
            pending ->
                pending.manifest().id().partitionId() == 1
                    && pending.manifest().id().checkpointId() < FIRST_PAGE_MANIFESTS - 1)
        .findFirst()
        .orElseThrow()
        .complete();
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(pendingManifests)
                    .filteredOn(pending -> pending.manifest().id().partitionId() == 1)
                    .hasSize(FIRST_PAGE_MANIFESTS + 1));

    completePendingManifests();
    Awaitility.await().untilAsserted(() -> assertThat(requests).hasSize(4));
    completePendingManifests();

    final var statuses = result.join();
    assertThat(statuses).hasSize(19).extracting(BackupStatus::id).doesNotHaveDuplicates();
    assertThat(maximumCurrentManifestReads).hasValue(16);
    assertRequestsUseExpectedPagination();
  }

  private void assertRequestsUseExpectedPagination() {
    assertThat(requests).allSatisfy(request -> assertThat(request.maxKeys()).isEqualTo(PAGE_SIZE));

    final var legacyRequests =
        requests.stream().filter(request -> request.prefix().isEmpty()).toList();
    assertThat(legacyRequests).hasSize(2);
    assertThat(legacyRequests.get(0).continuationToken()).isNull();
    assertThat(legacyRequests.get(1).continuationToken()).isEqualTo("legacy-page-2");

    final var currentRequests =
        requests.stream().filter(request -> request.prefix().equals("manifests/")).toList();
    assertThat(currentRequests).hasSize(2);
    assertThat(currentRequests.get(0).continuationToken()).isNull();
    assertThat(currentRequests.get(1).continuationToken()).isEqualTo("current-page-2");
  }

  private void completePendingManifests() {
    pendingManifests.forEach(PendingManifest::complete);
  }

  private static ListObjectsV2Response responseFor(final ListObjectsV2Request request) {
    if (request.prefix().isEmpty()) {
      if (request.continuationToken() == null) {
        return response(List.of(), "legacy-page-2");
      }
      return response(
          List.of(manifestObject("2/200/1/manifest.json"), manifestObject("1/16/1/manifest.json")),
          null);
    }

    if (request.continuationToken() == null) {
      final var manifests =
          IntStream.range(0, FIRST_PAGE_MANIFESTS)
              .mapToObj(index -> manifestObject("manifests/1/%d/1/manifest.json".formatted(index)))
              .toList();
      return response(manifests, "current-page-2");
    }
    return response(List.of(manifestObject("manifests/1/999/1/manifest.json")), null);
  }

  private static ListObjectsV2Response response(
      final List<S3Object> contents, final String continuationToken) {
    return ListObjectsV2Response.builder()
        .contents(contents)
        .nextContinuationToken(continuationToken)
        .build();
  }

  private static S3Object manifestObject(final String key) {
    return S3Object.builder().key(key).build();
  }

  private record PendingManifest(Manifest manifest, CompletableFuture<Manifest> result) {
    private void complete() {
      result.complete(manifest);
    }
  }
}
